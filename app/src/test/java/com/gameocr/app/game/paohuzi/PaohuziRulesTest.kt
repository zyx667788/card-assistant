package com.gameocr.app.game.paohuzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaohuziRulesTest {

    private val rules = PaohuziRules()

    private fun small(vararg ranks: Int): List<PaohuziTile> =
        ranks.map { PaohuziTile(it, TileCase.SMALL) }

    private fun big(vararg ranks: Int): List<PaohuziTile> =
        ranks.map { PaohuziTile(it, TileCase.BIG) }

    @Test
    fun `two runs plus a pair wins`() {
        assertTrue(rules.canWin(small(1, 2, 3, 4, 5, 6, 7, 7)))
    }

    @Test
    fun `two triplets plus a pair wins`() {
        assertTrue(rules.canWin(small(1, 1, 1, 2, 2, 2, 3, 3)))
    }

    @Test
    fun `mismatched shape does not win`() {
        assertFalse(rules.canWin(small(1, 2, 4, 5, 6, 7, 9, 9)))
    }

    @Test
    fun `a run never crosses the small and big case`() {
        assertFalse(rules.canWin(small(9, 10) + big(1, 2, 3, 4, 4)))
    }

    @Test
    fun `kong inside the hand counts as a set`() {
        assertTrue(rules.canWin(small(1, 1, 1, 1, 2, 2, 2, 3, 3)))
    }

    @Test
    fun `kong inside the hand can be turned off`() {
        val strict = PaohuziRules(PaohuziRuleConfig(allowKongInHand = false))
        assertFalse(strict.canWin(small(1, 1, 1, 1, 2, 2, 2, 3, 3)))
    }

    @Test
    fun `empty hand is not a win`() {
        assertFalse(rules.canWin(emptyList()))
    }

    @Test
    fun `waiting tiles include the completing tile`() {
        val waits = rules.waitingTiles(small(1, 1, 1, 2, 2, 2, 3))
        assertTrue(waits.contains(PaohuziTile(3, TileCase.SMALL)))
    }

    @Test
    fun `waiting tiles exclude tiles that cannot complete the hand`() {
        val waits = rules.waitingTiles(small(1, 1, 1, 2, 2, 2, 3))
        assertFalse(waits.contains(PaohuziTile(10, TileCase.SMALL)))
    }

    @Test
    fun `chow needs both supporting tiles in hand`() {
        assertEquals(
            listOf(small(2, 3)),
            rules.chowOptions(small(2, 3), PaohuziTile(1, TileCase.SMALL)),
        )
    }

    @Test
    fun `chow is not offered when a supporting tile is missing`() {
        assertTrue(rules.chowOptions(small(2, 4), PaohuziTile(1, TileCase.SMALL)).isEmpty())
    }

    @Test
    fun `chow never crosses the case`() {
        assertTrue(rules.chowOptions(small(2, 3), PaohuziTile(1, TileCase.BIG)).isEmpty())
    }

    @Test
    fun `chow can be disabled by config`() {
        val noChow = PaohuziRules(PaohuziRuleConfig(allowChow = false))
        assertTrue(noChow.chowOptions(small(2, 3), PaohuziTile(1, TileCase.SMALL)).isEmpty())
    }

    @Test
    fun `pung and kong use the expected hand thresholds`() {
        val five = PaohuziTile(5, TileCase.SMALL)
        assertFalse(rules.canPung(small(5), five))
        assertTrue(rules.canPung(small(5, 5), five))
        assertFalse(rules.canKong(small(5, 5), five))
        assertTrue(rules.canKong(small(5, 5, 5), five))
    }

    @Test
    fun `discard options are de-duplicated and ordered`() {
        val options = rules.discardOptions(small(3, 1, 3, 2))
        assertEquals(small(1, 2, 3), options)
    }

    @Test
    fun `meld inference splits kong pung and chow`() {
        val melds = PaohuziMeldInference.infer(small(1, 1, 1, 1, 2, 2, 2, 3, 4, 5))
        assertEquals(3, melds.size)
        assertEquals(PaohuziMeldKind.KONG, melds[0].kind)
        assertEquals(PaohuziMeldKind.PUNG, melds[1].kind)
        assertEquals(PaohuziMeldKind.CHOW, melds[2].kind)
    }

    @Test
    fun `meld inference ignores tiles that do not form a group`() {
        assertTrue(PaohuziMeldInference.infer(small(1, 5, 9)).isEmpty())
    }
}
