package com.gameocr.app.data

import kotlinx.serialization.Serializable

/**
 * 打牌助手的全部设置。刻意保持精简：云端 VLM 识别 + 文本 LLM 决策，
 * 不再有翻译 / 本地 OCR / TTS / 词典等历史字段。
 */
@Serializable
data class Settings(
    // 云端 VLM（看牌）与文本 LLM（决策）共用同一套 OpenAI 兼容接口配置。
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com/v1/",
    val model: String = "deepseek-flash",
    val apiTimeoutSeconds: Int = 60,

    // 牌类模块（如 paohuzi 湖南跑胡子）。
    val gameModuleId: String = "paohuzi",
    /** 各模块的当局建议历史；服务重启后可继续提供上下文。 */
    val gameSessionHistoryByModule: Map<String, List<String>> = emptyMap(),

    // 悬浮球。
    val floatingButtonSizeDp: Int = 56,
    val floatingButtonAlpha: Float = 1f,
    val floatingButtonX: Int = -1,
    val floatingButtonY: Int = -1,
    val floatingButtonSnapToEdge: Boolean = true,
    val floatingButtonAutoDock: Boolean = false,
    val floatingButtonDockInsetDp: Int = 0,

    // 建议悬浮卡窗口几何（拖动/缩放后持久化）。
    val floatingWindowX: Int = -1,
    val floatingWindowY: Int = -1,
    val floatingWindowWidthDp: Int = 320,
    val floatingWindowHeightDp: Int = 180,
    val floatingWindowLocked: Boolean = false,

    // 悬浮卡外观。
    val overlayTextSizeSp: Int = 14,
    val overlayAlpha: Float = 0.85f,
    val overlayTheme: OverlayTheme = OverlayTheme.CLASSIC_DARK,
    val overlayTextStyle: OverlayTextStyle = OverlayTextStyle(),
    val overlayFontFileName: String = "",
    val customBgColor: Int = 0xE6000000.toInt(),
    val customFgColor: Int = 0xFFFFFFFF.toInt(),
    val customBorderColor: Int = 0,
    val customBorderWidth: Int = 0,
    val customBorderStyle: BorderStyle = BorderStyle.SOLID,
)

/**
 * 悬浮窗口边框样式。SOLID 是默认（跟 CSS `border-style: solid` 等价）。
 * 仅在主题本身有 stroke 时生效（AMBER_GOLD / PAPER_LIGHT / FROST_GLASS / CUSTOM with width>0）；
 * CLASSIC_DARK 默认无边，选啥样式都不画。
 */
@Serializable
enum class BorderStyle {
    SOLID,
    DASHED,
    DOTTED,
    DOUBLE,
    GROOVE
}

@Serializable
enum class OverlayTheme {
    /** 经典深色：黑底白字。 */
    CLASSIC_DARK,
    /** 琥珀黑金：深棕底 + 暖金字。 */
    AMBER_GOLD,
    /** 浅色纸张：米色底 + 深褐字。 */
    PAPER_LIGHT,
    /** 半透明霜玻璃：蓝灰底 + 浅蓝字。 */
    FROST_GLASS,
    /** 自定义：bg/fg/border/border 粗细全由用户设置。 */
    CUSTOM
}
