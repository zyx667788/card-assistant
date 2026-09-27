package com.gameocr.app.game.paohuzi

import com.gameocr.app.game.core.BoardZone
import com.gameocr.app.game.core.TileTextSpan
import kotlin.math.abs

/**
 * 把 OCR 文本块还原成牌。
 *
 * 手牌通常排成一行或多行，所以先按纵向位置聚成行，再在行内按横坐标从左到右排。
 * 纯数据实现，不依赖 Android，可在普通 JVM 单元测试里验证。
 */
class PaohuziOcrParser(
    /** 聚行时允许的纵向偏差，相对文本块高度的倍数。 */
    private val rowToleranceRatio: Float = 0.6f,
) {

    /** 取落在 [zone] 内的文本块。 */
    fun spansInZone(
        spans: List<TileTextSpan>,
        zone: BoardZone,
        imageWidth: Int,
        imageHeight: Int,
    ): List<TileTextSpan> {
        val bounds = zone.rect.toPixelBounds(imageWidth, imageHeight)
        return spans.filter { bounds.contains(it.centerX, it.centerY) }
    }

    /** 取落在 [zone] 内的原始文本，用空格连接，供「操作提示」这类区域使用。 */
    fun textInZone(
        spans: List<TileTextSpan>,
        zone: BoardZone,
        imageWidth: Int,
        imageHeight: Int,
    ): String = spansInZone(spans, zone, imageWidth, imageHeight)
        .sortedBy { it.centerY }
        .joinToString(" ") { it.text.trim() }
        .trim()

    /** 取 [zone] 内的牌，按阅读顺序排列。 */
    fun tilesInZone(
        spans: List<TileTextSpan>,
        zone: BoardZone,
        imageWidth: Int,
        imageHeight: Int,
    ): List<PaohuziTile> = parseInReadingOrder(spansInZone(spans, zone, imageWidth, imageHeight))

    /** 按「先上后下、行内先左后右」的顺序解析出牌。 */
    fun parseInReadingOrder(spans: List<TileTextSpan>): List<PaohuziTile> {
        val meaningful = spans.filter { PaohuziTileCodec.parse(it.text).isNotEmpty() }
        return groupIntoRows(meaningful)
            .flatMap { row -> row.sortedBy { it.centerX }.flatMap { PaohuziTileCodec.parse(it.text) } }
    }

    private fun groupIntoRows(spans: List<TileTextSpan>): List<List<TileTextSpan>> {
        if (spans.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<TileTextSpan>>()
        val rowCenterY = mutableListOf<Float>()

        spans.sortedBy { it.centerY }.forEach { span ->
            val tolerance = (span.height * rowToleranceRatio).coerceAtLeast(1f)
            val target = rowCenterY.indexOfFirst { mean -> abs(mean - span.centerY) <= tolerance }
            if (target >= 0) {
                val row = rows[target]
                row += span
                rowCenterY[target] = row.sumOf { it.centerY }.toFloat() / row.size
            } else {
                rows += mutableListOf(span)
                rowCenterY += span.centerY.toFloat()
            }
        }
        return rows.map { it.toList() }
    }
}
