package com.example.medimind.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.medimind.R
import com.example.medimind.databinding.ActivityReportBinding
import com.example.medimind.db.DatabaseHelper
import com.example.medimind.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportActivity : AppCompatActivity() {
    private lateinit var b: ActivityReportBinding
    private val db by lazy { DatabaseHelper.getInstance(this) }
    private var days = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityReportBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.sevenDays.setOnClickListener { days = 7; load() }
        b.thirtyDays.setOnClickListener { days = 30; load() }
    }

    override fun onResume() { super.onResume(); load() }

    private fun load() {
        lifecycleScope.launch {
            b.loading.isVisible = true
            try {
                val now = DateUtils.now()
                val today = DateUtils.formatDate(now.toLocalDate())
                val result = withContext(Dispatchers.IO) {
                    db.getAdherenceSummary(days, today, DateUtils.formatTime(now.toLocalTime())) to
                        db.getMissedDoses(DateUtils.periodStart(today, days), today, DateUtils.formatTime(now.toLocalTime()))
                }
                val summary = result.first
                b.summary.text = "Last $days days\nTaken: ${summary.taken}\nMissed: ${summary.missed}\nSkipped: ${summary.skipped}"
                b.missed.text = result.second.joinToString("\n") { "${it.doseDate} ${it.timeOfDay} — ${it.name} ${it.dose}" }.ifBlank { getString(R.string.empty_missed) }
            } finally { b.loading.isVisible = false }
        }
    }
}
