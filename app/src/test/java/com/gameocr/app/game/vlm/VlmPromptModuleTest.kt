package com.gameocr.app.game.vlm

import com.gameocr.app.game.core.AdviceContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlmPromptStateTest {

@Test
fun `toPromptText returns the board text trimmed`() {
val state = VlmPromptState(moduleId = "doudizhu", boardText = " 3 4 5\n")
assertEquals("3 4 5", state.toPromptText())
}

@Test
fun `summary uses the first non-blank line`() {
val state = VlmPromptState(moduleId = "doudizhu", boardText = "\n 3 4 5\n地主\n")
assertEquals("3 4 5", state.summary())
}

@Test
fun `summary falls back when the board text is blank`() {
val state = VlmPromptState(moduleId = "doudizhu", boardText = " \n ")
assertEquals("已识别牌局", state.summary())
}
}

class VlmPromptPolicyTest {

private val policy = VlmPromptPolicy(
rulesSummary = "一副 54 张牌。",
advisorSystemPrompt = "你是斗地主陪练。一副 54 张牌。只输出 JSON。",
)

private fun state() = VlmPromptState(
moduleId = "doudizhu",
boardText = "3 4 5 6\n轮到我出牌",
)

@Test
fun `system prompt is the advisor prompt verbatim`() {
assertEquals("你是斗地主陪练。一副 54 张牌。只输出 JSON。", policy.systemPrompt())
}

@Test
fun `user prompt embeds the recognized board text`() {
val prompt = policy.userPrompt(state(), AdviceContext())
assertTrue(prompt.contains(""))
assertTrue(prompt.contains("3 4 5 6"))
assertTrue(prompt.contains("请按约定格式给出建议"))
}

@Test
fun `user prompt appends history and extra instructions`() {
val prompt = policy.userPrompt(
state(),
AdviceContext(
extraInstructions = "多留炸弹",
history = listOf("上次建议：出 3456"),
),
)
assertTrue(prompt.contains(""))
assertTrue(prompt.contains("多留炸弹"))
assertTrue(prompt.contains(""))
assertTrue(prompt.contains("上次建议：出 3456"))
}

@Test
fun `module carries its id and display name`() {
val module = VlmPromptGameModule(
id = "doudizhu",
displayName = "斗地主",
promptPolicy = policy,
recognizer = FakeRecognizer(),
)
assertEquals("doudizhu", module.id)
assertEquals("斗地主", module.displayName)
assertTrue(module.defaultZones.isEmpty())
}

private class FakeRecognizer: com.gameocr.app.game.core.BoardRecognizer {
override suspend fun recognize(
spans: List<com.gameocr.app.game.core.TileTextSpan>,
zones: List<com.gameocr.app.game.core.BoardZone>,
imageWidth: Int,
imageHeight: Int,
screenshotJpeg: ByteArray?,
): com.gameocr.app.game.core.GameState? = null
}
}
