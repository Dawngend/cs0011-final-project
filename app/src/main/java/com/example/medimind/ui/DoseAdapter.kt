package com.example.medimind.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.medimind.R
import com.example.medimind.databinding.ItemDoseBinding
import com.example.medimind.model.*

class DoseAdapter(
    private val onAction: (DoseItem, IntakeStatus) -> Unit,
    private val onChange: (DoseItem) -> Unit,
) : ListAdapter<DoseItem, DoseAdapter.Holder>(Diff) {
    var highlighted: Pair<Long, String>? = null
    var busy = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemDoseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val b: ItemDoseBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(d: DoseItem) {
            val c = b.root.context
            b.time.text = d.timeOfDay
            b.name.text = c.getString(R.string.dose_heading, d.name, d.dose)
            b.instructions.text = d.instructions.orEmpty()
            b.instructions.isVisible = !d.instructions.isNullOrBlank()
            val statusText =
                when (d.status) {
                    DoseStatus.TAKEN -> R.string.status_taken
                    DoseStatus.SKIPPED -> R.string.status_skipped
                    DoseStatus.MISSED -> R.string.status_missed
                    DoseStatus.UPCOMING -> R.string.status_upcoming
                }
            b.status.setText(statusText)
            b.actions.isVisible = d.status == DoseStatus.MISSED
            b.change.isVisible = d.status == DoseStatus.TAKEN || d.status == DoseStatus.SKIPPED
            b.taken.isEnabled = !busy
            b.skip.isEnabled = !busy
            b.change.isEnabled = !busy
            b.taken.setOnClickListener { onAction(d, IntakeStatus.TAKEN) }
            b.skip.setOnClickListener { onAction(d, IntakeStatus.SKIPPED) }
            b.change.setOnClickListener { onChange(d) }
            b.card.setBackgroundColor(
                c.getColor(
                    if (highlighted == (d.scheduleId to d.doseDate)) R.color.highlight
                    else R.color.card,
                ),
            )
        }
    }

    private object Diff : DiffUtil.ItemCallback<DoseItem>() {
        override fun areItemsTheSame(a: DoseItem, b: DoseItem) =
            a.scheduleId == b.scheduleId && a.doseDate == b.doseDate

        override fun areContentsTheSame(a: DoseItem, b: DoseItem) = a == b
    }
}
