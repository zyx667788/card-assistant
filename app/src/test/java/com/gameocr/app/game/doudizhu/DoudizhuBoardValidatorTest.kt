package com.gameocr.app.game.doudizhu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoudizhuBoardValidatorTest {

    private fun cards(text: String) = DoudizhuCardCodec.parseAll(text)

    /** 用户实测的场景：4 个 7 两红两黑，只有 4 张不同的牌才是对的。 */
    @Test
    fun `four sevens with four suits are accepted`() {
        val hand = cards("黑桃7 红桃7 梅花7 方块7")
        val result = DoudizhuBoardValidator.validate(hand, role = DoudizhuRole.FARMER)
        assertEquals(4, hand.size)
        assertFalse("四张不同花色的 7 不应报错", result.isFatal)
    }

    /** 同一张牌出现两次 → 模型把一张牌重复算了，必须重新识别。 */
    @Test
    fun `duplicated physical card is fatal`() {
        val result = DoudizhuBoardValidator.validate(
            cards("黑桃7 黑桃7 红桃7"),
            role = DoudizhuRole.FARMER,
        )
        assertTrue(result.isFatal)
        assertTrue(
            "错误信息应指出具体是哪张牌",
            result.errors.any { it.contains("黑桃7") },
        )
    }

    @Test
    fun `more than four cards of one rank is fatal`() {
        val result = DoudizhuBoardValidator.validate(
            cards("黑桃7 红桃7 梅花7 方块7 7"),
            role = DoudizhuRole.FARMER,
        )
        assertTrue(result.isFatal)
        assertTrue(result.errors.any { it.contains("7") })
    }

    @Test
    fun `same card cannot be in hand and already played`() {
        val result = DoudizhuBoardValidator.validate(
            hand = cards("黑桃7 红桃8"),
            playedCards = cards("黑桃7"),
            role = DoudizhuRole.FARMER,
        )
        assertTrue(result.isFatal)
    }

    @Test
    fun `missing suit only warns`() {
        val result = DoudizhuBoardValidator.validate(
            cards("3 4 5"),
            role = DoudizhuRole.FARMER,
        )
        assertFalse(result.isFatal)
        assertTrue(result.warnings.any { it.contains("花色") })
    }

    @Test
    fun `wrong hand size is a warning not an error`() {
        val result = DoudizhuBoardValidator.validate(
            cards("黑桃3 红桃4 梅花5"),
            role = DoudizhuRole.LANDLORD,
        )
        assertFalse(result.isFatal)
        assertTrue(result.warnings.any { it.contains("地主") })
    }

    @Test
    fun `unknown role is reported`() {
        val result = DoudizhuBoardValidator.validate(cards("黑桃3 红桃4"))
        assertTrue(result.warnings.any { it.contains("身份") })
    }
}

class DoudizhuVlmBoardParserTest {

    @Test
    fun `parses payload into state`() {
        val payload = DoudizhuVlmPayload(
            hand = listOf("黑桃7", "红桃7", "梅花7", "方块7"),
            role = "农民",
            bottomCards = listOf("黑桃3", "红桃5", "大王"),
            lastPlaySeat = "上家",
            lastPlay = listOf("红桃8", "方块8"),
            playedCards = listOf("黑桃9"),
            myRemaining = 4,
            upRemaining = 9,
            downRemaining = 11,
            actionHint = "轮到我出牌",
            globalObservation = "地主还剩 5 张",
        )

        val state = DoudizhuVlmBoardParser.parse(payload)
        assertNotNull(state)
        requireNotNull(state)
        assertEquals(DoudizhuRole.FARMER, state.role)
        assertEquals(4, state.hand.size)
        assertEquals(DoudizhuSeat.UP, state.lastPlay?.seat)
        assertEquals(2, state.lastPlay?.cards?.size)
        assertTrue(state.mustBeat)

        val text = state.toPromptText()
        assertTrue(text.contains("红桃8"))
        assertTrue(text.contains("上家"))
        assertTrue(text.contains("地主还剩 5 张"))
    }

    @Test
    fun `empty hand yields null`() {
        assertEquals(null, DoudizhuVlmBoardParser.parse(DoudizhuVlmPayload()))
    }

    @Test
    fun `role and seat aliases are accepted`() {
        assertEquals(DoudizhuRole.LANDLORD, DoudizhuVlmBoardParser.parseRole("landlord"))
        assertEquals(DoudizhuRole.FARMER, DoudizhuVlmBoardParser.parseRole("农民"))
        assertEquals(DoudizhuRole.UNKNOWN, DoudizhuVlmBoardParser.parseRole(null))
        assertEquals(DoudizhuSeat.ME, DoudizhuVlmBoardParser.parseSeat("我"))
        assertEquals(DoudizhuSeat.DOWN, DoudizhuVlmBoardParser.parseSeat("下家"))
        assertEquals(DoudizhuSeat.UNKNOWN, DoudizhuVlmBoardParser.parseSeat("谁知道"))
    }

    /** 本地校验要能挡住「同一张牌被算了两次」这种不可信识别。 */
    @Test
    fun `payload with duplicated card is fatal`() {
        val state = DoudizhuVlmBoardParser.parse(
            DoudizhuVlmPayload(hand = listOf("黑桃7", "黑桃7")),
        )
        assertNotNull(state)
        assertTrue(state!!.validation.isFatal)
    }
}
