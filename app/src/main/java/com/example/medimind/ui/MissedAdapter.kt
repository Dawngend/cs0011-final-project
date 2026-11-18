package com.example.medimind.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.medimind.databinding.ItemMissedBinding
import com.example.medimind.model.DoseItem
import com.example.medimind.util.DateUtils
import java.time.format.DateTimeFormatter
import java.util.Locale

class MissedAdapter : ListAdapter<DoseItem, MissedAdapter.Holder>(DIFF) {
    private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemMissedBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val b: ItemMissedBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(d: DoseItem) {
            b.name.text = d.name
            b.dose.text = d.dose
            b.time.text = d.timeOfDay
            b.date.text = DateUtils.parseDate(d.doseDate)?.format(dayFormat) ?: d.doseDate
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<DoseItem>() {
            override fun areItemsTheSame(old: DoseItem, new: DoseItem) =
                old.scheduleId == new.scheduleId && old.doseDate == new.doseDate

            override fun areContentsTheSame(old: DoseItem, new: DoseItem) = old == new
        }
    }
}
