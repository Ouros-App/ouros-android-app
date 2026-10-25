package com.ouros.app.feature.main.record

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ouros.app.R
import com.ouros.app.data.record.RecordHistoryItem

class RecordHistoryAdapter(private val onClick: (RecordHistoryItem) -> Unit) : RecyclerView.Adapter<RecordHistoryAdapter.Holder>() {
    private var items = emptyList<RecordHistoryItem>()
    fun submitList(value: List<RecordHistoryItem>) { items = value; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_record_history, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val value: TextView = view.findViewById(R.id.recordHistoryValue)
        private val month: TextView = view.findViewById(R.id.recordHistoryMonth)
        private val editHint: View = view.findViewById(R.id.recordHistoryEditHint)
        fun bind(item: RecordHistoryItem) {
            value.text = item.formattedValue()
            month.text = item.monthYear()
            editHint.visibility = if (item.editable) View.VISIBLE else View.GONE
            itemView.isClickable = item.editable
            itemView.alpha = if (item.editable) 1f else .82f
            itemView.setOnClickListener { if (item.editable) onClick(item) }
        }
    }
}