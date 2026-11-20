package com.example.medimind.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.medimind.R
import com.example.medimind.databinding.ItemMedicationBinding
import com.example.medimind.model.Medication
import com.example.medimind.model.MedicationRow
import com.example.medimind.util.DateUtils
import java.util.Locale

class MedicationAdapter(private val onClick: (Medication) -> Unit) :
    ListAdapter<MedicationRow, MedicationAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemMedicationBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick)

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(private val b: ItemMedicationBinding, private val onClick: (Medication) -> Unit) : RecyclerView.ViewHolder(b.root) {
        init {
            b.root.clipToOutline = true
        }

        fun bind(row: MedicationRow) {
            val m = row.medication
            val c = b.root.context
            b.times.text = c.getString(R.string.medication_times, row.times.joinToString(", "))
            b.times.isVisible = row.times.isNotEmpty()
            b.name.text = m.name
            b.dose.text = m.dose
            val current = m.isCurrentOn(DateUtils.formatDate(DateUtils.now().toLocalDate()))
            b.schedule.text = c.getString(if (current) R.string.medication_active else R.string.medication_finished)
            b.schedule.setBackgroundResource(if (current) R.drawable.bg_pill_success else R.drawable.bg_pill)
            b.schedule.setTextColor(c.getColor(if (current) R.color.success else R.color.text_secondary))
            val quantity = m.stockQuantity
            b.stock.text = when {
                quantity == null -> c.getString(R.string.stock_off)
                quantity <= m.lowStockThreshold -> c.getString(R.string.stock_low, quantity)
                else -> c.getString(R.string.stock_units, quantity)
            }
            b.stock.setTextColor(
                c.getColor(
                    when {
                        quantity == null -> R.color.text_secondary
                        quantity <= m.lowStockThreshold -> R.color.warning
                        else -> R.color.primary
                    },
                ),
            )
            b.root.setOnClickListener { onClick(m) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MedicationRow>() {
            override fun areItemsTheSame(old: MedicationRow, new: MedicationRow) = old.medication.id == new.medication.id
            override fun areContentsTheSame(old: MedicationRow, new: MedicationRow) = old == new
        }
    }
}
