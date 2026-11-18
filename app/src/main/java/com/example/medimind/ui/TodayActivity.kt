package com.example.medimind.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.medimind.R
import com.example.medimind.databinding.ActivityTodayBinding
import com.example.medimind.db.DatabaseHelper
import com.example.medimind.model.*
import com.example.medimind.reminder.*
import com.example.medimind.util.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.*

class TodayActivity : AppCompatActivity() {
    private lateinit var b: ActivityTodayBinding
    private val db by lazy { DatabaseHelper.getInstance(this) }
    private lateinit var adapter: DoseAdapter
    private var clockJob: Job? = null
    private var loadJob: Job? = null
    private var reminderState = ReminderState.READY
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshReminders()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTodayBinding.inflate(layoutInflater)
        ViewCompat.setAccessibilityHeading(b.title, true)
        setContentView(b.root)
        val left = b.root.paddingLeft
        val top = b.root.paddingTop
        val right = b.root.paddingRight
        val bottom = b.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime(),
                )
            view.setPadding(
                left + bars.left,
                top + bars.top,
                right + bars.right,
                bottom + bars.bottom,
            )
            insets
        }
        adapter = DoseAdapter(::record)
        b.doses.layoutManager = LinearLayoutManager(this)
        b.doses.adapter = adapter
        b.retry.setOnClickListener { loadDoses() }
        b.more.setOnClickListener {
            PopupMenu(this, b.more).apply {
                menuInflater.inflate(R.menu.today_menu, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.menu_medications -> openScreen("MedicationsActivity")
                        R.id.menu_report -> openScreen("ReportActivity")
                    }
                    true
                }
                show()
            }
        }
        b.reminderSettings.setOnClickListener { openReminderSettings() }
        readHighlight(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readHighlight(intent)
        loadDoses()
    }

    private fun readHighlight(intent: Intent) {
        val id = intent.getLongExtra(Extras.SCHEDULE_ID, -1L)
        val date = intent.getStringExtra(Extras.DOSE_DATE)
        adapter.highlighted =
            if (id > 0 && date != null && DateUtils.parseDate(date) != null) id to date else null
    }

    override fun onResume() {
        super.onResume()
        refreshReminders()
        clockJob?.cancel()
        clockJob = lifecycleScope.launch {
            while (isActive) {
                loadDoses()
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }

    override fun onPause() {
        clockJob?.cancel()
        super.onPause()
    }

    private fun loadDoses() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            b.loading.isVisible = adapter.itemCount == 0
            try {
                val now = DateUtils.now()
                b.title.text =
                    getString(
                        R.string.today_date,
                        now.toLocalDate()
                            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    )
                val items =
                    withContext(Dispatchers.IO) {
                        db.getTodayDoses(
                            DateUtils.formatDate(now.toLocalDate()),
                            now.dayOfWeek.value,
                        )
                    }
                b.empty.setText(R.string.empty_today)
                b.empty.isVisible = items.isEmpty()
                b.retry.isVisible = false
                val taken = items.count { it.status == DoseStatus.TAKEN }
                b.doseHero.isVisible = items.isNotEmpty()
                b.doseSummary.text = getString(R.string.doses_taken_of, taken, items.size)
                b.doseProgress.progress = if (items.isEmpty()) 0 else taken * 100 / items.size
                adapter.submitList(items) {
                    val position = items.indexOfFirst {
                        adapter.highlighted == (it.scheduleId to it.doseDate)
                    }
                    if (position >= 0) b.doses.scrollToPosition(position)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: RuntimeException) {
                b.doseHero.isVisible = false
                b.empty.setText(R.string.error_load_doses)
                b.empty.isVisible = true
                b.retry.isVisible = true
            } finally {
                b.loading.isVisible = false
            }
        }
    }

    private fun record(dose: DoseItem, status: IntakeStatus) {
        if (adapter.busy) return
        adapter.busy = true
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
        lifecycleScope.launch {
            try {
                val saved =
                    withContext(Dispatchers.IO) {
                        db.saveIntake(dose.scheduleId, dose.doseDate, status.name)
                    }
                if (saved) {
                    withContext(Dispatchers.IO) {
                        ReminderScheduler.cancelDoseNotification(
                            this@TodayActivity,
                            dose.scheduleId,
                            dose.doseDate,
                        )
                        ReminderScheduler.checkLowStock(this@TodayActivity, dose.medicationId)
                    }
                    toast(
                        if (status == IntakeStatus.TAKEN) R.string.marked_taken
                        else R.string.marked_skipped,
                    )
                    loadDoses()
                } else toast(R.string.error_save_intake)
            } finally {
                adapter.busy = false
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
            }
        }
    }

    private fun refreshReminders() {
        lifecycleScope.launch {
            val state =
                withContext(Dispatchers.IO) {
                    val alarms = ReminderScheduler.rescheduleAll(this@TodayActivity)
                    val stock = ReminderScheduler.checkLowStock(this@TodayActivity)
                    if (stock == ReminderState.ERROR) stock else alarms
                }
            reminderState = state
            b.reminderWarning.isVisible = state != ReminderState.READY
            b.reminderSettings.isVisible = state != ReminderState.READY
            b.reminderWarning.setText(
                when (state) {
                    ReminderState.NOTIFICATIONS_OFF -> R.string.reminders_off
                    ReminderState.INEXACT -> R.string.reminders_inexact
                    else -> R.string.reminders_retry_error
                },
            )
            b.reminderSettings.setText(
                when (state) {
                    ReminderState.NOTIFICATIONS_OFF -> R.string.action_notifications
                    ReminderState.INEXACT -> R.string.action_exact_alarms
                    else -> R.string.action_retry
                },
            )
        }
    }

    private fun openReminderSettings() {
        if (reminderState == ReminderState.ERROR) {
            refreshReminders()
            return
        }
        if (
            reminderState == ReminderState.NOTIFICATIONS_OFF &&
                Build.VERSION.SDK_INT >= 33 &&
                !ReminderScheduler.notificationsAllowed(this) &&
                !getPreferences(MODE_PRIVATE).getBoolean("notification_requested", false)
        ) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notification_requested", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val target =
            if (reminderState == ReminderState.INEXACT && Build.VERSION.SDK_INT >= 31)
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName"),
                )
            else
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(target)
        } catch (_: ActivityNotFoundException) {
            toast(R.string.error_open_screen)
        }
    }

    private fun openScreen(name: String) {
        // Explicit component allows Clarence's Activities to be added without placeholder classes.
        try {
            startActivity(Intent().setClassName(packageName, "com.example.medimind.ui.$name"))
        } catch (_: ActivityNotFoundException) {
            toast(R.string.error_open_screen)
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
