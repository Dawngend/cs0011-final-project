package com.example.medimind.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medimind.util.Extras

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        ReminderScheduler.worker.execute {
            try {
                val id = intent.getLongExtra(Extras.SCHEDULE_ID, -1L)
                val date = intent.getStringExtra(Extras.DOSE_DATE)
                if (id > 0 && date != null)
                    ReminderScheduler.deliverDose(context.applicationContext, id, date)
            } finally {
                try {
                    ReminderScheduler.rescheduleAll(context.applicationContext)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
