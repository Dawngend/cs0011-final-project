package com.example.medimind.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.medimind.R
import com.example.medimind.databinding.ActivityMedicationFormBinding
import com.example.medimind.db.DatabaseHelper
import com.example.medimind.model.Medication
import com.example.medimind.model.Schedule
import com.example.medimind.reminder.ReminderScheduler
import com.example.medimind.util.DateUtils
import com.example.medimind.util.Extras
import com.example.medimind.util.ValidationField
import com.example.medimind.util.Validator
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MedicationFormActivity : AppCompatActivity() {
    private lateinit var b: ActivityMedicationFormBinding
    private val db by lazy { DatabaseHelper.getInstance(this) }
    private val schedules = mutableListOf<Schedule>()
    private var existing: Medication? = null
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMedicationFormBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.title.text = if (medicationId() > 0) "Edit medication" else "Add medication"
        b.startDate.setOnClickListener { pickDate(b.startDate, b.startDate.text.toString().ifBlank { today() }) }
        b.endDate.setOnClickListener { pickDate(b.endDate, b.endDate.text.toString().ifBlank { b.startDate.text.toString().ifBlank { today() } }) }
        b.addTime.setOnClickListener { pickTime() }
        b.stockTracking.setOnCheckedChangeListener { _, checked -> b.stockFields.isVisible = checked }
        b.cancel.setOnClickListener { confirmCancel() }
        b.save.setOnClickListener { save() }
        if (medicationId() > 0) loadExisting(medicationId()) else b.startDate.setText(today())
    }

    private fun medicationId() = intent.getLongExtra(Extras.MEDICATION_ID, -1L)

    private fun today() = DateUtils.formatDate(DateUtils.now().toLocalDate())

    private fun loadExisting(id: Long) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { db.getMedication(id) to db.getSchedulesForMedication(id) }
            existing = result.first
            if (existing == null) {
                toast(R.string.error_not_found)
                finish()
                return@launch
            }
            val m = existing!!
            b.name.setText(m.name)
            b.dose.setText(m.dose)
            b.instructions.setText(m.instructions.orEmpty())
            b.startDate.setText(m.startDate)
            b.endDate.setText(m.endDate.orEmpty())
            schedules += result.second
            val days = schedules.firstOrNull()?.daysOfWeek.orEmpty()
            dayBoxes().forEachIndexed { index, box -> box.isChecked = index + 1 in days }
            b.stockTracking.isChecked = m.stockQuantity != null
            b.stockQuantity.setText(m.stockQuantity?.toString().orEmpty())
            b.unitsPerDose.setText(m.unitsPerDose.toString())
            b.lowStockThreshold.setText(m.lowStockThreshold.toString())
            renderTimes()
        }
    }

    private fun pickDate(target: TextView, initial: String) {
        val date = DateUtils.parseDate(initial) ?: DateUtils.now().toLocalDate()
        DatePickerDialog(this, { _, year, month, day ->
            target.text = DateUtils.formatDate(LocalDate.of(year, month + 1, day))
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }

    private fun pickTime() {
        val now = DateUtils.now().toLocalTime()
        TimePickerDialog(this, { _, hour, minute ->
            val time = DateUtils.formatTime(LocalTime.of(hour, minute))
            if (schedules.any { it.timeOfDay == time }) {
                toast(R.string.error_time_duplicate)
                return@TimePickerDialog
            }
            schedules += Schedule(timeOfDay = time, daysOfWeek = selectedDays())
            renderTimes()
        }, now.hour, now.minute, true).show()
    }

    private fun renderTimes() {
        b.times.removeAllViews()
        schedules.sortedBy { it.timeOfDay }.forEach { schedule ->
            val row = layoutInflater.inflate(android.R.layout.simple_list_item_1, b.times, false)
            val label = row.findViewById<TextView>(android.R.id.text1)
            label.text = schedule.timeOfDay
            row.setOnClickListener { removeTime(schedule) }
            b.times.addView(row)
        }
    }

    private fun removeTime(schedule: Schedule) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_remove_time_title)
            .setMessage(R.string.confirm_remove_time_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("Remove") { _, _ -> schedules.remove(schedule); renderTimes() }
            .show()
    }

    private fun dayBoxes() = listOf(b.monday, b.tuesday, b.wednesday, b.thursday, b.friday, b.saturday, b.sunday)

    private fun selectedDays() = dayBoxes().mapIndexedNotNull { index, box -> if (box.isChecked) index + 1 else null }.toSet()

    private fun medicationFromFields(): Medication {
        val tracking = b.stockTracking.isChecked
        return Medication(
            id = existing?.id ?: -1L,
            name = b.name.text.toString().trim(),
            dose = b.dose.text.toString().trim(),
            instructions = b.instructions.text.toString().trim().ifBlank { null },
            startDate = b.startDate.text.toString().trim(),
            endDate = b.endDate.text.toString().trim().ifBlank { null },
            isActive = existing?.isActive ?: true,
            stockQuantity = if (tracking) b.stockQuantity.text.toString().trim().toIntOrNull() else null,
            unitsPerDose = b.unitsPerDose.text.toString().trim().toIntOrNull() ?: 1,
            lowStockThreshold = b.lowStockThreshold.text.toString().trim().toIntOrNull() ?: 5,
            lowStockNotified = existing?.lowStockNotified ?: false,
        )
    }

    private fun save() {
        if (saving) return
        val medication = medicationFromFields()
        val withDays = schedules.map { it.copy(daysOfWeek = selectedDays()) }
        val duplicate = db.medicationExists(medication.name, medication.dose, medication.id)
        val issues = Validator.validateMedication(medication, withDays, existing != null, today(), duplicate)
        clearErrors()
        if (issues.isNotEmpty()) {
            issues.forEach { issue -> errorView(issue.field)?.setError(getString(issue.messageResId)) }
            errorView(issues.first().field)?.requestFocus()
            return
        }
        saving = true
        b.save.isEnabled = false
        lifecycleScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    if (existing == null) db.insertMedication(medication, withDays) > 0
                    else db.updateMedication(medication, withDays, existing!!.stockQuantity != medication.stockQuantity)
                }
                if (!ok) {
                    toast(R.string.error_save)
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    ReminderScheduler.rescheduleAll(this@MedicationFormActivity)
                    ReminderScheduler.checkLowStock(this@MedicationFormActivity, medication.id.takeIf { it > 0 })
                }
                toast(if (existing == null) R.string.medicine_saved else R.string.medicine_updated)
                finish()
            } catch (_: RuntimeException) {
                toast(R.string.error_save)
            } finally {
                saving = false
                b.save.isEnabled = true
            }
        }
    }

    private fun clearErrors() = listOf(b.name, b.dose, b.instructions, b.startDate, b.endDate, b.stockQuantity, b.unitsPerDose, b.lowStockThreshold).forEach { it.error = null }

    private fun errorView(field: ValidationField) = when (field) {
        ValidationField.NAME -> b.name
        ValidationField.DOSE -> b.dose
        ValidationField.INSTRUCTIONS -> b.instructions
        ValidationField.START_DATE -> b.startDate
        ValidationField.END_DATE -> b.endDate
        ValidationField.STOCK -> b.stockQuantity
        ValidationField.UNITS_PER_DOSE -> b.unitsPerDose
        ValidationField.LOW_STOCK_THRESHOLD -> b.lowStockThreshold
        else -> b.name
    }

    private fun confirmCancel() {
        if (hasChanges()) AlertDialog.Builder(this).setTitle(R.string.confirm_discard_title).setMessage(R.string.confirm_discard_body).setNegativeButton("Keep editing", null).setPositiveButton("Discard") { _, _ -> finish() }.show()
        else finish()
    }

    private fun hasChanges() = b.name.text.isNotBlank() || b.dose.text.isNotBlank() || b.instructions.text.isNotBlank() || schedules.isNotEmpty() || b.stockTracking.isChecked

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
