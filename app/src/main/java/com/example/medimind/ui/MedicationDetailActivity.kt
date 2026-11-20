package com.example.medimind.ui

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.medimind.R
import com.example.medimind.databinding.ActivityMedicationDetailBinding
import com.example.medimind.db.DatabaseHelper
import com.example.medimind.model.Medication
import com.example.medimind.reminder.ReminderScheduler
import com.example.medimind.util.Extras
import com.example.medimind.util.Validator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        val left = b.root.paddingLeft
        val top = b.root.paddingTop
        val right = b.root.paddingRight
        val bottom = b.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            insets
        }
        b.back.setOnClickListener { finish() }
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
            b.title.text = getString(R.string.dose_heading, m.name, m.dose)
            val scheduleText = data.first.second.joinToString(", ") { it.timeOfDay }
            b.details.text = listOfNotNull(
                m.instructions,
                getString(R.string.detail_schedule, scheduleText),
                getString(R.string.detail_dates, m.startDate, m.endDate ?: getString(R.string.detail_ongoing)),
            ).joinToString("\n")
            b.stock.isVisible = m.stockQuantity != null
            b.refill.isVisible = m.stockQuantity != null
            val quantity = m.stockQuantity
            b.stock.text = when {
                quantity == null -> ""
                quantity <= m.lowStockThreshold -> getString(R.string.detail_stock_low, quantity, m.lowStockThreshold)
                else -> getString(R.string.detail_stock_units, quantity)
            }
            b.history.text = data.second.joinToString("\n") { "${it.doseDate} — ${it.status.name.lowercase().replaceFirstChar { character -> character.uppercase() }}" }.ifBlank { getString(R.string.empty_history) }
        }
    }

    private fun confirmDelete() {
        val m = medication ?: return
        MaterialAlertDialogBuilder(this).setTitle(R.string.confirm_delete_title).setMessage(R.string.confirm_delete_body).setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_delete) { _, _ ->
            lifecycleScope.launch {
                val deleted = withContext(Dispatchers.IO) { db.deleteMedication(m.id) }
                if (deleted) { ReminderScheduler.rescheduleAll(this@MedicationDetailActivity); toast(getString(R.string.medicine_deleted, m.name)); finish() } else toast(R.string.error_delete)
            }
        }.show()
    }

    private fun refill() {
        val input = EditText(this).apply { hint = getString(R.string.hint_refill_units); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        MaterialAlertDialogBuilder(this).setTitle(R.string.action_add_refill).setView(input).setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_add) { _, _ ->
            val amount = input.text.toString().toIntOrNull()
            val m = medication ?: return@setPositiveButton
            if (amount == null || !Validator.isValidRefill(m.stockQuantity ?: 0, amount)) {
                toast(R.string.error_refill)
                return@setPositiveButton
            }
            val id = m.id
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { db.restockMedication(id, amount) }
                if (ok) { ReminderScheduler.checkLowStock(this@MedicationDetailActivity, id); load() } else toast(R.string.error_refill_save)
            }
        }.show()
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
