package com.example.medimind.ui

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.medimind.R
import com.example.medimind.databinding.ActivityMedicationDetailBinding
import com.example.medimind.db.DatabaseHelper
import com.example.medimind.model.Medication
import com.example.medimind.reminder.ReminderScheduler
import com.example.medimind.util.Extras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MedicationDetailActivity : AppCompatActivity() {
    private lateinit var b: ActivityMedicationDetailBinding
    private val db by lazy { DatabaseHelper.getInstance(this) }
    private var medication: Medication? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMedicationDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.edit.setOnClickListener { startActivity(Intent(this, MedicationFormActivity::class.java).putExtra(Extras.MEDICATION_ID, medication?.id ?: -1L)) }
        b.delete.setOnClickListener { confirmDelete() }
        b.refill.setOnClickListener { refill() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val id = intent.getLongExtra(Extras.MEDICATION_ID, -1L)
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) { db.getMedication(id) to db.getSchedulesForMedication(id) to db.getHistoryForMedication(id) }
            medication = data.first.first
            val m = medication
            if (m == null) { toast(R.string.error_not_found); finish(); return@launch }
            b.title.text = "${m.name} ${m.dose}"
            val scheduleText = data.first.second.joinToString(", ") { it.timeOfDay }
            b.details.text = listOfNotNull(m.instructions, "Schedule: $scheduleText", "Dates: ${m.startDate} to ${m.endDate ?: "ongoing"}").joinToString("\n")
            b.stock.isVisible = m.stockQuantity != null
            b.refill.isVisible = m.stockQuantity != null
            b.stock.text = if (m.stockQuantity != null && m.stockQuantity <= m.lowStockThreshold) "Low stock: ${m.stockQuantity} units (threshold ${m.lowStockThreshold})" else "Stock: ${m.stockQuantity ?: 0} units"
            b.history.text = data.second.joinToString("\n") { "${it.doseDate} — ${it.status.name.lowercase().replaceFirstChar { character -> character.uppercase() }}" }.ifBlank { getString(R.string.empty_history) }
        }
    }

    private fun confirmDelete() {
        val m = medication ?: return
        AlertDialog.Builder(this).setTitle(R.string.confirm_delete_title).setMessage(R.string.confirm_delete_body).setNegativeButton(R.string.action_cancel, null).setPositiveButton("Delete") { _, _ ->
            lifecycleScope.launch {
                val deleted = withContext(Dispatchers.IO) { db.deleteMedication(m.id) }
                if (deleted) { ReminderScheduler.rescheduleAll(this@MedicationDetailActivity); toast(getString(R.string.medicine_deleted, m.name)); finish() } else toast(R.string.error_delete)
            }
        }.show()
    }

    private fun refill() {
        val input = EditText(this).apply { hint = "Whole units"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle("Add refill").setView(input).setNegativeButton(R.string.action_cancel, null).setPositiveButton("Add") { _, _ ->
            val amount = input.text.toString().toIntOrNull()
            val id = medication?.id ?: return@setPositiveButton
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { amount != null && db.restockMedication(id, amount) }
                if (ok) { ReminderScheduler.checkLowStock(this@MedicationDetailActivity, id); load() } else toast(R.string.error_refill_save)
            }
        }.show()
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
