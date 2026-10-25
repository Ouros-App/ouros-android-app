package com.ouros.app.feature.secondary.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ouros.app.R
import com.ouros.app.data.notification.NotificationItemResponse

class NotificationAdapter(
    private val onItemClick: (NotificationItemResponse) -> Unit
) : ListAdapter<NotificationItemResponse, NotificationAdapter.NotificationViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return if (isGoldAlert(item)) VIEW_TYPE_GOLD else VIEW_TYPE_DARK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_GOLD) {
            R.layout.item_notification_gold
        } else {
            R.layout.item_notification_dark
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return NotificationViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NotificationViewHolder(
        itemView: View,
        private val onItemClick: (NotificationItemResponse) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val txtTitle: TextView = itemView.findViewById(R.id.txtNotificationTitle)
        private val txtMessage: TextView = itemView.findViewById(R.id.txtNotificationMessage)

        fun bind(item: NotificationItemResponse) {
            txtTitle.text = item.title
            txtMessage.text = item.message
            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        const val VIEW_TYPE_DARK = 0
        const val VIEW_TYPE_GOLD = 1

        fun isGoldAlert(item: NotificationItemResponse): Boolean {
            val type = item.type.uppercase()
            if (type in listOf("ALERT_WATER", "ALERT_FEED", "CRITICAL_ALERT", "WARNING", "ALERT", "OPERATIONAL_ALERT")) {
                return true
            }
            val text = (item.title + " " + item.message).lowercase()
            return text.contains("sede") ||
                    text.contains("alimentar") ||
                    text.contains("cuidado") ||
                    text.contains("vishh") ||
                    text.contains("urgente") ||
                    text.contains("alerta")
        }

        private val DiffCallback = object : DiffUtil.ItemCallback<NotificationItemResponse>() {
            override fun areItemsTheSame(
                oldItem: NotificationItemResponse,
                newItem: NotificationItemResponse
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: NotificationItemResponse,
                newItem: NotificationItemResponse
            ): Boolean = oldItem == newItem
        }
    }
}
