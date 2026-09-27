package com.gameocr.app.game.integration

import com.gameocr.app.game.core.TileTextSpan
import com.gameocr.app.ocr.TextBlock

/** Android OCR 结果到牌局领域层的无损桥接。 */
fun TextBlock.toTileTextSpan(): TileTextSpan = TileTextSpan(
    text = text,
    left = boundingBox.left,
    top = boundingBox.top,
    right = boundingBox.right,
    bottom = boundingBox.bottom,
    confidence = confidence,
)

fun List<TextBlock>.toTileTextSpans(): List<TileTextSpan> = map(TextBlock::toTileTextSpan)
