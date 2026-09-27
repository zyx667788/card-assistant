package com.gameocr.app.game.paohuzi

import com.gameocr.app.game.core.AdviceContext
import org.junit.Assert.assertTrue
import org.junit.Test

class PaohuziPromptPolicyTest {

    private val policy = PaohuziPromptPolicy(rulesSummary = "顺子只在同一门内成立。")

    private fun small(vararg ranks: Int): List<PaohuziTile> =
        ranks.map { PaohuziTile(it, TileCase.SMALL) }

    @Test
    fun `system prompt carries the rules summary and the output contract`() {
        val prompt = policy.systemPrompt()
        assertTrue(prompt.contains("顺子只在同一门内成立"))
        assertTrue(prompt.contains("\"action\""))
        assertTrue(prompt.contains("\"reason\""))
    }

    @Test
    fun `my turn prompt lists legal discards`() {
        val state = PaohuziState(hand = small(1, 2, 3, 4, 5, 6, 7))
        val prompt = policy.userPrompt(state, AdviceContext())
        assertTrue(prompt.contains("【本地规则引擎已确认的合法动作】"))
        assertTrue(prompt.contains("打「"))
    }

    @Test
    fun `my turn prompt mentions the wait after a discard`() {
        // 8 张 = 两组 + 一对，扔掉一张七之后进七即成胡。
        val state = PaohuziState(hand = small(1, 2, 3, 4, 5, 6, 7, 7))
        val prompt = policy.userPrompt(state, AdviceContext())
        assertTrue(prompt.contains("可胡"))
    }

    @Test
    fun `incoming tile prompt offers chow and pass`() {
        val state = PaohuziState(
            hand = small(2, 3),
            incomingTile = PaohuziTile(1, TileCase.SMALL),
        )
        val prompt = policy.userPrompt(state, AdviceContext())
        assertTrue(prompt.contains("吃："))
        assertTrue(prompt.contains("过："))
    }

    @Test
    fun `incoming tile prompt offers kong when three copies are held`() {
        val five = PaohuziTile(5, TileCase.SMALL)
        val state = PaohuziState(hand = small(5, 5, 5, 1, 2), incomingTile = five)
        val prompt = policy.userPrompt(state, AdviceContext())
        assertTrue(prompt.contains("提："))
    }

    @Test
    fun `extra instructions are appended to the user prompt`() {
        val state = PaohuziState(hand = small(1, 2, 3, 4, 5, 6, 7))
        val prompt = policy.userPrompt(state, AdviceContext(extraInstructions = "优先留大字"))
        assertTrue(prompt.contains("【用户额外要求】"))
        assertTrue(prompt.contains("优先留大字"))
    }

    @Test
    fun `state text lists hand and turnaround`() {
        val state = PaohuziState(hand = small(1, 2, 3, 4, 5, 6, 7))
        val text = state.toPromptText()
        assertTrue(text.contains("【我的手牌】"))
        assertTrue(text.contains("轮到我出牌"))
    }
}
