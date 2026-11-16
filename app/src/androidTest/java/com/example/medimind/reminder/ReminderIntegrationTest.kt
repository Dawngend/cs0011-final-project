package com.example.medimind.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.medimind.db.DatabaseHelper
import com.example.medimind.model.*
import com.example.medimind.util.DateUtils
import java.io.FileInputStream
import java.time.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderIntegrationTest {
    private lateinit var c: Context
    private lateinit var db: DatabaseHelper
    private lateinit var nm: NotificationManager

    @Before
    fun before() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        c = instrumentation.targetContext
        DateUtils.clockOverride = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)
        db = DatabaseHelper.getInstance(c)
        db.writableDatabase.delete("medications", null, null)
        ReminderScheduler.rescheduleAll(c)
        c.getSharedPreferences("reminder_registry", Context.MODE_PRIVATE).edit().clear().commit()
        nm = c.getSystemService(NotificationManager::class.java)
        nm.cancelAll()
        if (Build.VERSION.SDK_INT >= 33)
            instrumentation.uiAutomation
                .executeShellCommand(
                    "pm grant ${c.packageName} ${Manifest.permission.POST_NOTIFICATIONS}",
                )
                .use { descriptor -> FileInputStream(descriptor.fileDescriptor).readBytes() }
        ReminderScheduler.createChannel(c)
    }

    @After
    fun after() {
        db.writableDatabase.delete("medications", null, null)
        ReminderScheduler.rescheduleAll(c)
        nm.cancelAll()
        DateUtils.clockOverride = null
    }

    private fun add(stock: Int? = null) =
        db.insertMedication(
            Medication(
                name = "Reminder demo",
                dose = "500 mg",
                startDate = "2026-11-16",
                stockQuantity = stock,
                unitsPerDose = 2,
            ),
            listOf(
                Schedule(timeOfDay = "08:00", daysOfWeek = (1..7).toSet()),
                Schedule(timeOfDay = "20:00", daysOfWeek = (1..7).toSet()),
            ),
        )

    @Test
    fun stockPostsOnceThenRearmsOnlyAfterSupplyRisesAboveThreshold() {
        val id = add(5)
        assertEquals(ReminderState.READY, ReminderScheduler.checkLowStock(c, id))
        assertTrue(requireNotNull(db.getMedication(id)).lowStockNotified)
        assertEquals(1, nm.activeNotifications.count { it.tag == "stock:$id" })
        nm.cancel("stock:$id", 0)
        ReminderScheduler.checkLowStock(c, id)
        assertEquals(0, nm.activeNotifications.size)
        assertTrue(db.restockMedication(id, 1))
        ReminderScheduler.checkLowStock(c, id)
        assertFalse(requireNotNull(db.getMedication(id)).lowStockNotified)
        db.saveIntake(db.getSchedulesForMedication(id).first().id, "2026-11-16", "TAKEN")
        ReminderScheduler.checkLowStock(c, id)
        assertEquals(1, nm.activeNotifications.count { it.tag == "stock:$id" })
        db.deleteMedication(id)
        ReminderScheduler.checkLowStock(c, id)
        assertEquals(0, nm.activeNotifications.size)
    }

    @Test
    fun doseDeliveryRejectsOldAndAlreadyRecordedOccurrences() {
        val id = add()
        val sid = db.getSchedulesForMedication(id).first().id
        ReminderScheduler.deliverDose(c, sid, "2026-11-15")
        assertEquals(0, nm.activeNotifications.size)
        ReminderScheduler.deliverDose(c, sid, "2026-11-16")
        assertEquals(1, nm.activeNotifications.size)
        assertTrue(db.saveIntake(sid, "2026-11-16", "TAKEN"))
        ReminderScheduler.cancelDoseNotification(c, sid, "2026-11-16")
        ReminderScheduler.deliverDose(c, sid, "2026-11-16")
        assertEquals(0, nm.activeNotifications.size)
    }

    @Test
    fun rebuildTracksLongIdsAndCleansDeletedPlans() {
        db.writableDatabase.execSQL("DELETE FROM sqlite_sequence WHERE name='schedules'")
        db.writableDatabase.execSQL(
            "INSERT INTO sqlite_sequence(name,seq) VALUES ('schedules',4294967296)",
        )
        val id = add()
        val sid = db.getSchedulesForMedication(id).last().id
        assertTrue(sid > Int.MAX_VALUE)
        val state = ReminderScheduler.rescheduleAll(c)
        assertTrue(state == ReminderState.READY || state == ReminderState.INEXACT)
        val registry = c.getSharedPreferences("reminder_registry", Context.MODE_PRIVATE)
        assertEquals(
            db.getSchedulesForMedication(id).map { it.id.toString() }.toSet(),
            registry.getStringSet("alarm_ids", emptySet()),
        )
        assertTrue(db.deleteMedication(id))
        ReminderScheduler.rescheduleAll(c)
        assertTrue(registry.getStringSet("alarm_ids", emptySet()).orEmpty().isEmpty())
    }
}
