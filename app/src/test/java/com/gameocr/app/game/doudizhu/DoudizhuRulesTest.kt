package com.gameocr.app.game.doudizhu

import com.gameocr.app.game.doudizhu.DoudizhuPatternType.BOMB
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.PAIR
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.ROCKET
import com.gameocr.app.game.doudizhu.DoudizhuPatternType.STRAIGHT
import com.gameocr.app.game.doudizhu.DoudizhuRank.BIG_JOKER
import com.gameocr.app.game.doudizhu.DoudizhuRank.EIGHT
import com.gameocr.app.game.doudizhu.DoudizhuRank.FIVE
import com.gameocr.app.game.doudizhu.DoudizhuRank.FOUR
import com.gameocr.app.game.doudizhu.DoudizhuRank.JACK
import com.gameocr.app.game.doudizhu.DoudizhuRank.NINE
import com.gameocr.app.game.doudizhu.DoudizhuRank.QUEEN
import com.gameocr.app.game.doudizhu.DoudizhuRank.SEVEN
import com.gameocr.app.game.doudizhu.DoudizhuRank.SIX
import com.gameocr.app.game.doudizhu.DoudizhuRank.SMALL_JOKER
import com.gameocr.app.game.doudizhu.DoudizhuRank.TEN
import com.gameocr.app.game.doudizhu.DoudizhuRank.THREE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoudizhuRulesTest {

    private val rules = DoudizhuRules()

    private fun hand(vararg ranks: DoudizhuRank): List<DoudizhuCard> = ranks.map { DoudizhuCard(it) }

    private fun move(vararg ranks: DoudizhuRank): DoudizhuMove {
        val cards = hand(*ranks)
        return DoudizhuMove(cards, DoudizhuPatterns.detect(cards)!!)
    }

    @Test
    fun `must beat the target with a bigger pair`() {
        val moves = rules.legalMoves(hand(SEVEN, SEVEN, EIGHT, EIGHT), move(SEVEN, SEVEN))
        assertTrue(
            "对 8 能压对 7",
            moves.any { it.pattern.type == PAIR && it.pattern.primary == EIGHT.order },
        )
        assertFalse(
            "同点数的对子压不过",
            moves.any { it.pattern.type == PAIR && it.pattern.primary == SEVEN.order },
        )
    }

    @Test
    fun `returns nothing when the hand cannot beat the target`() {
        val moves = rules.legalMoves(hand(THREE, THREE, FOUR), move(NINE, NINE))
        assertTrue("要不起时应为空", moves.isEmpty())
    }

    @Test
    fun `offers bomb and rocket`() {
        assertTrue(
            "四张同点应给出炸弹",
            rules.legalMoves(hand(NINE, NINE, NINE, NINE), null).any { it.pattern.type == BOMB },
        )
        assertTrue(
            "双王应给出火箭",
            rules.legalMoves(hand(SMALL_JOKER, BIG_JOKER), null).any { it.pattern.type == ROCKET },
        )
    }

    @Test
    fun `bomb can beat a plain pair`() {
        val moves = rules.legalMoves(hand(NINE, NINE, NINE, NINE, THREE), move(EIGHT, EIGHT))
        assertTrue(moves.any { it.pattern.type == BOMB })
    }

    @Test
    fun `generates straights`() {
        val moves = rules.legalMoves(hand(THREE, FOUR, FIVE, SIX, SEVEN), null)
        assertTrue(moves.any { it.pattern.type == STRAIGHT && it.pattern.length == 5 })
    }

    /** 手牌多、单张/对子候选多时，顺子不能被候选条数上限挤掉。 */
    @Test
    fun `big shapes survive the candidate cap`() {
        val ranks = listOf(
            THREE, THREE, FOUR, FOUR, FIVE, FIVE, SIX, SIX, SEVEN, SEVEN,
            EIGHT, EIGHT, NINE, NINE, TEN, TEN, JACK, JACK, QUEEN, QUEEN,
        )
        val moves = rules.legalMoves(hand(*ranks.toTypedArray()), null)
        assertTrue("20 张手牌里应该看到顺子", moves.any { it.pattern.type == STRAIGHT })
        assertTrue("顺子应该留下多条而不是被单张挤掉", moves.count { it.pattern.type == STRAIGHT } >= 2)
    }

    @Test
    fun `isLegal accepts a play from hand and rejects missing cards`() {
        val myHand = hand(SEVEN, SEVEN, EIGHT)
        assertTrue(rules.isLegal(myHand, hand(SEVEN, SEVEN), null))
        assertFalse(rules.isLegal(myHand, hand(NINE, NINE), null))
    }

    @Test
    fun `isLegal respects the target strength`() {
        assertFalse(
            "对 6 压不过对 7",
            rules.isLegal(hand(SIX, SIX), hand(SIX, SIX), move(SEVEN, SEVEN)),
        )
        assertTrue(
            "对 8 能压对 7",
            rules.isLegal(hand(EIGHT, EIGHT), hand(EIGHT, EIGHT), move(SEVEN, SEVEN)),
        )
    }
}
