package com.gameocr.app.game.doudizhu

import com.gameocr.app.game.core.AdviceContext
import com.gameocr.app.game.core.GameState
import com.gameocr.app.game.core.PromptPolicy

/**
 * 斗地主的提示词。
 *
 * 结构与跑胡子一致：system 是角色 + 规则 + 输出契约，user 是牌局 + 本地算出的合法动作。
 * 模型只负责在合法动作里挑一个并给理由，避免它用错牌型或点错张数。
 */
class DoudizhuPromptPolicy(
    override val rulesSummary: String,
    private val advisorSystemPrompt: String,
    private val rules: DoudizhuRules = DoudizhuRules(),
) : PromptPolicy {

    override fun systemPrompt(): String = buildString {
        appendLine(advisorSystemPrompt.trim())
        appendLine()
        appendLine("【合法性约束】")
        appendLine("- 只能从用户消息里「本地规则引擎已确认的合法动作」中挑一个，tile 必须原样照抄该条里的牌。")
        appendLine("- 不允许自创牌型，也不允许使用手牌里没有的牌。")
        appendLine("- 要不起、或信息不足时 action 用 \"pass\"，并在 reason 里说明原因。")
        appendLine("- 如果牌局信息里有「识别校验」提示，请在 reason 里提醒用户核对相应牌面。")
    }

    override fun userPrompt(state: GameState, context: AdviceContext): String = buildString {
        appendLine(state.toPromptText().trimEnd())
        if (state is DoudizhuState) {
            appendLine()
            appendLine("【本地规则引擎已确认的合法动作】")
            legalMoves(state).forEach { appendLine("- $it") }
        }
        if (context.extraInstructions.isNotBlank()) {
            appendLine()
            appendLine("【用户额外要求】")
            appendLine(context.extraInstructions.trim())
        }
        if (context.history.isNotEmpty()) {
            appendLine()
            appendLine("【本局已给出的建议】")
            context.history.takeLast(MAX_HISTORY_LINES).forEach { appendLine("- $it") }
        }
        appendLine()
        append("请按约定格式给出建议，tile 必须是上面列出的某一条。")
    }

    /** 把本地算出的合法动作渲染成提示词里的条目。 */
    private fun legalMoves(state: DoudizhuState): List<String> {
        val lastPlay = state.lastPlay
        if (state.mustBeat && lastPlay?.pattern == null) {
            return listOf("上一手牌型无法确认：只能选择不出（pass），或提示用户重新识别")
        }
        val target = lastPlay?.takeIf { state.mustBeat }?.let { play ->
            play.pattern?.let { DoudizhuMove(play.cards, it) }
        }
        val moves = rules.legalMoves(state.hand, target).map { it.display }
        if (moves.isEmpty()) {
            val reason = if (state.mustBeat) "要不起：没有能压过上一手的牌" else "没有可出的牌"
            return listOf("$reason，只能不出（pass）")
        }
        return moves
    }

    private companion object {
        const val MAX_HISTORY_LINES = 5
    }
}
