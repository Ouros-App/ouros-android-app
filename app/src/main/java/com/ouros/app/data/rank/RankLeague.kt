package com.ouros.app.data.rank

import androidx.annotation.DrawableRes
import com.ouros.app.R
import java.util.Locale

enum class RankLeague(
    val displayName: String,
    val uppercaseName: String,
    @get:DrawableRes val iconRes: Int
) {
    OURO("Ouro", "OURO", R.drawable.rank_ouro),
    PRATA("Prata", "PRATA", R.drawable.rank_prata),
    BRONZE("Bronze", "BRONZE", R.drawable.rank_bronze),
    COBRE("Cobre", "COBRE", R.drawable.rank_cobre),
    FERRO("Ferro", "FERRO", R.drawable.rank_ferro);

    companion object {
        val GOLD get() = OURO
        val SILVER get() = PRATA
        val COPPER get() = COBRE
        val IRON get() = FERRO

        fun fromPosition(position: Int, totalRankedFarms: Int): RankLeague? {
            if (position <= 0 || totalRankedFarms <= 0) return null

            val percentage = position.toDouble() / totalRankedFarms.toDouble() * 100.0

            return when {
                percentage <= 15.0 -> OURO
                percentage <= 35.0 -> PRATA
                percentage <= 65.0 -> BRONZE
                percentage <= 85.0 -> COBRE
                else -> FERRO
            }
        }

        fun fromPosition(position: Int): RankLeague {
            return when (position) {
                1 -> OURO
                2 -> PRATA
                3 -> BRONZE
                4, 5 -> COBRE
                else -> FERRO
            }
        }

        fun fromName(name: String?): RankLeague? {
            return entries.firstOrNull {
                it.name.equals(name, ignoreCase = true) ||
                    it.displayName.equals(name, ignoreCase = true)
            }
        }
    }
}
