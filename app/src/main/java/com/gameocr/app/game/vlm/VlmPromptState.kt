package com.gameocr.app.game.vlm

import com.gameocr.app.game.core.GameState

/**
 * 纯提示词模式的牌局状态。
 *
 * 这类模式没有本地规则引擎：VLM 眼睛看到什么，就原样记下来，
 * 决策时把这段描述直接交给文本 LLM，模式之间的区别只在提示词。
 */
data class VlmPromptState(
    override val moduleId: String,
    /** VLM 按该模式 eyes 提示词输出的牌局描述原文。 */
    val boardText: String,
) : GameState {

    override fun toPromptText(): String = boardText.trim()

    override fun summary(): String =
        boardText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.take(SUMMARY_MAX_LENGTH)
            ?: "已识别牌局"

    private companion object {
        const val SUMMARY_MAX_LENGTH = 60
    }
}
