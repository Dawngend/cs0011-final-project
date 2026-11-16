package com.example.medimind.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.medimind.R
import com.example.medimind.db.DatabaseHelper
import com.example.medimind.model.*
import com.example.medimind.ui.TodayActivity
import com.example.medimind.util.*
import java.time.ZoneId
import java.util.concurrent.Executors

enum class ReminderState {
    READY,
    NOTIFICATIONS_OFF,
    INEXACT,
    ERROR,
}

/**
 * Public operations are synchronous: call from IO. All notification/alarm mutations share one lock.
 */
object ReminderScheduler {
    private const val CHANNEL = "medication_reminders"
    private const val TAG = "MediMindReminders"
    private val lock = Any()
    internal val worker = Executors.newSingleThreadExecutor()

    private fun prefs(c: Context) =
        c.getSharedPreferences("reminder_registry", Context.MODE_PRIVATE)

    private fun manager(c: Context) = c.getSystemService(NotificationManager::class.java)

    fun createChannel(c: Context) {
        manager(c)
            .createNotificationChannel(
                NotificationChannel(
                        CHANNEL,
                        c.getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_HIGH,
                    )
                    .apply {
                        description = c.getString(R.string.notification_channel_description)
                    },
            )
    }

    fun notificationsAllowed(c: Context): Boolean {
        createChannel(c)
        return (Build.VERSION.SDK_INT < 33 ||
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) &&
            manager(c).areNotificationsEnabled() &&
            manager(c).getNotificationChannel(CHANNEL).importance !=
                NotificationManager.IMPORTANCE_NONE
    }

    private fun exactAllowed(c: Context) =
        Build.VERSION.SDK_INT < 31 ||
            c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun rescheduleAll(context: Context): ReminderState =
        synchronized(lock) {
            val c = context.applicationContext
            try {
                createChannel(c)
                val alarm = c.getSystemService(AlarmManager::class.java)
                val registry = prefs(c)
                val db = DatabaseHelper.getInstance(c)
                val schedules = db.getAllSchedulesForAlarms()
                val identities = registry.getStringSet("alarm_ids", emptySet()).orEmpty().toSet()
                identities.mapNotNull(String::toLongOrNull).forEach { id ->
                    alarm.cancel(alarmIntent(c, id, null))
                }
                // Save before registering, so a later rebuild can cancel a partially registered
                // plan.
                check(
                    registry
                        .edit()
                        .putStringSet("alarm_ids", schedules.map { it.id.toString() }.toSet())
                        .commit(),
                )
                val now = DateUtils.now()
                var exact = exactAllowed(c)
                for (s in schedules) {
                    val next = ScheduleTiming.next(s, now) ?: continue
                    val pending = alarmIntent(c, s.id, DateUtils.formatDate(next.toLocalDate()))
                    val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if (exact) {
                        try {
                            alarm.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                millis,
                                pending,
                            )
                        } catch (_: SecurityException) {
                            exact = false
                            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
                        }
                    } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
                }
                when {
                    !notificationsAllowed(c) -> ReminderState.NOTIFICATIONS_OFF
                    !exact -> ReminderState.INEXACT
                    else -> ReminderState.READY
                }
            } catch (e: RuntimeException) {
                diagnostic("Alarm rebuild", e)
                ReminderState.ERROR
            }
        }

    private fun alarmIntent(c: Context, id: Long, date: String?): PendingIntent {
        val intent =
            Intent(c, ReminderReceiver::class.java)
                .setData(Uri.parse("medimind://reminder/$id"))
                .putExtra(Extras.SCHEDULE_ID, id)
        if (date != null) intent.putExtra(Extras.DOSE_DATE, date)
        return PendingIntent.getBroadcast(
            c,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun cancelDoseNotification(context: Context, scheduleId: Long, doseDate: String) =
        synchronized(lock) {
            manager(context).cancel("dose:$scheduleId:$doseDate", 0)
        }

    @SuppressLint("MissingPermission")
    internal fun deliverDose(context: Context, id: Long, date: String) =
        synchronized(lock) {
            try {
                val now = DateUtils.now()
                if (date != DateUtils.formatDate(now.toLocalDate())) return@synchronized
                val db = DatabaseHelper.getInstance(context)
                val s = db.getSchedule(id) ?: return@synchronized
                val at = now.toLocalDate().atTime(requireNotNull(DateUtils.parseTime(s.timeOfDay)))
                if (at > now || !ScheduleTiming.eligible(s, at)) return@synchronized
                val dose =
                    db.getTodayDoses(date, now.dayOfWeek.value).find { it.scheduleId == id }
                        ?: return@synchronized
                if (dose.status != DoseStatus.MISSED || !notificationsAllowed(context))
                    return@synchronized
                val intent =
                    Intent(context, TodayActivity::class.java)
                        .setData(Uri.parse("medimind://dose/$id/$date"))
                        .putExtra(Extras.SCHEDULE_ID, id)
                        .putExtra(Extras.DOSE_DATE, date)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pending =
                    PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                val notification =
                    NotificationCompat.Builder(context, CHANNEL)
                        .setSmallIcon(R.drawable.ic_medication)
                        .setContentTitle(
                            context.getString(R.string.dose_notification_title, dose.name),
                        )
                        .setContentText(
                            context.getString(
                                R.string.dose_notification_body,
                                dose.dose,
                                dose.instructions.orEmpty(),
                            ),
                        )
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setContentIntent(pending)
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(true)
                        .build()
                manager(context).notify("dose:$id:$date", 0, notification)
            } catch (e: RuntimeException) {
                diagnostic("Dose delivery", e)
            }
        }

    @SuppressLint("MissingPermission")
    fun checkLowStock(context: Context, medicationId: Long? = null): ReminderState =
        synchronized(lock) {
            val c = context.applicationContext
            try {
                val db = DatabaseHelper.getInstance(c)
                val registry = prefs(c)
                val posted = registry.getStringSet("stock_ids", emptySet()).orEmpty().toMutableSet()
                val medicines =
                    db.getAllMedications(null, MedFilter.ALL).filter {
                        medicationId == null || it.id == medicationId
                    }
                val lowIds = db.getLowStockMedications().map { it.id }.toSet()
                val idsToCheck =
                    if (medicationId != null) setOf(medicationId.toString())
                    else posted + medicines.map { it.id.toString() }
                var error = false
                for (idText in idsToCheck) {
                    val id = idText.toLong()
                    if (id !in lowIds) {
                        manager(c).cancel("stock:$id", 0)
                        posted.remove(idText)
                        db.getMedication(id)?.let {
                            if (it.lowStockNotified && !db.setLowStockNotified(id, false))
                                error = true
                        }
                    }
                }
                if (!notificationsAllowed(c)) {
                    registry.edit().putStringSet("stock_ids", posted).apply()
                    return@synchronized ReminderState.NOTIFICATIONS_OFF
                }
                for (candidate in medicines.filter { it.id in lowIds && !it.lowStockNotified }) {
                    val m = db.getLowStockMedications().find { it.id == candidate.id } ?: continue
                    if (m.lowStockNotified) continue
                    val intent =
                        Intent()
                            .setClassName(
                                c.packageName,
                                "com.example.medimind.ui.MedicationDetailActivity",
                            )
                            .setData(Uri.parse("medimind://stock/${m.id}"))
                            .putExtra(Extras.MEDICATION_ID, m.id)
                    val pending =
                        PendingIntent.getActivity(
                            c,
                            0,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    val empty = m.stockQuantity == 0
                    val body =
                        if (empty) c.getString(R.string.stock_empty_body, m.name)
                        else
                            c.resources.getQuantityString(
                                R.plurals.stock_low_message,
                                requireNotNull(m.stockQuantity),
                                m.name,
                                m.stockQuantity,
                            )
                    val notification =
                        NotificationCompat.Builder(c, CHANNEL)
                            .setSmallIcon(R.drawable.ic_medication)
                            .setContentTitle(
                                c.getString(
                                    if (empty) R.string.stock_empty_title
                                    else R.string.stock_low_title,
                                ),
                            )
                            .setContentText(body)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                            .setContentIntent(pending)
                            .setOnlyAlertOnce(true)
                            .setAutoCancel(true)
                            .build()
                    posted.add(m.id.toString())
                    check(registry.edit().putStringSet("stock_ids", posted.toSet()).commit())
                    manager(c).notify("stock:${m.id}", 0, notification)
                    if (!db.setLowStockNotified(m.id, true)) {
                        manager(c).cancel("stock:${m.id}", 0)
                        error = true
                    }
                }
                registry.edit().putStringSet("stock_ids", posted.toSet()).apply()
                if (error) ReminderState.ERROR else ReminderState.READY
            } catch (_: SecurityException) {
                ReminderState.NOTIFICATIONS_OFF
            } catch (e: RuntimeException) {
                diagnostic("Stock check", e)
                ReminderState.ERROR
            }
        }

    internal fun diagnostic(operation: String, e: RuntimeException) {
        // Never put medicine names, quantities, SQL arguments, or exception messages into logs.
        Log.w(TAG, "$operation failed (${e.javaClass.simpleName})")
    }
}
