package com.gameocr.app.game.core

/** 单个牌类模块的决策上下文。 */
data class AdviceContext(
    /** 用户在设置里追加的额外要求（例如「优先留大字」）。 */
    val extraInstructions: String = "",
    /** 本局已经给过的建议，供模型保持前后一致。 */
    val history: List<String> = emptyList(),
)

/** 由 OCR 文本还原牌局状态。信息不足时返回 null，由调用方提示用户修正。 */
interface BoardRecognizer {
    fun recognize(
        spans: List<TileTextSpan>,
        zones: List<BoardZone>,
        imageWidth: Int,
        imageHeight: Int,
    ): GameState?
}

/** 把牌局状态渲染成模型可读的提示词。 */
interface PromptPolicy {
    /** 规则说明，会写进 system 消息，同时在设置页作为说明展示。 */
    val rulesSummary: String

    fun systemPrompt(): String

    fun userPrompt(state: GameState, context: AdviceContext): String
}

/**
 * 一个牌类模块（跑胡子、斗地主、麻将……）。
 *
 * 模块自带区域布局、识别器和提示词，新增玩法只需要再实现一个 [GameModule] 并注册进
 * [GameModuleRegistry]，不需要改动决策链路。
 */
interface GameModule {
    val id: String
    val displayName: String

    /** 首次使用时的默认区域布局；用户在手机上标定后会覆盖坐标。 */
    val defaultZones: List<BoardZone>

    val recognizer: BoardRecognizer
    val promptPolicy: PromptPolicy
}
