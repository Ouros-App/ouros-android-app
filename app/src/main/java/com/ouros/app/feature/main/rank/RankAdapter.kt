package com.ouros.app.feature.main.rank

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ouros.app.R
import com.ouros.app.data.rank.RankPodiumEntry
import java.text.NumberFormat
import java.util.Locale

class RankAdapter : ListAdapter<RankPodiumEntry, RankAdapter.RankViewHolder>(DiffCallback) {
    private val scoreFormatter = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rank, parent, false)
        return RankViewHolder(view)
    }

    override fun onBindViewHolder(holder: RankViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RankViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val position: TextView = itemView.findViewById(R.id.textRankPosition)
        private val avatar: View = itemView.findViewById(R.id.viewRankAvatar)
        private val farmName: TextView = itemView.findViewById(R.id.textFarmName)
        private val score: TextView = itemView.findViewById(R.id.textRankScore)

        fun bind(item: RankPodiumEntry) {
            position.text = item.rankPosition.toString()
            farmName.text = item.displayName
            score.text = scoreFormatter.format(item.score)
            avatar.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorFor(item))
            }
            avatar.contentDescription = "Identificação de ${item.displayName}"
        }

        private fun colorFor(item: RankPodiumEntry): Int {
            return when {
                item.relation.equals(RankPodiumEntry.ABOVE, true) ->
                    Color.parseColor("#FFB8EE")
                item.relation.equals(RankPodiumEntry.CURRENT, true) ->
                    Color.parseColor("#DDA42E")
                item.relation.equals(RankPodiumEntry.BELOW, true) ->
                    Color.parseColor("#251DB5")
                else -> Color.parseColor("#A7ADB3")
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RankPodiumEntry>() {
        override fun areItemsTheSame(
            oldItem: RankPodiumEntry,
            newItem: RankPodiumEntry
        ): Boolean {
            return oldItem.farmId == newItem.farmId
        }

        override fun areContentsTheSame(
            oldItem: RankPodiumEntry,
            newItem: RankPodiumEntry
        ): Boolean {
            return oldItem == newItem
        }
    }
}