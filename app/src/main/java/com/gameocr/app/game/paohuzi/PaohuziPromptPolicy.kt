package com.gameocr.app.game.paohuzi

import com.gameocr.app.game.core.AdviceContext
import com.gameocr.app.game.core.GameState
import com.gameocr.app.game.core.PromptPolicy

/**
 * 跑胡子的提示词。
 *
 * 结构固定为：角色与规则（system）+ 牌局与本地算出的合法动作（user）。
 * 合法动作由 [PaohuziRules] 计算，模型只负责在合法范围里挑一个并给理由，
 * 这样即使模型对地方规则不熟也不会给出规则上不成立的建议。
 */
class PaohuziPromptPolicy(
    override val rulesSummary: String,
    private val rules: PaohuziRules = PaohuziRules(),
) : PromptPolicy {

    override fun systemPrompt(): String = buildString {
        appendLine("你是一名字牌（跑胡子）陪练助手。你的唯一任务是依据用户消息里的牌局信息，给出下一步怎么打。")
        appendLine("你只能看到文字描述的牌局，看不到画面；如果信息不足以判断，请在理由里说明缺什么，不要编造没给出的牌。")
        appendLine("只从「本地规则引擎已确认的合法动作」里选，不要给出规则上不成立的动作。")
        appendLine()
        appendLine("【规则要点】")
        appendLine(rulesSummary.trim())
        appendLine()
        appendLine("【输出格式】")
        appendLine("只输出一个 JSON 对象，不要输出解释性文字，也不要包在 Markdown 代码块里：")
        appendLine(OUTPUT_CONTRACT)
    }

    override fun userPrompt(state: GameState, context: AdviceContext): String = buildString {
        appendLine(state.toPromptText().trimEnd())
        val legalMoves = legalMoveLines(state)
        if (legalMoves.isNotEmpty()) {
            appendLine()
            appendLine("【本地规则引擎已确认的合法动作】")
            legalMoves.forEach { appendLine("- $it") }
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
        append("请按约定格式给出建议。")
    }

    private fun legalMoveLines(state: GameState): List<String> {
        if (state !is PaohuziState) return emptyList()
        val lines = mutableListOf<String>()
        val incoming = state.incomingTile

        if (incoming == null) {
            if (rules.canWin(state.hand)) {
                lines += "当前手牌已经成胡牌牌型"
            }
            rules.discardOptions(state.hand).forEach { candidate ->
                val rest = state.hand.toMutableList().also { it.remove(candidate) }
                val waits = rules.waitingTiles(rest)
                lines += if (waits.isEmpty()) {
                    "打「${candidate.display}」"
                } else {
                    "打「${candidate.display}」，之后进 " +
                        PaohuziTileCodec.formatSpaced(waits) +
                        " 可胡"
                }
            }
        } else {
            if (rules.canWin(state.hand + incoming)) {
                lines += "胡：进「${incoming.display}」"
            }
            if (rules.canKong(state.hand, incoming)) {
                lines += "提：用手上三张「${incoming.display}」提"
            }
            if (rules.canPung(state.hand, incoming)) {
                lines += "碰：用手上两张「${incoming.display}」碰"
            }
            rules.chowOptions(state.hand, incoming).forEach { pair ->
                lines += "吃：用 ${PaohuziTileCodec.formatSpaced(pair)} 吃「${incoming.display}」"
            }
            lines += "过：不要这张牌"
        }
        return lines.take(MAX_LEGAL_MOVES)
    }

    private companion object {
        const val MAX_HISTORY_LINES = 5
        const val MAX_LEGAL_MOVES = 24

        val OUTPUT_CONTRACT = """
            {"action":"play|chow|pung|kong|win|pass","tile":"要打出的牌，或要吃/碰/提的目标牌；pass 时可留空","reason":"一句话理由，40 字以内","alternatives":["备选打法，最多 2 条"]}
        """.trimIndent()
    }
}
