package com.gameocr.app.game.core

/**
 * 相对截屏画面的归一化矩形，四个分量都在 0f..1f。
 *
 * 用归一化坐标而不是像素，是为了让同一份区域配置在不同分辨率 / 横竖屏之间复用。
 */
import kotlinx.serialization.Serializable

@Serializable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(right >= left) { "right($right) must be >= left($left)" }
        require(bottom >= top) { "bottom($bottom) must be >= top($top)" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    /** 换算成像素矩形，用于和 OCR 的像素坐标比对。 */
    fun toPixelBounds(imageWidth: Int, imageHeight: Int): PixelBounds = PixelBounds(
        left = (left * imageWidth).toInt(),
        top = (top * imageHeight).toInt(),
        right = (right * imageWidth).toInt(),
        bottom = (bottom * imageHeight).toInt(),
    )
}

/** 像素矩形（左闭右开由调用方自行约定，这里只做几何比较）。 */
data class PixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom
}

/** 画面上一个语义区域的用途。 */
@Serializable
enum class BoardZoneRole(val displayName: String) {
    SELF_HAND("我的手牌"),
    SELF_MELDS("我亮出的组合"),
    OPPONENT_LEFT_MELDS("左家亮牌"),
    OPPONENT_RIGHT_MELDS("右家亮牌"),
    TABLE_DISCARDS("桌面已出的牌"),
    ACTION_PROMPT("当前操作提示"),
    REMAINING_COUNT("剩余牌数"),
}

/**
 * 一次识别中要裁切的语义区域。
 *
 * 同一 [BoardZoneRole] 可以出现多个区域（例如两行手牌），识别时按 [id] 区分。
 */
@Serializable
data class BoardZone(
    val id: String,
    val label: String,
    val role: BoardZoneRole,
    val rect: NormalizedRect,
)
