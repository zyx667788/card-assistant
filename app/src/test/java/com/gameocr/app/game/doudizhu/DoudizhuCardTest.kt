package com.gameocr.app.game.doudizhu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoudizhuCardTest {

    private fun cards(text: String) = DoudizhuCardCodec.parseAll(text)

    @Test
    fun `parses chinese suit names`() {
        assertEquals(DoudizhuCard(DoudizhuRank.SEVEN, DoudizhuSuit.SPADE), DoudizhuCardCodec.parse("黑桃7"))
        assertEquals(DoudizhuCard(DoudizhuRank.TEN, DoudizhuSuit.HEART), DoudizhuCardCodec.parse("红桃10"))
        assertEquals(DoudizhuCard(DoudizhuRank.JACK, DoudizhuSuit.CLUB), DoudizhuCardCodec.parse("草花J"))
        assertEquals(DoudizhuCard(DoudizhuRank.ACE, DoudizhuSuit.DIAMOND), DoudizhuCardCodec.parse("方块A"))
    }

    @Test
    fun `parses letter and symbol suits`() {
        assertEquals(DoudizhuSuit.SPADE, DoudizhuCardCodec.parse("7s")?.suit)
        assertEquals(DoudizhuSuit.HEART, DoudizhuCardCodec.parse("10h")?.suit)
        assertEquals(DoudizhuSuit.CLUB, DoudizhuCardCodec.parse("Jc")?.suit)
        assertEquals(DoudizhuSuit.DIAMOND, DoudizhuCardCodec.parse("Ad")?.suit)
        assertEquals(DoudizhuSuit.SPADE, DoudizhuCardCodec.parse("♠7")?.suit)
        assertEquals(DoudizhuSuit.HEART, DoudizhuCardCodec.parse("7♥")?.suit)
    }

    @Test
    fun `parses jokers without suit`() {
        val small = DoudizhuCardCodec.parse("小王")
        val big = DoudizhuCardCodec.parse("大王")
        assertEquals(DoudizhuRank.SMALL_JOKER, small?.rank)
        assertEquals(DoudizhuRank.BIG_JOKER, big?.rank)
        assertEquals(DoudizhuSuit.JOKER, small?.suit)
        assertEquals(DoudizhuSuit.JOKER, big?.suit)
    }

    @Test
    fun `parses rank without suit as unknown suit`() {
        val card = DoudizhuCardCodec.parse("7")
        assertEquals(DoudizhuRank.SEVEN, card?.rank)
        assertEquals(DoudizhuSuit.UNKNOWN, card?.suit)
    }

    @Test
    fun `unknown token returns null`() {
        assertNull(DoudizhuCardCodec.parse("看不清"))
        assertNull(DoudizhuCardCodec.parse(""))
    }

    /** 用户实测的牌型：4 个 7 必须是 4 张不同的牌，不能被解析层合并。 */
    @Test
    fun `parses four sevens with different suits`() {
        val hand = cards("黑桃7 红桃7 梅花7 方块7")
        assertEquals(4, hand.size)
        assertEquals(4, hand.map { it.suit }.toSet().size)
        assertEquals(4, DoudizhuCardCodec.countsByRank(hand)[DoudizhuRank.SEVEN.order])
    }

    @Test
    fun `parseAll splits common separators`() {
        assertEquals(3, cards("黑桃A,红桃A、梅花A").size)
        assertEquals(2, cards("小王 大王").size)
    }

    @Test
    fun `format keeps suits so the prompt can be verified`() {
        val hand = cards("黑桃7 红桃7")
        assertEquals("黑桃7 红桃7", DoudizhuCardCodec.format(hand))
        assertEquals("7s 7h", DoudizhuCardCodec.formatCodes(hand))
    }

    @Test
    fun `sortedForDisplay orders by rank then suit`() {
        val hand = cards("方块9 黑桃3 红桃3 大王")
        val sorted = hand.sortedForDisplay()
        assertEquals(
            listOf(DoudizhuRank.THREE, DoudizhuRank.THREE, DoudizhuRank.NINE, DoudizhuRank.BIG_JOKER),
            sorted.map { it.rank },
        )
        assertEquals(DoudizhuSuit.SPADE, sorted.first().suit)
    }
}
