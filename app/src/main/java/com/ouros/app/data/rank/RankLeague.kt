package com.ouros.app.data.rank

import java.util.Locale

enum class RankLeague(val displayName: String) {
    GOLD("Ouro"),
    SILVER("Prata"),
    BRONZE("Bronze"),
    COPPER("Cobre"),
    IRON("Ferro");

    val uppercaseName: String
        get() = displayName.uppercase(Locale("pt", "BR"))

    companion object {
        fun fromPosition(position: Int, totalRankedFarms: Int): RankLeague? {
            if (position <= 0 || totalRankedFarms <= 0) return null

            val percentage = position.toDouble() / totalRankedFarms.toDouble() * 100.0

            return when {
                percentage <= 15.0 -> GOLD
                percentage <= 35.0 -> SILVER
                percentage <= 65.0 -> BRONZE
                percentage <= 85.0 -> COPPER
                else -> IRON
            }
        }
    }
}