package com.ouros.app.feature.main.rank

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

/**
 * Adaptador visual de rank. Converte itens em componentes reutilizáveis e conecta os dados às linhas da interface.
 */
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
        private val farmName: TextView = itemView.findViewById(R.id.textFarmName)
        private val score: TextView = itemView.findViewById(R.id.textRankScore)

        fun bind(item: RankPodiumEntry, currentPhoto: Any?) {
            position.text = item.rankPosition.toString()
            farmName.text = item.displayName
            score.text = scoreFormatter.format(item.score ?: 0.0)
            if (item.isCurrent && currentPhoto != null) {
                avatar.visibility = View.VISIBLE
                avatar.load(currentPhoto)
            } else {
                avatar.setImageResource(avatarFor(item))
                avatar.visibility = View.VISIBLE
            }
            avatar.contentDescription = if (item.isCurrent) "Sua foto de perfil" else "Identificação de ${item.displayName}"
        }

        private fun avatarFor(item: RankPodiumEntry): Int = AVATAR_DRAWABLES[
            Math.floorMod(item.farmId.hashCode(), AVATAR_DRAWABLES.size)
        ]
    }

    var currentPhoto: Any? = null

    private companion object {
        val AVATAR_DRAWABLES = intArrayOf(
            R.drawable.avatar_yellow,
            R.drawable.avatar_orange,
            R.drawable.avatar_green,
            R.drawable.avatar_blue,
            R.drawable.avatar_purple
        )
    }

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
