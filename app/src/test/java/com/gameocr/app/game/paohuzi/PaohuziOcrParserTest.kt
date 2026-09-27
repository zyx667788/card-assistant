package com.gameocr.app.game.paohuzi

import com.gameocr.app.game.core.BoardZone
import com.gameocr.app.game.core.BoardZoneRole
import com.gameocr.app.game.core.NormalizedRect
import com.gameocr.app.game.core.TileTextSpan
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaohuziOcrParserTest {

    private val parser = PaohuziOcrParser()

    private val fullScreenZone = BoardZone(
        id = "hand",
        label = "我的手牌",
        role = BoardZoneRole.SELF_HAND,
        rect = NormalizedRect(0f, 0f, 1f, 1f),
    )

    private fun span(text: String, left: Int, top: Int, size: Int = 20): TileTextSpan =
        TileTextSpan(text = text, left = left, top = top, right = left + size, bottom = top + size)

    @Test
    fun `sorts a single row from left to right`() {
        val spans = listOf(span("三", 100, 10), span("一", 10, 10), span("二", 55, 10))
        assertEquals(
            listOf(
                PaohuziTile(1, TileCase.SMALL),
                PaohuziTile(2, TileCase.SMALL),
                PaohuziTile(3, TileCase.SMALL),
            ),
            parser.parseInReadingOrder(spans),
        )
    }

    @Test
    fun `reads an upper row before a lower row`() {
        val spans = listOf(span("四", 10, 200), span("一", 10, 10), span("二", 60, 10))
        assertEquals(
            listOf(
                PaohuziTile(1, TileCase.SMALL),
                PaohuziTile(2, TileCase.SMALL),
                PaohuziTile(4, TileCase.SMALL),
            ),
            parser.parseInReadingOrder(spans),
        )
    }

    @Test
    fun `ignores spans that contain no tile glyph`() {
        val spans = listOf(span("可以吃", 10, 10), span("五", 200, 10))
        assertEquals(listOf(PaohuziTile(5, TileCase.SMALL)), parser.parseInReadingOrder(spans))
    }

    @Test
    fun `zone filter drops tiles outside the rectangle`() {
        val leftHalf = fullScreenZone.copy(
            rect = NormalizedRect(0f, 0f, 0.5f, 1f),
        )
        val spans = listOf(span("一", 10, 10), span("二", 900, 10))
        assertEquals(
            listOf(PaohuziTile(1, TileCase.SMALL)),
            parser.tilesInZone(spans, leftHalf, imageWidth = 1000, imageHeight = 1000),
        )
    }

    @Test
    fun `text in zone keeps raw characters`() {
        val spans = listOf(span("可以吃", 10, 10), span("五", 200, 10))
        val text = parser.textInZone(spans, fullScreenZone, imageWidth = 1000, imageHeight = 1000)
        assertTrue(text.contains("可以吃"))
        assertTrue(text.contains("五"))
    }
}

class PaohuziBoardRecognizerTest {

    private val recognizer = PaohuziBoardRecognizer()

    private fun spanAt(text: String, centerX: Int, centerY: Int): TileTextSpan =
        TileTextSpan(
            text = text,
            left = centerX - 10,
            top = centerY - 10,
            right = centerX + 10,
            bottom = centerY + 10,
        )

    @Test
    fun `returns null when nothing is recognised in the hand zone`() {
        val state = runBlocking {
            recognizer.recognize(
                spans = listOf(spanAt("開始", 100, 100)),
                zones = PaohuziGameModule.DEFAULT_ZONES,
                imageWidth = 1000,
                imageHeight = 1000,
            )
        }
        assertNull(state)
    }

    @Test
    fun `reads the hand and treats a single prompt tile as the incoming tile`() {
        val spans = listOf(
            // 手牌区：画面下方 80%~90%
            spanAt("一", 100, 850),
            spanAt("二", 140, 850),
            spanAt("三", 180, 850),
            spanAt("四", 220, 850),
            spanAt("五", 260, 850),
            // 操作提示区：画面 62%~72%
            spanAt("可以吃 五", 500, 670),
        )
        val state = runBlocking {
            recognizer.recognize(
                spans = spans,
                zones = PaohuziGameModule.DEFAULT_ZONES,
                imageWidth = 1000,
                imageHeight = 1000,
            )
        } as PaohuziState

        assertEquals(
            listOf(
                PaohuziTile(1, TileCase.SMALL),
                PaohuziTile(2, TileCase.SMALL),
                PaohuziTile(3, TileCase.SMALL),
                PaohuziTile(4, TileCase.SMALL),
                PaohuziTile(5, TileCase.SMALL),
            ),
            state.hand,
        )
        assertEquals(PaohuziTile(5, TileCase.SMALL), state.incomingTile)
        assertTrue(state.actionHint!!.contains("可以吃"))
    }

    @Test
    fun `keeps incoming tile empty when the prompt is ambiguous`() {
        val spans = listOf(
            spanAt("一", 100, 850),
            spanAt("二", 140, 850),
            spanAt("三", 180, 850),
            spanAt("可以吃 二 五", 500, 670),
        )
        val state = runBlocking {
            recognizer.recognize(
                spans = spans,
                zones = PaohuziGameModule.DEFAULT_ZONES,
                imageWidth = 1000,
                imageHeight = 1000,
            )
        } as PaohuziState

        assertNull(state.incomingTile)
    }

    @Test
    fun `keeps tiles out of roles they were not recognised in`() {
        val state = runBlocking {
            recognizer.recognize(
                spans = listOf(spanAt("一", 100, 850), spanAt("二", 140, 850)),
                zones = PaohuziGameModule.DEFAULT_ZONES,
                imageWidth = 1000,
                imageHeight = 1000,
            )
        } as PaohuziState

        assertEquals(2, state.hand.size)
        assertTrue(state.tableDiscards.isEmpty())
        assertTrue(state.melds.isEmpty())
    }
}
