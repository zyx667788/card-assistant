package com.gameocr.app.game.doudizhu

import com.gameocr.app.game.doudizhu.DoudizhuRank.ACE
import com.gameocr.app.game.doudizhu.DoudizhuRank.EIGHT
import com.gameocr.app.game.doudizhu.DoudizhuRank.FIVE
import com.gameocr.app.game.doudizhu.DoudizhuRank.FOUR
import com.gameocr.app.game.doudizhu.DoudizhuRank.JACK
import com.gameocr.app.game.doudizhu.DoudizhuRank.KING
import com.gameocr.app.game.doudizhu.DoudizhuRank.NINE
import com.gameocr.app.game.doudizhu.DoudizhuRank.QUEEN
import com.gameocr.app.game.doudizhu.DoudizhuRank.SEVEN
import com.gameocr.app.game.doudizhu.DoudizhuRank.SIX
import com.gameocr.app.game.doudizhu.DoudizhuRank.TEN
import com.gameocr.app.game.doudizhu.DoudizhuRank.THREE
import com.gameocr.app.game.doudizhu.DoudizhuRank.TWO
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.AIRPLANE
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.AIRPLANE_PAIR
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.AIRPLANE_SINGLE
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.BOMB
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.CONSECUTIVE_PAIRS
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.FOUR_TWO_SINGLE
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.PAIR
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.ROCKET
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.SINGLE
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.STRAIGHT
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.TRIPLE
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.TRIPLE_PAIR
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.TRIPLE_SINGLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoudizhuPatternTest {

    private fun cards(vararg ranks: DoudizhuRank): List<DoudizhuCard> = ranks.map { DoudizhuCard(it) }

    private fun detect(vararg ranks: DoudizhuRank) = DoudizhuPatterns.detect(cards(*ranks))

    @Test
    fun `recognises basic shapes`() {
        assertEquals(SINGLE, detect(SEVEN)?.type)
        assertEquals(PAIR, detect(SEVEN, SEVEN)?.type)
        assertEquals(TRIPLE, detect(SEVEN, SEVEN, SEVEN)?.type)
        assertEquals(BOMB, detect(SEVEN, SEVEN, SEVEN, SEVEN)?.type)
        assertEquals(ROCKET, detect(DoudizhuRank.SMALL_JOKER, DoudizhuRank.BIG_JOKER)?.type)
    }

    @Test
    fun `recognises triple with wings`() {
        assertEquals(TRIPLE_SINGLE, detect(EIGHT, EIGHT, EIGHT, THREE)?.type)
        assertEquals(TRIPLE_PAIR, detect(EIGHT, EIGHT, EIGHT, THREE, THREE)?.type)
        assertEquals(EIGHT.order, detect(EIGHT, EIGHT, EIGHT, THREE)?.primary)
    }

    @Test
    fun `straight needs five cards inside 3 to ace`() {
        val straight = detect(THREE, FOUR, FIVE, SIX, SEVEN)
        assertEquals(STRAIGHT, straight?.type)
        assertEquals(5, straight?.length)
        assertEquals(SEVEN.order, straight?.primary)

        assertNull("四张不成顺子", detect(THREE, FOUR, FIVE, SIX))
        assertNull("2 不能进顺子", detect(JACK, QUEEN, KING, ACE, TWO))
        assertNull("相同点数不成顺子", detect(THREE, THREE, FOUR, FIVE, SIX))
    }

    @Test
    fun `consecutive pairs need three pairs`() {
        val pairs = detect(THREE, THREE, FOUR, FOUR, FIVE, FIVE)
        assertEquals(CONSECUTIVE_PAIRS, pairs?.type)
        assertEquals(3, pairs?.length)
        assertNull("两对不算连对", detect(THREE, THREE, FOUR, FOUR))
    }

    @Test
    fun `airplane supports no wings singles and pairs`() {
        val body = arrayOf(THREE, THREE, THREE, FOUR, FOUR, FOUR)
        assertEquals(AIRPLANE, DoudizhuPatterns.detect(cards(*body))?.type)

        val withSingles = cards(*(body + arrayOf(FIVE, SIX)))
        assertEquals(AIRPLANE_SINGLE, DoudizhuPatterns.detect(withSingles)?.type)

        val withPairs = cards(*(body + arrayOf(FIVE, FIVE, SIX, SIX)))
        assertEquals(AIRPLANE_PAIR, DoudizhuPatterns.detect(withPairs)?.type)
    }

    @Test
    fun `four with two singles`() {
        assertEquals(FOUR_TWO_SINGLE, detect(NINE, NINE, NINE, NINE, FIVE, SIX)?.type)
    }

    @Test
    fun `non consecutive triples are not an airplane`() {
        assertNull(detect(THREE, THREE, THREE, FIVE, FIVE, FIVE))
    }

    @Test
    fun `beating rules`() {
        val pairSeven = detect(SEVEN, SEVEN)!!
        val pairEight = detect(EIGHT, EIGHT)!!
        val bomb = detect(NINE, NINE, NINE, NINE)!!
        val rocket = detect(DoudizhuRank.SMALL_JOKER, DoudizhuRank.BIG_JOKER)!!

        assertTrue(DoudizhuPatterns.canBeat(pairEight, pairSeven))
        assertFalse(DoudizhuPatterns.canBeat(pairSeven, pairEight))
        assertTrue("炸弹压普通牌", DoudizhuPatterns.canBeat(bomb, pairEight))
        assertFalse("普通牌压不了炸弹", DoudizhuPatterns.canBeat(pairEight, bomb))
        assertTrue("火箭压炸弹", DoudizhuPatterns.canBeat(rocket, bomb))
        assertFalse(DoudizhuPatterns.canBeat(bomb, rocket))
    }

    @Test
    fun `straight must match length`() {
        val five = detect(THREE, FOUR, FIVE, SIX, SEVEN)!!
        val six = detect(THREE, FOUR, FIVE, SIX, SEVEN, EIGHT)!!
        val higherFive = detect(FOUR, FIVE, SIX, SEVEN, EIGHT)!!

        assertFalse("长度不同不能压", DoudizhuPatterns.canBeat(six, five))
        assertTrue("同样 5 张比大小", DoudizhuPatterns.canBeat(higherFive, five))
    }

    @Test
    fun `different shapes do not beat each other`() {
        val pair = detect(EIGHT, EIGHT)!!
        val single = detect(TEN)!!
        assertFalse(DoudizhuPatterns.canBeat(single, pair))
        assertFalse(DoudizhuPatterns.canBeat(pair, single))
    }

    @Test
    fun `free lead accepts any legal shape`() {
        assertTrue(DoudizhuPatterns.canBeat(detect(THREE)!!, null))
    }
}
