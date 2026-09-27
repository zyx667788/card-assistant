package com.gameocr.app.game.advice

import com.gameocr.app.game.core.AdviceAction
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdviceResponseParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses a clean json object`() {
        val advice = AdviceResponseParser.parse(
            """{"action":"play","tile":"五","reason":"保留顺子","alternatives":["打三"]}""",
            json,
        )
        assertEquals(AdviceAction.PLAY, advice.action)
        assertEquals("五", advice.targetTile)
        assertEquals("保留顺子", advice.reason)
        assertEquals(listOf("打三"), advice.alternatives)
    }

    @Test
    fun `maps chinese action words`() {
        assertEquals(AdviceAction.CHOW, AdviceResponseParser.parse("""{"action":"吃"}""", json).action)
        assertEquals(AdviceAction.PUNG, AdviceResponseParser.parse("""{"action":"碰"}""", json).action)
        assertEquals(AdviceAction.WIN, AdviceResponseParser.parse("""{"action":"胡"}""", json).action)
        assertEquals(AdviceAction.PASS, AdviceResponseParser.parse("""{"action":"过"}""", json).action)
    }

    @Test
    fun `accepts json wrapped in a markdown fence`() {
        val raw = "建议如下：\n```json\n{\"action\":\"pass\",\"reason\":\"不值得\"}\n```"
        val advice = AdviceResponseParser.parse(raw, json)
        assertEquals(AdviceAction.PASS, advice.action)
        assertEquals("不值得", advice.reason)
    }

    @Test
    fun `handles braces inside strings`() {
        val advice = AdviceResponseParser.parse(
            """{"action":"play","reason":"打{五}更稳"}""",
            json,
        )
        assertEquals(AdviceAction.PLAY, advice.action)
        assertEquals("打{五}更稳", advice.reason)
    }

    @Test
    fun `unknown action falls back to unknown`() {
        assertEquals(AdviceAction.UNKNOWN, AdviceResponseParser.parse("""{"action":"跳舞"}""", json).action)
    }

    @Test
    fun `keeps the raw text when nothing parses`() {
        val advice = AdviceResponseParser.parse("今天天气不错", json)
        assertEquals(AdviceAction.UNKNOWN, advice.action)
        assertEquals("今天天气不错", advice.rawText)
        assertNull(advice.targetTile)
    }

    @Test
    fun `missing optional fields stay empty`() {
        val advice = AdviceResponseParser.parse("""{"action":"pass"}""", json)
        assertEquals(AdviceAction.PASS, advice.action)
        assertNull(advice.targetTile)
        assertEquals("", advice.reason)
        assertEquals(emptyList<String>(), advice.alternatives)
    }

    @Test
    fun `extracts the outermost object only`() {
        val extracted = AdviceResponseParser.extractJsonObject("前缀 {\"a\":{\"b\":1}} 后缀")
        assertEquals("""{"a":{"b":1}}""", extracted)
    }
}
