package com.ouros.app.feature.main.rank

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.ouros.app.R
import com.ouros.app.data.rank.RankPodiumEntry
import java.text.NumberFormat
import java.util.Locale

class RankAdapter : ListAdapter<RankPodiumEntry, RankAdapter.RankViewHolder>(DiffCallback) {
    private val scoreFormatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rank, parent, false)
        return RankViewHolder(view)
    }

    inner class RankViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val position: TextView = itemView.findViewById(R.id.textRankPosition)
        private val avatar: ImageView = itemView.findViewById(R.id.viewRankAvatar)
        private val initials: TextView = itemView.findViewById(R.id.textRankInitials)
        private val farmName: TextView = itemView.findViewById(R.id.textFarmName)
        private val score: TextView = itemView.findViewById(R.id.textRankScore)

        fun bind(item: RankPodiumEntry, currentPhoto: Any?) {
            position.text = item.rankPosition.toString()
            farmName.text = item.displayName
            score.text = scoreFormatter.format(item.score ?: 0.0)
            if (item.isCurrent && currentPhoto != null) {
                initials.visibility = View.GONE
                avatar.visibility = View.VISIBLE
                avatar.load(currentPhoto)
            } else {
                avatar.setImageDrawable(null)
                avatar.visibility = View.INVISIBLE
                initials.text = initialsFor(item.farmName ?: item.displayName)
                initials.backgroundTintList = ColorStateList.valueOf(colorFor(item))
                initials.visibility = View.VISIBLE
            }
            avatar.contentDescription = if (item.isCurrent) "Sua foto de perfil" else "Identificação de ${item.displayName}"
        }

        private fun initialsFor(name: String): String = name.trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { "?" }

        private fun colorFor(item: RankPodiumEntry): Int {
            if (item.isCurrent) return Color.parseColor("#D8A23A")
            val avatarColors = listOf("#D85F3A", "#3AD83F", "#3A47D8", "#993AD8")
            return Color.parseColor(avatarColors[Math.floorMod(item.farmId.hashCode(), avatarColors.size)])
        }
    }

    var currentPhoto: Any? = null

    override fun onBindViewHolder(holder: RankViewHolder, position: Int) {
        holder.bind(getItem(position), currentPhoto)
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
