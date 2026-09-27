package com.gameocr.app.game.core

/**
 * 一段 OCR 文本的纯数据表示。
 *
 * 刻意不使用 `android.graphics.Rect`，这样牌面解析逻辑可以在普通 JVM 单元测试里跑。
 * Android 侧的适配器负责把 OCR 结果转换成这个类型。
 */
data class TileTextSpan(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float = 1f,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    /** 用于行聚合的容差基准；保证至少为 1，避免除零。 */
    val height: Int get() = (bottom - top).coerceAtLeast(1)

    val width: Int get() = (right - left).coerceAtLeast(1)
}
