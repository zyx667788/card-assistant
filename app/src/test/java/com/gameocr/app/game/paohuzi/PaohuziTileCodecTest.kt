package com.gameocr.app.game.paohuzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaohuziTileCodecTest {

    @Test
    fun `parses small numerals in order`() {
        assertEquals(
            listOf(
                PaohuziTile(1, TileCase.SMALL),
                PaohuziTile(2, TileCase.SMALL),
                PaohuziTile(3, TileCase.SMALL),
            ),
            PaohuziTileCodec.parse("一二三"),
        )
    }

    @Test
    fun `parses big numerals as big case`() {
        assertEquals(
            listOf(
                PaohuziTile(1, TileCase.BIG),
                PaohuziTile(2, TileCase.BIG),
                PaohuziTile(10, TileCase.BIG),
            ),
            PaohuziTileCodec.parse("壹贰拾"),
        )
    }

    @Test
    fun `ambiguous glyph resolves to big case`() {
        assertEquals(PaohuziTile(1, TileCase.BIG), PaohuziTileCodec.parse("壹").single())
    }

    @Test
    fun `normalizes common ocr variants`() {
        assertEquals(PaohuziTile(3, TileCase.BIG), PaohuziTileCodec.parse("參").single())
        assertEquals(PaohuziTile(6, TileCase.BIG), PaohuziTileCodec.parse("陸").single())
        assertEquals(PaohuziTile(2, TileCase.BIG), PaohuziTileCodec.parse("貳").single())
    }

    @Test
    fun `ignores separators and noise`() {
        assertEquals(4, PaohuziTileCodec.parse("一 二\t三、四").size)
    }

    @Test
    fun `returns empty when no tile glyph is present`() {
        assertEquals(emptyList<PaohuziTile>(), PaohuziTileCodec.parse("可以吃"))
    }

    @Test
    fun `unknown character has no tile`() {
        assertNull(PaohuziTileCodec.parseChar('好'))
    }

    @Test
    fun `format round trips through parse`() {
        val tiles = listOf(PaohuziTile(1, TileCase.SMALL), PaohuziTile(10, TileCase.BIG))
        assertEquals(tiles, PaohuziTileCodec.parse(PaohuziTileCodec.format(tiles)))
    }
}
