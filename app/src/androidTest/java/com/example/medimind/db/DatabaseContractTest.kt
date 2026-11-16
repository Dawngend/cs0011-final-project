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
    fun scheduleEditAppliesImmediatelyAndPreservesRetainedIdsAndLogs() {
        val id = add()
        val original = db.getSchedulesForMedication(id)
        val sid = original.first().id
        assertTrue(db.saveIntake(sid, "2026-11-16", "TAKEN"))
        val storedId = logId()
        at("2026-11-16 10:15")
        assertTrue(
            db.updateMedication(
                requireNotNull(db.getMedication(id)),
                listOf(original.first().copy(timeOfDay = "09:00"), original.last()),
            ),
        )
        assertEquals(sid, db.getSchedulesForMedication(id).first().id)
        assertEquals(2, rowCount("schedules"))
        assertEquals("09:00", db.getTodayDoses("2026-11-16", 1).first().timeOfDay)
        assertEquals(IntakeStatus.TAKEN, db.getHistoryForMedication(id).single().status)
        assertEquals(storedId, db.getHistoryForMedication(id).single().id)
    }

    @Test
    fun removingTimeDeletesItsLogsWithoutRefundAndStaleIdsRollback() {
        val id = add(stock = 10)
        val original = db.getSchedulesForMedication(id)
        val med = requireNotNull(db.getMedication(id))
        assertTrue(db.saveIntake(original.first().id, "2026-11-16", "TAKEN"))
        assertTrue(db.updateMedication(med, listOf(original.last())))
        assertTrue(db.getHistoryForMedication(id).isEmpty())
        assertEquals(0, rowCount("intake_logs"))
        assertEquals(1, rowCount("schedules"))
        assertEquals(9, db.getMedication(id)?.stockQuantity)
        assertFalse(db.updateMedication(med.copy(name = "Must not save"), original))
        assertEquals(med.name, db.getMedication(id)?.name)
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
    fun inactivePlanRecalculatesMissedButKeepsRecordedHistoryAndCounts() {
        val id = add()
        val original = db.getSchedulesForMedication(id)
        assertTrue(db.saveIntake(original.first().id, "2026-11-16", "TAKEN"))
        assertTrue(
            db.updateMedication(
                requireNotNull(db.getMedication(id)).copy(isActive = false),
                original,
            ),
        )
        assertTrue(db.getTodayDoses("2026-11-16", 1).isEmpty())
        assertEquals(1, db.getHistoryForMedication(id).size)
        assertEquals(AdherenceSummary(1, 0, 0), db.getAdherenceSummary(7, "2026-11-16", "10:00"))
    }

    @Test
    fun wholeMedicineDeletionCascadesSchedulesAndLogs() {
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
    fun scheduleUpdateFailureRollsBackMetadataAndSchedules() {
        val id = add()
        val med = requireNotNull(db.getMedication(id))
        val original = db.getSchedulesForMedication(id)
        db.writableDatabase.execSQL(
            "CREATE TRIGGER fail_schedule BEFORE UPDATE OF time_of_day ON schedules WHEN NEW.time_of_day='09:00' BEGIN SELECT RAISE(ABORT,'test failure'); END",
        )
        assertFalse(
            db.updateMedication(
                med.copy(name = "Must rollback"),
                listOf(original.first().copy(timeOfDay = "09:00")),
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

    @Test
    fun migrationFromExpandedVersion3KeepsCurrentLogsStockAndSequence() {
        migration(3)
        assertEquals(listOf("08:00"), db.getSchedulesForMedication(42).map { it.timeOfDay })
        assertEquals(1, db.getHistoryForMedication(42).single().stockDeducted)
        val current = db.getSchedulesForMedication(42)
        assertTrue(
            db.updateMedication(requireNotNull(db.getMedication(42)), current + times("20:00")),
        )
        assertTrue(db.getSchedulesForMedication(42).last().id > 150L)
    }

    @Test
    fun freshScheduleColumnsMatchProposalAndHistoryContainsOnlyActions() {
        val id = add()
        val columns =
            db.readableDatabase.rawQuery("PRAGMA table_info(schedules)", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(1)) }
            }
        assertEquals(listOf("id", "medication_id", "time_of_day", "days_of_week"), columns)
        assertTrue(db.getHistoryForMedication(id).isEmpty())
        assertEquals(1, db.getMissedDoses("2026-11-16", "2026-11-16", "10:00").size)
    }

    @Test
    fun swappingRetainedTimesKeepsTheirIdsAndLogAssociation() {
        val id = add()
        val original = db.getSchedulesForMedication(id)
        assertTrue(db.saveIntake(original.first().id, "2026-11-16", "TAKEN"))
        assertTrue(
            db.updateMedication(
                requireNotNull(db.getMedication(id)),
                listOf(
                    original.first().copy(timeOfDay = "20:00"),
                    original.last().copy(timeOfDay = "08:00"),
                ),
            ),
        )
        assertEquals(original.last().id, db.getSchedulesForMedication(id).first().id)
        assertEquals(original.first().id, db.getHistoryForMedication(id).single().scheduleId)
        assertEquals(1, db.getAdherenceSummary(7, "2026-11-16", "10:00").taken)
        assertTrue(db.saveIntake(original.first().id, "2026-11-16", "SKIPPED"))
    }

    @Test
    fun migrationRejectsRecordedRetiredVersionsWithoutErasingOriginalDatabase() {
        db.close()
        context.deleteDatabase(filename)
        val legacy = context.openOrCreateDatabase(filename, Context.MODE_PRIVATE, null)
        val sql =
            InstrumentationRegistry.getInstrumentation()
                .context
                .assets
                .open("schema_v3.sql")
                .bufferedReader()
                .use { it.readText() }
        sql.split(';').filter { it.isNotBlank() }.forEach(legacy::execSQL)
        legacy.execSQL(
            "INSERT INTO medications(id,name,dose,start_date) VALUES (42,'Legacy demo','500 mg','2026-11-15')",
        )
        legacy.execSQL(
            "INSERT INTO schedules(id,medication_id,time_of_day,days_of_week,effective_from,is_current) VALUES (81,42,'08:00','1','2026-11-15 00:00',0)",
        )
        legacy.execSQL(
            "INSERT INTO intake_logs(id,schedule_id,dose_date,status,logged_at) VALUES (91,81,'2026-11-15','TAKEN','2026-11-15 08:01')",
        )
        legacy.version = 3
        legacy.close()
        db = DatabaseHelper(context, filename)
        try {
            db.writableDatabase
            fail("Migration must protect archived recorded history")
        } catch (_: android.database.sqlite.SQLiteException) {}
        context.openOrCreateDatabase(filename, Context.MODE_PRIVATE, null).use { original ->
            assertEquals(3, original.version)
            original.rawQuery("SELECT COUNT(*) FROM intake_logs WHERE id=91", null).use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
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
        if (version == 3) {
            legacy.execSQL("UPDATE medications SET stock_quantity=7,units_per_dose=2 WHERE id=42")
            legacy.execSQL("UPDATE intake_logs SET stock_deducted=1 WHERE id=91")
            legacy.execSQL(
                "INSERT INTO schedules(id,medication_id,time_of_day,days_of_week,effective_from,is_current) VALUES (150,42,'07:00','1','2026-11-15 00:00',0)",
            )
        }
        legacy.version = version
        legacy.close()
        db = DatabaseHelper(context, filename)
        assertEquals(42L, db.getMedication(42)?.id)
        assertEquals(listOf(42L), db.getAllMedications(null, MedFilter.ACTIVE).map { it.id })
        if (version == 3) assertEquals(7, db.getMedication(42)?.stockQuantity)
        else assertNull(db.getMedication(42)?.stockQuantity)
        assertEquals(81L, db.getSchedule(81)?.id)
        assertEquals(91L, logId())
        assertEquals(1, rowCount("intake_logs"))
        assertEquals(4, db.readableDatabase.version)
        assertEquals(1, db.getHistoryForMedication(42).count { it.status == IntakeStatus.TAKEN })
    }
}
