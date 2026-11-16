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
        require(oldVersion in 1..2 && newVersion == 3) { "Unsupported database migration" }
        db.execSQL("UPDATE medications SET end_date=NULL WHERE trim(end_date)=''")
        db.execSQL("UPDATE medications SET instructions=NULL WHERE trim(instructions)=''")
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
        if (oldVersion == 1) {
            db.execSQL("ALTER TABLE schedules ADD COLUMN effective_from TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE schedules ADD COLUMN effective_until TEXT")
            db.execSQL(
                "ALTER TABLE schedules ADD COLUMN is_current INTEGER NOT NULL DEFAULT 1 CHECK (is_current IN (0,1))",
            )
            val now = DateUtils.now()
            val meds = medications(db).associateBy { it.id }
            rows(db, "schedules", mapper = { c -> c.long("id") to c.long("medication_id") })
                .forEach { (id, medId) ->
                    val m = meds[medId] ?: throw SQLiteException("Orphan schedule during migration")
                    val lower = requireNotNull(DateUtils.parseDate(m.startDate)).atStartOfDay()
                    var upper =
                        m.endDate?.let {
                            requireNotNull(DateUtils.parseDate(it)).plusDays(1).atStartOfDay()
                        }
                    if (!m.isActive) upper = maxOf(lower, minOf(upper ?: now, now))
                    val values =
                        ContentValues().apply {
                            put("effective_from", DateUtils.formatTimestamp(lower))
                            put("effective_until", upper?.let(DateUtils::formatTimestamp))
                        }
                    db.update("schedules", values, "id=?", arrayOf(id.toString()))
                }
            db.execSQL(
                "CREATE UNIQUE INDEX idx_schedules_current_time ON schedules(medication_id,time_of_day) WHERE is_current=1",
            )
        }
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_schedules_medication ON schedules(medication_id)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_logs_date ON intake_logs(dose_date)")
    }

    fun insertMedication(m: Medication, schedules: List<Schedule>): Long =
        write(-1L) { db ->
            val now = DateUtils.now()
            val normalized = normalize(m).copy(lowStockNotified = false)
            validate(db, normalized, schedules, false, now)
            require(schedules.all { it.id == -1L })
            val id = db.insertOrThrow("medications", null, values(normalized))
            schedules.forEach { insertSchedule(db, id, normalized, it, null) }
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
            val oldSchedules =
                schedules(db, "medication_id=? AND is_current=1", arrayOf(m.id.toString()))
                    .associateBy { it.id }
            val suppliedIds = schedules.filter { it.id != -1L }.map { it.id }
            require(
                suppliedIds.distinct().size == suppliedIds.size &&
                    suppliedIds.all { it in oldSchedules },
            )
            val allChanged =
                previous.startDate != updated.startDate ||
                    previous.endDate != updated.endDate ||
                    previous.isActive != updated.isActive
            val keepIds =
                schedules
                    .filter { desired ->
                        val old = oldSchedules[desired.id]
                        !allChanged &&
                            old != null &&
                            old.timeOfDay == desired.timeOfDay &&
                            old.daysOfWeek == desired.daysOfWeek
                    }
                    .map { it.id }
                    .toSet()
            val cutover = now.plusMinutes(1)
            oldSchedules.values
                .filter { it.id !in keepIds }
                .forEach { old ->
                    val lower = requireNotNull(DateUtils.parseTimestamp(old.effectiveFrom))
                    val upper =
                        maxOf(
                            lower,
                            minOf(
                                old.effectiveUntil?.let {
                                    requireNotNull(DateUtils.parseTimestamp(it))
                                } ?: cutover,
                                cutover,
                            ),
                        )
                    db.update(
                        "schedules",
                        ContentValues().apply {
                            put("is_current", 0)
                            put("effective_until", DateUtils.formatTimestamp(upper))
                        },
                        "id=?",
                        arrayOf(old.id.toString()),
                    )
                }
            val adjusted =
                updated.copy(
                    lowStockNotified =
                        updated.lowStockNotified && isLow(updated, now.toLocalDate()),
                )
            db.update("medications", values(adjusted), "id=?", arrayOf(m.id.toString()))
            schedules
                .filter { it.id !in keepIds }
                .forEach { insertSchedule(db, m.id, adjusted, it, cutover) }
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
        schedules(
            readableDatabase,
            "medication_id=? AND is_current=1",
            arrayOf(medicationId.toString()),
        )

    fun getSchedule(id: Long): Schedule? = schedule(readableDatabase, id)

    fun getAllSchedulesForAlarms(): List<Schedule> {
        val now = DateUtils.now()
        return schedules(readableDatabase).filter { ScheduleTiming.next(it, now) != null }
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
            require(at <= now && ScheduleTiming.eligible(s, at))
            val m = requireNotNull(medication(db, s.medicationId))
            val old =
                logs(db, "schedule_id=? AND dose_date=?", arrayOf(scheduleId.toString(), doseDate))
                    .firstOrNull()
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

    fun getHistoryForMedication(medicationId: Long): List<DoseItem> = snapshot { db ->
        val now = DateUtils.now()
        val first =
            schedules(db, "medication_id=?", arrayOf(medicationId.toString()))
                .mapNotNull { DateUtils.parseTimestamp(it.effectiveFrom)?.toLocalDate() }
                .minOrNull() ?: now.toLocalDate()
        historyOrder(
            doses(db, first, now.toLocalDate(), now, medicationId).filter {
                it.status != DoseStatus.UPCOMING
            },
        )
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
            AdherenceSummary(
                items.count { it.status == DoseStatus.TAKEN },
                items.count { it.status == DoseStatus.SKIPPED },
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
        val versions =
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
                for (s in versions) {
                    val m = meds[s.medicationId] ?: continue
                    val at = date.atTime(requireNotNull(DateUtils.parseTime(s.timeOfDay)))
                    if (!ScheduleTiming.eligible(s, at)) continue
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
        m.isActive &&
            (m.endDate == null || m.endDate >= DateUtils.formatDate(today)) &&
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

    private fun insertSchedule(
        db: SQLiteDatabase,
        medId: Long,
        m: Medication,
        s: Schedule,
        cutover: LocalDateTime?,
    ) {
        val lower =
            maxOf(
                requireNotNull(DateUtils.parseDate(m.startDate)).atStartOfDay(),
                cutover ?: LocalDateTime.MIN,
            )
        var upper =
            m.endDate?.let { requireNotNull(DateUtils.parseDate(it)).plusDays(1).atStartOfDay() }
        if (upper != null && upper < lower) upper = lower
        if (!m.isActive) upper = lower
        db.insertOrThrow(
            "schedules",
            null,
            ContentValues().apply {
                put("medication_id", medId)
                put("time_of_day", s.timeOfDay)
                put("days_of_week", s.daysOfWeek.sorted().joinToString(","))
                put("effective_from", DateUtils.formatTimestamp(lower))
                put("effective_until", upper?.let(DateUtils::formatTimestamp))
                put("is_current", 1)
            },
        )
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
            c.text("effective_from"),
            c.optionalText("effective_until"),
            c.int("is_current") == 1,
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
        const val DATABASE_VERSION = 3
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
    effective_from TEXT NOT NULL,
    effective_until TEXT,
    is_current INTEGER NOT NULL DEFAULT 1 CHECK (is_current IN (0, 1)),
    FOREIGN KEY (medication_id) REFERENCES medications(id) ON DELETE CASCADE,
    CHECK (effective_until IS NULL OR effective_until >= effective_from)
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

CREATE UNIQUE INDEX idx_schedules_current_time
    ON schedules(medication_id, time_of_day) WHERE is_current = 1;
CREATE INDEX idx_schedules_medication ON schedules(medication_id);
CREATE INDEX idx_logs_date ON intake_logs(dose_date);"""
    }
}
