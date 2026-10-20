package com.ouros.app.data.rank

import com.ouros.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankLeagueTest {

    @Test
    fun fromPositionWithTotal_calculatesCorrectLeague() {
        assertEquals(RankLeague.OURO, RankLeague.fromPosition(1, 100))
        assertEquals(RankLeague.OURO, RankLeague.fromPosition(15, 100))
        assertEquals(RankLeague.PRATA, RankLeague.fromPosition(16, 100))
        assertEquals(RankLeague.PRATA, RankLeague.fromPosition(35, 100))
        assertEquals(RankLeague.BRONZE, RankLeague.fromPosition(36, 100))
        assertEquals(RankLeague.BRONZE, RankLeague.fromPosition(65, 100))
        assertEquals(RankLeague.COBRE, RankLeague.fromPosition(66, 100))
        assertEquals(RankLeague.COBRE, RankLeague.fromPosition(85, 100))
        assertEquals(RankLeague.FERRO, RankLeague.fromPosition(86, 100))
        assertEquals(RankLeague.FERRO, RankLeague.fromPosition(100, 100))
    }

    @Test
    fun fromPositionWithTotal_invalidPositionsReturnNull() {
        assertNull(RankLeague.fromPosition(0, 100))
        assertNull(RankLeague.fromPosition(-1, 100))
        assertNull(RankLeague.fromPosition(1, 0))
        assertNull(RankLeague.fromPosition(1, -10))
    }

    @Test
    fun fromPositionSingle_returnsCorrectLeague() {
        assertEquals(RankLeague.OURO, RankLeague.fromPosition(1))
        assertEquals(RankLeague.PRATA, RankLeague.fromPosition(2))
        assertEquals(RankLeague.BRONZE, RankLeague.fromPosition(3))
        assertEquals(RankLeague.COBRE, RankLeague.fromPosition(4))
        assertEquals(RankLeague.COBRE, RankLeague.fromPosition(5))
        assertEquals(RankLeague.FERRO, RankLeague.fromPosition(6))
        assertEquals(RankLeague.FERRO, RankLeague.fromPosition(99))
    }

    @Test
    fun fromName_resolvesCorrectLeague() {
        assertEquals(RankLeague.OURO, RankLeague.fromName("OURO"))
        assertEquals(RankLeague.OURO, RankLeague.fromName("Ouro"))
        assertEquals(RankLeague.OURO, RankLeague.fromName("ouro"))
        assertEquals(RankLeague.PRATA, RankLeague.fromName("Prata"))
        assertEquals(RankLeague.BRONZE, RankLeague.fromName("BRONZE"))
        assertEquals(RankLeague.COBRE, RankLeague.fromName("cobre"))
        assertEquals(RankLeague.FERRO, RankLeague.fromName("Ferro"))
        assertNull(RankLeague.fromName("Invalido"))
        assertNull(RankLeague.fromName(null))
    }

    @Test
    fun companionAliases_matchPortugueseEnums() {
        assertEquals(RankLeague.OURO, RankLeague.GOLD)
        assertEquals(RankLeague.PRATA, RankLeague.SILVER)
        assertEquals(RankLeague.COBRE, RankLeague.COPPER)
        assertEquals(RankLeague.FERRO, RankLeague.IRON)
    }

    @Test
    fun leagueProperties_areCorrect() {
        assertEquals("Ouro", RankLeague.OURO.displayName)
        assertEquals("OURO", RankLeague.OURO.uppercaseName)
        assertEquals(R.drawable.rank_ouro, RankLeague.OURO.iconRes)
    }
}
