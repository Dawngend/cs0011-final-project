package com.example.medimind.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.medimind.R
import com.example.medimind.databinding.ActivityReportBinding
import com.example.medimind.db.DatabaseHelper
import com.example.medimind.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReportActivity : AppCompatActivity() {
    private lateinit var b: ActivityReportBinding
    private val db by lazy { DatabaseHelper.getInstance(this) }
    private val missedAdapter = MissedAdapter()
    private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private var days = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityReportBinding.inflate(layoutInflater)
        setContentView(b.root)
        val left = b.root.paddingLeft
        val top = b.root.paddingTop
        val right = b.root.paddingRight
        val bottom = b.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            insets
        }
        b.missedList.layoutManager = LinearLayoutManager(this)
        b.missedList.adapter = missedAdapter
        b.back.setOnClickListener { finish() }
        b.sevenDays.setOnClickListener { selectPeriod(7) }
        b.thirtyDays.setOnClickListener { selectPeriod(30) }
        renderPeriod()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun selectPeriod(value: Int) {
        days = value
        renderPeriod()
        load()
    }

    private fun renderPeriod() {
        b.sevenDays.isSelected = days == 7
        b.thirtyDays.isSelected = days == 30
    }

    private fun load() {
        lifecycleScope.launch {
            b.loading.isVisible = true
            try {
                val now = DateUtils.now()
                val today = DateUtils.formatDate(now.toLocalDate())
                val nowTime = DateUtils.formatTime(now.toLocalTime())
                val result = withContext(Dispatchers.IO) {
                    db.getAdherenceSummary(days, today, nowTime) to
                        db.getMissedDoses(DateUtils.periodStart(today, days), today, nowTime)
                }
                val summary = result.first
                val start = DateUtils.parseDate(DateUtils.periodStart(today, days))
                val end = now.toLocalDate()
                b.summary.text = getString(
                    R.string.report_range,
                    start?.format(dayFormat) ?: "",
                    end.format(dayFormat),
                )
                b.takenValue.text = String.format(Locale.getDefault(), "%d", summary.taken)
                b.missedValue.text = String.format(Locale.getDefault(), "%d", summary.missed)
                b.skippedValue.text = String.format(Locale.getDefault(), "%d", summary.skipped)
                val missed = result.second
                missedAdapter.submitList(missed)
                val noDoses = summary.taken + summary.missed + summary.skipped == 0
                b.missedEmpty.setText(if (noDoses) R.string.empty_report else R.string.empty_missed)
                b.missedEmpty.isVisible = missed.isEmpty()
            } finally {
                b.loading.isVisible = false
            }
        }
    }
}
