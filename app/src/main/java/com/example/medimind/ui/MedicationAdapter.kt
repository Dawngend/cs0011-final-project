package com.example.medimind.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.medimind.R
import com.example.medimind.databinding.ItemMedicationBinding
import com.example.medimind.model.Medication
import java.util.Locale

class MedicationAdapter(private val onClick: (Medication) -> Unit) :
    ListAdapter<Medication, MedicationAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemMedicationBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick)

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(private val b: ItemMedicationBinding, private val onClick: (Medication) -> Unit) : RecyclerView.ViewHolder(b.root) {
        init {
            b.root.clipToOutline = true
        }

        fun bind(m: Medication) {
            val c = b.root.context
            b.name.text = m.name
            b.dose.text = m.dose
            b.schedule.text = c.getString(if (m.isActive) R.string.medication_active else R.string.medication_finished)
            b.schedule.setBackgroundResource(if (m.isActive) R.drawable.bg_pill_success else R.drawable.bg_pill)
            b.schedule.setTextColor(c.getColor(if (m.isActive) R.color.success else R.color.text_secondary))
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
        private val DIFF = object : DiffUtil.ItemCallback<Medication>() {
            override fun areItemsTheSame(old: Medication, new: Medication) = old.id == new.id
            override fun areContentsTheSame(old: Medication, new: Medication) = old == new
        }
    }
}
