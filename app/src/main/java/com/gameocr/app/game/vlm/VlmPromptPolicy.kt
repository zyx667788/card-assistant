package com.gameocr.app.game.vlm

import com.gameocr.app.game.core.AdviceContext
import com.gameocr.app.game.core.GameState
import com.gameocr.app.game.core.PromptPolicy

/**
* 纯提示词模式的决策提示词。
*
* [advisorSystemPrompt] 是该模式完整的 system 提示词（含规则、策略与输出格式约定），
* 由 DI 绑定从 assets 读取后传入；user 消息直接拼 VLM 眼睛看到的牌局描述。
* 这类模式没有本地规则引擎算合法动作，决策完全依赖模型的牌局理解。
*/
class VlmPromptPolicy(
override val rulesSummary: String,
private val advisorSystemPrompt: String,
): PromptPolicy {

override fun systemPrompt(): String = advisorSystemPrompt

override fun userPrompt(state: GameState, context: AdviceContext): String = buildString {
appendLine("")
appendLine(state.toPromptText().trim())
if (context.extraInstructions.isNotBlank()) {
appendLine()
appendLine("")
appendLine(context.extraInstructions.trim())
}
if (context.history.isNotEmpty()) {
appendLine()
appendLine("")
context.history.takeLast(MAX_HISTORY_LINES).forEach { appendLine("- $it")}
}
appendLine()
append("请按约定格式给出建议。")
}

private companion object {
const val MAX_HISTORY_LINES = 5
}
}
