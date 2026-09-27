package com.gameocr.app.game.core

/** 某一时刻从画面识别出的牌局状态。 */
interface GameState {
    val moduleId: String

    /** 结构化文本，直接拼进 LLM 的用户消息。实现必须是确定性的，便于快照测试。 */
    fun toPromptText(): String

    /** 悬浮卡片 / 日志用的一行摘要。 */
    fun summary(): String
}
