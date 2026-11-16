package com.example.medimind.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action !in
                setOf(
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
                )
        )
            return
        val pending = goAsync()
        ReminderScheduler.worker.execute {
            try {
                ReminderScheduler.rescheduleAll(context.applicationContext)
                ReminderScheduler.checkLowStock(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
