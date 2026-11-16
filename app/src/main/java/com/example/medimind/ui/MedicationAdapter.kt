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
        fun bind(m: Medication) {
            b.name.text = m.name
            b.dose.text = m.dose
            b.schedule.text = if (m.isActive) "Active" else "Finished"
            b.stock.text = when {
                m.stockQuantity == null -> "Stock tracking off"
                m.stockQuantity <= m.lowStockThreshold -> "Low stock: ${m.stockQuantity} units"
                else -> "Stock: ${m.stockQuantity} units"
            }
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
