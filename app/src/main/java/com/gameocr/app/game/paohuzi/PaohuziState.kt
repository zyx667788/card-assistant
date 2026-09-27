package com.gameocr.app.game.paohuzi

import com.gameocr.app.game.core.GameState

/** 跑胡子模块的注册 id。 */
const val PAOHUZI_MODULE_ID = "paohuzi"

/** 对手的可见信息。 */
data class PaohuziOpponentView(
    val seatLabel: String,
    val melds: List<PaohuziMeld> = emptyList(),
    val discardCount: Int? = null,
)

/**
 * 一屏识别出来的跑胡子牌局。
 *
 * [incomingTile] 为空表示轮到我出牌；不为空表示别人打出了这张牌、等我要不要吃/碰/提/胡。
 */
data class PaohuziState(
    val hand: List<PaohuziTile>,
    val melds: List<PaohuziMeld> = emptyList(),
    val incomingTile: PaohuziTile? = null,
    val tableDiscards: List<PaohuziTile> = emptyList(),
    val opponents: List<PaohuziOpponentView> = emptyList(),
    val remainingTileCount: Int? = null,
    val actionHint: String? = null,
    val notes: List<String> = emptyList(),
    override val moduleId: String = PAOHUZI_MODULE_ID,
) : GameState {

    override fun toPromptText(): String = buildString {
        appendLine(
            "【我的手牌】" +
                PaohuziTileCodec.formatSpaced(hand.sortedForDisplay()) +
                "（共 ${hand.size} 张）",
        )
        if (melds.isNotEmpty()) {
            appendLine("【我已亮出】" + melds.joinToString("；") { it.display })
        }
        appendLine(
            if (incomingTile == null) {
                "【待我决定】轮到我出牌"
            } else {
                "【待我决定】别人打出「${incomingTile.display}」"
            },
        )
        if (tableDiscards.isNotEmpty()) {
            appendLine(
                "【桌面已出】" +
                    PaohuziTileCodec.formatSpaced(tableDiscards.sortedForDisplay()) +
                    "（共 ${tableDiscards.size} 张）",
            )
        }
        opponents.forEach { opponent ->
            val meldText = if (opponent.melds.isEmpty()) {
                "无"
            } else {
                opponent.melds.joinToString("；") { it.display }
            }
            val discardText = opponent.discardCount?.let { "，已出 $it 张" }.orEmpty()
            appendLine("【${opponent.seatLabel}】亮牌：$meldText$discardText")
        }
        remainingTileCount?.let { appendLine("【剩余未摸】$it 张") }
        actionHint?.takeIf { it.isNotBlank() }?.let { appendLine("【画面提示】$it") }
        if (notes.isNotEmpty()) {
            appendLine("【识别备注】" + notes.joinToString("；"))
        }
    }

    override fun summary(): String {
        val decision = incomingTile?.let { "待决策：${it.display}" } ?: "轮到我出牌"
        return "手牌 ${hand.size} 张；$decision"
    }
}
