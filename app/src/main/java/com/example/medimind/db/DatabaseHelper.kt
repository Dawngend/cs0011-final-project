package com.example.medimind.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.example.medimind.model.*
import com.example.medimind.util.*
import java.time.LocalDate
import java.time.LocalDateTime

/** Synchronous database API: call from IO. Every multi-row write is atomic. */
class DatabaseHelper internal constructor(context: Context, name: String = DATABASE_NAME) :
    SQLiteOpenHelper(context.applicationContext, name, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        CREATE_SQL.split(';').filter { it.isNotBlank() }.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion in 1..3 && newVersion == DATABASE_VERSION) {
            "Unsupported database migration"
        }
        db.execSQL("UPDATE medications SET end_date=NULL WHERE trim(end_date)=''")
        db.execSQL("UPDATE medications SET instructions=NULL WHERE trim(instructions)=''")
        if (oldVersion < 3) {
            db.execSQL(
                "ALTER TABLE medications ADD COLUMN stock_quantity INTEGER CHECK (stock_quantity IS NULL OR stock_quantity >= 0)",
            )
            db.execSQL(
                "ALTER TABLE medications ADD COLUMN units_per_dose INTEGER NOT NULL DEFAULT 1 CHECK (units_per_dose >= 1)",
            )
            db.execSQL(
                "ALTER TABLE medications ADD COLUMN low_stock_threshold INTEGER NOT NULL DEFAULT 5 CHECK (low_stock_threshold >= 0)",
            )
            db.execSQL(
                "ALTER TABLE medications ADD COLUMN low_stock_notified INTEGER NOT NULL DEFAULT 0 CHECK (low_stock_notified IN (0,1))",
            )
            db.execSQL(
                "ALTER TABLE intake_logs ADD COLUMN stock_deducted INTEGER NOT NULL DEFAULT 0 CHECK (stock_deducted >= 0)",
            )
        }
        if (oldVersion >= 2) removeDevelopmentVersioning(db)
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_schedules_medication_time ON schedules(medication_id,time_of_day)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_schedules_medication ON schedules(medication_id)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_logs_date ON intake_logs(dose_date)")
    }

    /**
     * Earlier expanded builds were never released. Do not silently erase their recorded history.
     */
    private fun removeDevelopmentVersioning(db: SQLiteDatabase) {
        val retiredLogs =
            db.rawQuery(
                    "SELECT COUNT(*) FROM intake_logs l JOIN schedules s ON s.id=l.schedule_id WHERE s.is_current=0",
                    null,
                )
                .use {
                    it.moveToFirst()
                    it.getLong(0)
                }
        if (retiredLogs != 0L)
            throw SQLiteException(
                "Export recorded retired schedules before converting this development database",
            )
        val scheduleSequence = sequence(db, "schedules")
        val logSequence = sequence(db, "intake_logs")
        db.execSQL(
            "CREATE TABLE schedules_new (id INTEGER PRIMARY KEY AUTOINCREMENT, medication_id INTEGER NOT NULL, time_of_day TEXT NOT NULL, days_of_week TEXT NOT NULL, FOREIGN KEY(medication_id) REFERENCES medications(id) ON DELETE CASCADE)",
        )
        db.execSQL(
            "INSERT INTO schedules_new(id,medication_id,time_of_day,days_of_week) SELECT id,medication_id,time_of_day,days_of_week FROM schedules WHERE is_current=1",
        )
        db.execSQL(
            "CREATE TABLE intake_logs_new (id INTEGER PRIMARY KEY AUTOINCREMENT, schedule_id INTEGER NOT NULL, dose_date TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('TAKEN','SKIPPED')), logged_at TEXT NOT NULL, stock_deducted INTEGER NOT NULL DEFAULT 0 CHECK(stock_deducted>=0), FOREIGN KEY(schedule_id) REFERENCES schedules_new(id) ON DELETE CASCADE, UNIQUE(schedule_id,dose_date))",
        )
        db.execSQL(
            "INSERT INTO intake_logs_new(id,schedule_id,dose_date,status,logged_at,stock_deducted) SELECT id,schedule_id,dose_date,status,logged_at,stock_deducted FROM intake_logs",
        )
        db.execSQL("DROP TABLE intake_logs")
        db.execSQL("DROP TABLE schedules")
        db.execSQL("ALTER TABLE schedules_new RENAME TO schedules")
        db.execSQL("ALTER TABLE intake_logs_new RENAME TO intake_logs")
        preserveSequence(db, "schedules", scheduleSequence)
        preserveSequence(db, "intake_logs", logSequence)
    }

    private fun sequence(db: SQLiteDatabase, table: String): Long =
        db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name=?", arrayOf(table)).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }

    private fun preserveSequence(db: SQLiteDatabase, table: String, previous: Long) {
        val value = maxOf(previous, sequence(db, table))
        val values = ContentValues().apply { put("seq", value) }
        if (db.update("sqlite_sequence", values, "name=?", arrayOf(table)) == 0) {
            values.put("name", table)
            db.insertOrThrow("sqlite_sequence", null, values)
        }
    }

    fun insertMedication(m: Medication, schedules: List<Schedule>): Long =
        write(-1L) { db ->
            val now = DateUtils.now()
            val normalized = normalize(m).copy(lowStockNotified = false)
            validate(db, normalized, schedules, false, now)
            require(schedules.all { it.id == -1L })
            val id = db.insertOrThrow("medications", null, values(normalized))
            schedules.forEach { insertSchedule(db, id, it) }
            id
        }

    fun getMedication(id: Long): Medication? = medication(readableDatabase, id)

    fun getAllMedications(query: String?, filter: MedFilter): List<Medication> {
        val today = DateUtils.formatDate(DateUtils.now().toLocalDate())
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        query
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                clauses += "name LIKE ?"
                args += "%$it%"
            }
        when (filter) {
            MedFilter.ACTIVE -> {
                clauses += "is_active=1 AND (end_date IS NULL OR end_date>=?)"
                args += today
            }
            MedFilter.DONE -> {
                clauses += "(is_active=0 OR (end_date IS NOT NULL AND end_date<?))"
                args += today
            }
            MedFilter.ALL -> Unit
        }
        return medications(
            readableDatabase,
            clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            args.toTypedArray(),
        )
    }

    /** Same list as getAllMedications, with each medicine's dose times for the Medications screen. */
    fun getMedicationRows(query: String?, filter: MedFilter): List<MedicationRow> {
        val medications = getAllMedications(query, filter)
        val times = schedules(readableDatabase).groupBy({ it.medicationId }, { it.timeOfDay })
        return medications.map { MedicationRow(it, times[it.id].orEmpty()) }
    }

    fun medicationExists(name: String, dose: String, excludeId: Long = -1L): Boolean =
        exists(readableDatabase, name, dose, excludeId)

    fun updateMedication(
        m: Medication,
        schedules: List<Schedule>,
        stockQuantityChanged: Boolean = false,
    ): Boolean =
        write(false) { db ->
            val now = DateUtils.now()
            val previous = requireNotNull(medication(db, m.id))
            val updated =
                normalize(m)
                    .copy(
                        stockQuantity =
                            if (stockQuantityChanged) m.stockQuantity else previous.stockQuantity,
                        lowStockNotified = previous.lowStockNotified,
                    )
            validate(db, updated, schedules, true, now)
            val existing =
                schedules(db, "medication_id=?", arrayOf(m.id.toString())).associateBy { it.id }
            val ids = schedules.filter { it.id != -1L }.map { it.id }
            require(ids.distinct().size == ids.size && ids.all { it in existing })
            existing.keys
                .filter { it !in ids }
                .forEach { id ->
                    // Cascade deletes history. The quantity already consumed is not a refund.
                    db.delete("schedules", "id=?", arrayOf(id.toString()))
                }
            val adjusted =
                updated.copy(
                    lowStockNotified =
                        updated.lowStockNotified && isLow(updated, now.toLocalDate()),
                )
            db.update("medications", values(adjusted), "id=?", arrayOf(m.id.toString()))
            // Temporary values allow valid swaps of two retained times under the unique index.
            schedules
                .filter { it.id > 0 && existing[it.id]?.timeOfDay != it.timeOfDay }
                .forEach {
                    db.update(
                        "schedules",
                        ContentValues().apply { put("time_of_day", "__editing_${it.id}") },
                        "id=?",
                        arrayOf(it.id.toString()),
                    )
                }
            schedules.forEach { desired ->
                if (desired.id == -1L) insertSchedule(db, m.id, desired)
                else
                    db.update(
                        "schedules",
                        scheduleValues(m.id, desired),
                        "id=?",
                        arrayOf(desired.id.toString()),
                    )
            }
            if (stockQuantityChanged)
                db.execSQL(
                    "UPDATE intake_logs SET stock_deducted=0 WHERE schedule_id IN (SELECT id FROM schedules WHERE medication_id=?)",
                    arrayOf(m.id),
                )
            true
        }

    fun deleteMedication(id: Long): Boolean =
        write(false) { it.delete("medications", "id=?", arrayOf(id.toString())) == 1 }

    fun getSchedulesForMedication(medicationId: Long): List<Schedule> =
        schedules(readableDatabase, "medication_id=?", arrayOf(medicationId.toString()))

    fun getSchedule(id: Long): Schedule? = schedule(readableDatabase, id)

    fun getAllSchedulesForAlarms(): List<Schedule> = snapshot { db ->
        val now = DateUtils.now()
        val meds = medications(db).associateBy { it.id }
        schedules(db).filter { s ->
            meds[s.medicationId]?.let { ScheduleTiming.next(s, it, now) != null } == true
        }
    }

    fun restockMedication(id: Long, quantity: Int): Boolean =
        write(false) { db ->
            require(quantity > 0)
            val m = requireNotNull(medication(db, id))
            val stock = requireNotNull(m.stockQuantity).toLong() + quantity
            require(stock <= Int.MAX_VALUE)
            updateStock(db, m, stock.toInt(), DateUtils.now().toLocalDate())
            true
        }

    fun getLowStockMedications(): List<Medication> {
        val now = DateUtils.now().toLocalDate()
        return medications(readableDatabase).filter { isLow(it, now) }
    }

    fun setLowStockNotified(id: Long, notified: Boolean): Boolean =
        write(false) { db ->
            val m = requireNotNull(medication(db, id))
            require(notified == isLow(m))
            db.update(
                "medications",
                ContentValues().apply { put("low_stock_notified", if (notified) 1 else 0) },
                "id=?",
                arrayOf(id.toString()),
            ) == 1
        }

    fun saveIntake(scheduleId: Long, doseDate: String, status: String): Boolean =
        write(false) { db ->
            val desired =
                IntakeStatus.entries.firstOrNull { it.name == status }
                    ?: throw IllegalArgumentException("Invalid intake status")
            val now = DateUtils.now()
            require(doseDate == DateUtils.formatDate(now.toLocalDate()))
            val s = requireNotNull(schedule(db, scheduleId))
            val at =
                requireNotNull(DateUtils.parseDate(doseDate))
                    .atTime(requireNotNull(DateUtils.parseTime(s.timeOfDay)))
            val m = requireNotNull(medication(db, s.medicationId))
            val old =
                logs(db, "schedule_id=? AND dose_date=?", arrayOf(scheduleId.toString(), doseDate))
                    .firstOrNull()
            require(old != null || (at <= now && ScheduleTiming.eligible(s, m, at)))
            if (old?.status == desired) return@write true
            var stock = m.stockQuantity
            var deducted = 0
            if (stock != null) {
                val restored = stock.toLong() + (old?.stockDeducted ?: 0)
                require(restored <= Int.MAX_VALUE)
                deducted =
                    if (desired == IntakeStatus.TAKEN) minOf(restored.toInt(), m.unitsPerDose)
                    else 0
                stock = restored.toInt() - deducted
            }
            val logValues =
                ContentValues().apply {
                    put("schedule_id", scheduleId)
                    put("dose_date", doseDate)
                    put("status", status)
                    put("logged_at", DateUtils.formatTimestamp(now))
                    put("stock_deducted", deducted)
                }
            if (old == null) db.insertOrThrow("intake_logs", null, logValues)
            else db.update("intake_logs", logValues, "id=?", arrayOf(old.id.toString()))
            updateStock(db, m, stock, now.toLocalDate())
            true
        }

    fun getTodayDoses(today: String, dayOfWeek: Int): List<DoseItem> {
        val now = DateUtils.now()
        val date = requireNotNull(DateUtils.parseDate(today))
        require(date == now.toLocalDate() && date.dayOfWeek.value == dayOfWeek)
        return snapshot { db -> doses(db, date, date, now) }
    }

    fun getHistoryForMedication(medicationId: Long): List<IntakeLog> =
        readableDatabase
            .rawQuery(
                "SELECT l.* FROM intake_logs l JOIN schedules s ON s.id=l.schedule_id WHERE s.medication_id=? ORDER BY l.dose_date DESC,s.time_of_day DESC,l.id DESC",
                arrayOf(medicationId.toString()),
            )
            .use { c ->
                buildList { while (c.moveToNext()) add(readLog(c)) }
            }

    fun getMissedDoses(fromDate: String, today: String, nowTime: String): List<DoseItem> =
        snapshot { db ->
            val start = requireNotNull(DateUtils.parseDate(fromDate))
            val end = requireNotNull(DateUtils.parseDate(today))
            require(start <= end)
            val now = end.atTime(requireNotNull(DateUtils.parseTime(nowTime)))
            historyOrder(doses(db, start, end, now).filter { it.status == DoseStatus.MISSED })
        }

    fun getAdherenceSummary(days: Int, today: String, nowTime: String): AdherenceSummary =
        snapshot { db ->
            val first = requireNotNull(DateUtils.parseDate(DateUtils.periodStart(today, days)))
            val end = requireNotNull(DateUtils.parseDate(today))
            val items =
                doses(db, first, end, end.atTime(requireNotNull(DateUtils.parseTime(nowTime))))
            val cutoff =
                DateUtils.formatTimestamp(end.atTime(requireNotNull(DateUtils.parseTime(nowTime))))
            val recorded =
                logs(
                    db,
                    "dose_date BETWEEN ? AND ? AND logged_at<=?",
                    arrayOf(DateUtils.formatDate(first), today, cutoff),
                )
            AdherenceSummary(
                recorded.count { it.status == IntakeStatus.TAKEN },
                recorded.count { it.status == IntakeStatus.SKIPPED },
                items.count { it.status == DoseStatus.MISSED },
            )
        }

    private fun doses(
        db: SQLiteDatabase,
        first: LocalDate,
        last: LocalDate,
        now: LocalDateTime,
        medicationId: Long? = null,
    ): List<DoseItem> {
        val meds =
            medications(db)
                .filter { medicationId == null || it.id == medicationId }
                .associateBy { it.id }
        val currentSchedules =
            db.rawQuery(
                    "SELECT s.* FROM schedules s JOIN medications m ON m.id=s.medication_id ORDER BY s.time_of_day, m.name COLLATE NOCASE, s.id",
                    null,
                )
                .use { c -> buildList { while (c.moveToNext()) add(readSchedule(c)) } }
        val recorded =
            logs(
                    db,
                    "dose_date BETWEEN ? AND ?",
                    arrayOf(DateUtils.formatDate(first), DateUtils.formatDate(last)),
                )
                .associateBy { it.scheduleId to it.doseDate }
        return buildList {
            var date = first
            while (date <= last) {
                val dateText = DateUtils.formatDate(date)
                for (s in currentSchedules) {
                    val m = meds[s.medicationId] ?: continue
                    val at = date.atTime(requireNotNull(DateUtils.parseTime(s.timeOfDay)))
                    if (!ScheduleTiming.eligible(s, m, at)) continue
                    val status =
                        recorded[s.id to dateText]?.let { DoseStatus.valueOf(it.status.name) }
                            ?: if (at > now) DoseStatus.UPCOMING else DoseStatus.MISSED
                    add(
                        DoseItem(
                            s.id,
                            m.id,
                            m.name,
                            m.dose,
                            m.instructions,
                            s.timeOfDay,
                            dateText,
                            status,
                        ),
                    )
                }
                date = date.plusDays(1)
            }
        }
    }

    private fun historyOrder(items: List<DoseItem>) =
        items.sortedWith(
            compareByDescending<DoseItem> { it.doseDate }
                .thenByDescending { it.timeOfDay }
                .thenBy { it.scheduleId },
        )

    private fun normalize(m: Medication) =
        m.copy(
            name = m.name.trim(),
            dose = m.dose.trim(),
            instructions = m.instructions?.trim()?.takeIf { it.isNotEmpty() },
            endDate = m.endDate?.takeIf { it.isNotBlank() },
        )

    private fun exists(db: SQLiteDatabase, name: String, dose: String, excludeId: Long) =
        medications(db).any {
            it.id != excludeId &&
                it.name.equals(name.trim(), true) &&
                it.dose.equals(dose.trim(), true)
        }

    private fun validate(
        db: SQLiteDatabase,
        m: Medication,
        schedules: List<Schedule>,
        edit: Boolean,
        now: LocalDateTime,
    ) {
        require(
            Validator.validateMedication(
                    m,
                    schedules,
                    edit,
                    DateUtils.formatDate(now.toLocalDate()),
                    exists(db, m.name, m.dose, if (edit) m.id else -1L),
                )
                .isEmpty(),
        )
    }

    private fun isLow(m: Medication, today: LocalDate = DateUtils.now().toLocalDate()) =
        m.isCurrentOn(DateUtils.formatDate(today)) &&
            m.stockQuantity != null &&
            m.stockQuantity <= m.lowStockThreshold

    private fun updateStock(db: SQLiteDatabase, m: Medication, stock: Int?, today: LocalDate) {
        val updated = m.copy(stockQuantity = stock)
        db.update(
            "medications",
            ContentValues().apply {
                put("stock_quantity", stock)
                put("low_stock_notified", if (m.lowStockNotified && isLow(updated, today)) 1 else 0)
            },
            "id=?",
            arrayOf(m.id.toString()),
        )
    }

    private fun scheduleValues(medId: Long, s: Schedule) =
        ContentValues().apply {
            put("medication_id", medId)
            put("time_of_day", s.timeOfDay)
            put("days_of_week", s.daysOfWeek.sorted().joinToString(","))
        }

    private fun insertSchedule(db: SQLiteDatabase, medId: Long, s: Schedule) {
        db.insertOrThrow("schedules", null, scheduleValues(medId, s))
    }

    private fun values(m: Medication) =
        ContentValues().apply {
            put("name", m.name)
            put("dose", m.dose)
            put("instructions", m.instructions)
            put("start_date", m.startDate)
            put("end_date", m.endDate)
            put("is_active", if (m.isActive) 1 else 0)
            put("stock_quantity", m.stockQuantity)
            put("units_per_dose", m.unitsPerDose)
            put("low_stock_threshold", m.lowStockThreshold)
            put("low_stock_notified", if (m.lowStockNotified) 1 else 0)
        }

    private fun medications(
        db: SQLiteDatabase,
        where: String? = null,
        args: Array<String>? = null,
    ) = rows(db, "medications", where, args, "name COLLATE NOCASE,id", ::readMedication)

    private fun medication(db: SQLiteDatabase, id: Long) =
        medications(db, "id=?", arrayOf(id.toString())).firstOrNull()

    private fun schedules(db: SQLiteDatabase, where: String? = null, args: Array<String>? = null) =
        rows(db, "schedules", where, args, "time_of_day,id", ::readSchedule)

    private fun schedule(db: SQLiteDatabase, id: Long) =
        schedules(db, "id=?", arrayOf(id.toString())).firstOrNull()

    private fun logs(db: SQLiteDatabase, where: String? = null, args: Array<String>? = null) =
        rows(db, "intake_logs", where, args, mapper = ::readLog)

    private fun <T> rows(
        db: SQLiteDatabase,
        table: String,
        where: String? = null,
        args: Array<String>? = null,
        order: String? = null,
        mapper: (Cursor) -> T,
    ): List<T> =
        db.query(table, null, where, args, null, null, order).use { c ->
            buildList { while (c.moveToNext()) add(mapper(c)) }
        }

    private fun Cursor.text(key: String) = getString(getColumnIndexOrThrow(key))

    private fun Cursor.long(key: String) = getLong(getColumnIndexOrThrow(key))

    private fun Cursor.int(key: String) = getInt(getColumnIndexOrThrow(key))

    private fun Cursor.optionalText(key: String) = text(key)?.takeIf { it.isNotBlank() }

    private fun Cursor.optionalInt(key: String): Int? =
        getColumnIndexOrThrow(key).let { if (isNull(it)) null else getInt(it) }

    private fun readMedication(c: Cursor) =
        Medication(
            c.long("id"),
            c.text("name"),
            c.text("dose"),
            c.optionalText("instructions"),
            c.text("start_date"),
            c.optionalText("end_date"),
            c.int("is_active") == 1,
            c.optionalInt("stock_quantity"),
            c.int("units_per_dose"),
            c.int("low_stock_threshold"),
            c.int("low_stock_notified") == 1,
        )

    private fun readSchedule(c: Cursor) =
        Schedule(
            c.long("id"),
            c.long("medication_id"),
            c.text("time_of_day"),
            c.text("days_of_week").split(',').map(String::toInt).toSet(),
        )

    private fun readLog(c: Cursor) =
        IntakeLog(
            c.long("id"),
            c.long("schedule_id"),
            c.text("dose_date"),
            IntakeStatus.valueOf(c.text("status")),
            c.text("logged_at"),
            c.int("stock_deducted"),
        )

    private inline fun <T> snapshot(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    private inline fun <T> write(failure: T, block: (SQLiteDatabase) -> T): T =
        try {
            snapshot(block)
        } catch (_: SQLiteException) {
            failure
        } catch (_: IllegalArgumentException) {
            failure
        }

    companion object {
        const val DATABASE_NAME = "medimind.db"
        const val DATABASE_VERSION = 4
        @Volatile private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper =
            instance
                ?: synchronized(this) {
                    instance ?: DatabaseHelper(context).also { instance = it }
                }

        private const val CREATE_SQL =
            """CREATE TABLE medications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    dose TEXT NOT NULL,
    instructions TEXT,
    start_date TEXT NOT NULL,
    end_date TEXT,
    is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    stock_quantity INTEGER CHECK (stock_quantity IS NULL OR stock_quantity >= 0),
    units_per_dose INTEGER NOT NULL DEFAULT 1 CHECK (units_per_dose >= 1),
    low_stock_threshold INTEGER NOT NULL DEFAULT 5 CHECK (low_stock_threshold >= 0),
    low_stock_notified INTEGER NOT NULL DEFAULT 0 CHECK (low_stock_notified IN (0, 1))
);
CREATE TABLE schedules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    medication_id INTEGER NOT NULL,
    time_of_day TEXT NOT NULL,
    days_of_week TEXT NOT NULL,
    FOREIGN KEY (medication_id) REFERENCES medications(id) ON DELETE CASCADE
);
CREATE TABLE intake_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id INTEGER NOT NULL,
    dose_date TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('TAKEN', 'SKIPPED')),
    logged_at TEXT NOT NULL,
    stock_deducted INTEGER NOT NULL DEFAULT 0 CHECK (stock_deducted >= 0),
    FOREIGN KEY (schedule_id) REFERENCES schedules(id) ON DELETE CASCADE,
    UNIQUE (schedule_id, dose_date)
);
CREATE UNIQUE INDEX idx_schedules_medication_time ON schedules(medication_id, time_of_day);
CREATE INDEX idx_schedules_medication ON schedules(medication_id);
CREATE INDEX idx_logs_date ON intake_logs(dose_date);"""
    }
}
