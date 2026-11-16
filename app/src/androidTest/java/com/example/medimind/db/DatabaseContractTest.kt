package com.example.medimind.db

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.medimind.model.*
import com.example.medimind.util.DateUtils
import java.time.*
import java.util.UUID
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseContractTest {
    private lateinit var context: Context
    private lateinit var db: DatabaseHelper
    private lateinit var filename: String

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        filename = "contract-${UUID.randomUUID()}.db"
        at("2026-11-16 10:00")
        db = DatabaseHelper(context, filename)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(filename)
        DateUtils.clockOverride = null
    }

    private fun at(text: String) {
        DateUtils.clockOverride =
            Clock.fixed(
                requireNotNull(DateUtils.parseTimestamp(text)).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC,
            )
    }

    private fun medicine(stock: Int? = null, units: Int = 1) =
        Medication(
            name = "Demo medicine",
            dose = "500 mg",
            startDate = "2026-11-16",
            stockQuantity = stock,
            unitsPerDose = units,
        )

    private fun times(vararg times: String) = times.map {
        Schedule(timeOfDay = it, daysOfWeek = (1..7).toSet())
    }

    private fun add(stock: Int? = null, units: Int = 1) =
        db.insertMedication(medicine(stock, units), times("08:00", "20:00"))

    private fun rowCount(table: String) =
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun logId() =
        db.readableDatabase.rawQuery("SELECT id FROM intake_logs", null).use {
            it.moveToFirst()
            it.getLong(0)
        }

    @Test
    fun insertPersistsAndInvalidSchedulesNeverPartiallySave() {
        val id = add()
        assertTrue(id > 0)
        assertEquals(2, db.getSchedulesForMedication(id).size)
        assertEquals(
            -1L,
            db.insertMedication(medicine().copy(name = "Other"), times("08:00", "08:00")),
        )
        assertEquals(1, rowCount("medications"))
        assertEquals(2, rowCount("schedules"))
        db.close()
        db = DatabaseHelper(context, filename)
        assertEquals("Demo medicine", db.getMedication(id)?.name)
    }

    @Test
    fun normalizedDuplicateCheckIncludesDoneButExcludesSelf() {
        val id = add()
        assertEquals(
            -1L,
            db.insertMedication(
                medicine().copy(name = " DEMO MEDICINE ", dose = " 500 MG "),
                times("09:00"),
            ),
        )
        val m = requireNotNull(db.getMedication(id))
        assertTrue(db.updateMedication(m, db.getSchedulesForMedication(id)))
        assertTrue(db.insertMedication(medicine().copy(dose = "250 mg"), times("09:00")) > 0)
    }

    @Test
    fun todayDueEqualityFutureAndWeekdayUseOneRule() {
        val id = add()
        val upcoming = db.getSchedulesForMedication(id).last()
        assertFalse(db.saveIntake(upcoming.id, "2026-11-16", "TAKEN"))
        at("2026-11-16 08:00")
        val rows = db.getTodayDoses("2026-11-16", 1)
        assertEquals(listOf(DoseStatus.MISSED, DoseStatus.UPCOMING), rows.map { it.status })
        assertFalse(db.saveIntake(rows.first().scheduleId, "2026-11-15", "TAKEN"))
        assertFalse(db.saveIntake(rows.first().scheduleId, "2026-11-16", "MISSED"))
    }

    @Test
    fun takenIsIdempotentAndCorrectionsPreserveLogIdAndStock() {
        val id = add(stock = 10, units = 2)
        val sid = db.getSchedulesForMedication(id).first().id
        assertTrue(db.saveIntake(sid, "2026-11-16", "TAKEN"))
        val log = logId()
        assertEquals(8, db.getMedication(id)?.stockQuantity)
        assertTrue(db.saveIntake(sid, "2026-11-16", "TAKEN"))
        assertEquals(8, db.getMedication(id)?.stockQuantity)
        assertTrue(db.saveIntake(sid, "2026-11-16", "SKIPPED"))
        assertEquals(10, db.getMedication(id)?.stockQuantity)
        assertTrue(db.saveIntake(sid, "2026-11-16", "TAKEN"))
        assertEquals(8, db.getMedication(id)?.stockQuantity)
        assertEquals(log, logId())
        assertEquals(1, rowCount("intake_logs"))
    }

    @Test
    fun insufficientStockClampsAndOnlyActualDeductionIsRefunded() {
        val id = add(stock = 1, units = 2)
        val sid = db.getSchedulesForMedication(id).first().id
        assertTrue(db.saveIntake(sid, "2026-11-16", "TAKEN"))
        assertEquals(0, db.getMedication(id)?.stockQuantity)
        assertTrue(db.saveIntake(sid, "2026-11-16", "SKIPPED"))
        assertEquals(1, db.getMedication(id)?.stockQuantity)
    }

    @Test
    fun observedBalanceResetsRefundsAndUnchangedFormDoesNotOverwriteIntake() {
        val id = add(stock = 10, units = 2)
        val old = requireNotNull(db.getMedication(id))
        val schedules = db.getSchedulesForMedication(id)
        assertTrue(db.saveIntake(schedules.first().id, "2026-11-16", "TAKEN"))
        assertTrue(db.updateMedication(old.copy(instructions = "With water"), schedules))
        assertEquals(8, db.getMedication(id)?.stockQuantity)
        assertTrue(
            db.updateMedication(
                requireNotNull(db.getMedication(id)).copy(stockQuantity = 30),
                schedules,
                true,
            ),
        )
        assertTrue(db.saveIntake(schedules.first().id, "2026-11-16", "SKIPPED"))
        assertEquals(30, db.getMedication(id)?.stockQuantity)
    }

    @Test
    fun refillsAndAlertLatchRespectEpisodeAndOverflow() {
        val id = add(stock = 5)
        assertEquals(listOf(id), db.getLowStockMedications().map { it.id })
        assertTrue(db.setLowStockNotified(id, true))
        assertFalse(db.setLowStockNotified(id, false))
        assertTrue(db.restockMedication(id, 1))
        assertEquals(6, db.getMedication(id)?.stockQuantity)
        assertFalse(requireNotNull(db.getMedication(id)).lowStockNotified)
        assertFalse(db.restockMedication(id, Int.MAX_VALUE))
        assertEquals(6, db.getMedication(id)?.stockQuantity)
    }

    @Test
    fun scheduleEditPreservesOldDosesAndDoesNotInventPastReplacement() {
        val id = add()
        val existing = db.getSchedulesForMedication(id)
        assertTrue(db.saveIntake(existing.first().id, "2026-11-16", "TAKEN"))
        at("2026-11-16 10:15")
        val desired = listOf(existing.first().copy(timeOfDay = "09:00"), existing.last())
        assertTrue(db.updateMedication(requireNotNull(db.getMedication(id)), desired))
        val current = db.getSchedulesForMedication(id)
        assertNotEquals(existing.first().id, current.first().id)
        assertEquals(existing.last().id, current.last().id)
        assertEquals("08:00", db.getHistoryForMedication(id).single().timeOfDay)
        assertEquals(DoseStatus.TAKEN, db.getHistoryForMedication(id).single().status)
        at("2026-11-17 10:00")
        assertEquals("09:00", db.getTodayDoses("2026-11-17", 2).first().timeOfDay)
    }

    @Test
    fun removingTimeKeepsHistoryAndStaleScheduleIdsRollbackMetadata() {
        val id = add()
        val existing = db.getSchedulesForMedication(id)
        val med = requireNotNull(db.getMedication(id))
        assertTrue(db.updateMedication(med, listOf(existing.last())))
        assertEquals(1, db.getHistoryForMedication(id).size)
        assertFalse(db.updateMedication(med.copy(name = "Should not save"), existing))
        assertEquals(med.name, db.getMedication(id)?.name)
        assertEquals(2, rowCount("schedules"))
    }

    @Test
    fun reportsCountOnlyDueDosesWithInclusiveWindowAndSeparateSkipped() {
        at("2026-11-10 10:00")
        val id =
            db.insertMedication(medicine().copy(startDate = "2026-11-10"), times("08:00", "20:00"))
        val sid = db.getSchedulesForMedication(id).first().id
        at("2026-11-15 10:00")
        assertTrue(db.saveIntake(sid, "2026-11-15", "TAKEN"))
        at("2026-11-16 10:00")
        assertTrue(db.saveIntake(sid, "2026-11-16", "SKIPPED"))
        val summary = db.getAdherenceSummary(7, "2026-11-16", "10:00")
        assertEquals(AdherenceSummary(1, 1, 11), summary)
        assertEquals(11, db.getMissedDoses("2026-11-10", "2026-11-16", "10:00").size)
    }

    @Test
    fun editedDatesAndInactivePlanDoNotEraseEarlierMissedDoses() {
        val id = add()
        val med = requireNotNull(db.getMedication(id))
        assertTrue(
            db.updateMedication(med.copy(isActive = false), db.getSchedulesForMedication(id)),
        )
        at("2026-11-17 10:00")
        assertTrue(db.getTodayDoses("2026-11-17", 2).isEmpty())
        assertEquals(1, db.getHistoryForMedication(id).size)
    }

    @Test
    fun wholeMedicineDeletionCascadesEveryVersionAndLog() {
        val id = add()
        val schedules = db.getSchedulesForMedication(id)
        db.saveIntake(schedules.first().id, "2026-11-16", "TAKEN")
        db.updateMedication(requireNotNull(db.getMedication(id)), listOf(schedules.last()))
        assertTrue(db.deleteMedication(id))
        assertFalse(db.deleteMedication(id))
        assertEquals(0, rowCount("schedules"))
        assertEquals(0, rowCount("intake_logs"))
    }

    @Test
    fun scheduleInsertFailureRollsBackRetirementAndMetadata() {
        val id = add()
        val med = requireNotNull(db.getMedication(id))
        val original = db.getSchedulesForMedication(id)
        db.writableDatabase.execSQL(
            "CREATE TRIGGER fail_schedule BEFORE INSERT ON schedules WHEN NEW.time_of_day='09:00' BEGIN SELECT RAISE(ABORT,'test failure'); END",
        )
        assertFalse(
            db.updateMedication(
                med.copy(name = "Must rollback"),
                listOf(original.first().copy(timeOfDay = "09:00"), original.last()),
            ),
        )
        assertEquals(med.name, db.getMedication(id)?.name)
        assertEquals(original, db.getSchedulesForMedication(id))
    }

    @Test
    fun stockWriteFailureRollsBackInsertedLog() {
        val id = add(stock = 10)
        val sid = db.getSchedulesForMedication(id).first().id
        db.writableDatabase.execSQL(
            "CREATE TRIGGER fail_stock BEFORE UPDATE ON medications WHEN NEW.stock_quantity<OLD.stock_quantity BEGIN SELECT RAISE(ABORT,'test failure'); END",
        )
        assertFalse(db.saveIntake(sid, "2026-11-16", "TAKEN"))
        assertEquals(0, rowCount("intake_logs"))
        assertEquals(10, db.getMedication(id)?.stockQuantity)
    }

    @Test
    fun migrationFromOriginalVersion1PreservesIdsAndLogs() {
        migration(1)
    }

    @Test
    fun migrationFromScheduleVersion2PreservesIdsAndLogs() {
        migration(2)
    }

    private fun migration(version: Int) {
        db.close()
        context.deleteDatabase(filename)
        val legacy = context.openOrCreateDatabase(filename, Context.MODE_PRIVATE, null)
        val sql =
            InstrumentationRegistry.getInstrumentation()
                .context
                .assets
                .open("schema_v$version.sql")
                .bufferedReader()
                .use { it.readText() }
        sql.split(';').filter { it.isNotBlank() }.forEach(legacy::execSQL)
        legacy.execSQL(
            "INSERT INTO medications(id,name,dose,start_date,end_date,instructions) VALUES (42,'Legacy demo','500 mg','2026-11-15','','')",
        )
        if (version == 1)
            legacy.execSQL(
                "INSERT INTO schedules(id,medication_id,time_of_day,days_of_week) VALUES (81,42,'08:00','1,2,3,4,5,6,7')",
            )
        else
            legacy.execSQL(
                "INSERT INTO schedules(id,medication_id,time_of_day,days_of_week,effective_from) VALUES (81,42,'08:00','1,2,3,4,5,6,7','2026-11-15 00:00')",
            )
        legacy.execSQL(
            "INSERT INTO intake_logs(id,schedule_id,dose_date,status,logged_at) VALUES (91,81,'2026-11-15','TAKEN','2026-11-15 08:01')",
        )
        legacy.version = version
        legacy.close()
        db = DatabaseHelper(context, filename)
        assertEquals(42L, db.getMedication(42)?.id)
        assertEquals(listOf(42L), db.getAllMedications(null, MedFilter.ACTIVE).map { it.id })
        assertNull(db.getMedication(42)?.stockQuantity)
        assertEquals(81L, db.getSchedule(81)?.id)
        assertEquals(91L, logId())
        assertEquals(1, rowCount("intake_logs"))
        assertEquals(3, db.readableDatabase.version)
        assertEquals(1, db.getHistoryForMedication(42).count { it.status == DoseStatus.TAKEN })
    }
}
