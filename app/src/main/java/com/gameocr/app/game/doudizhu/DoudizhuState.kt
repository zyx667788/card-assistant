package com.gameocr.app.game.doudizhu

import com.gameocr.app.game.core.GameState

/** 我的身份。 */
enum class DoudizhuRole(val label: String) {
    LANDLORD("地主"),
    FARMER("农民"),
    UNKNOWN("未知"),
}

/** 相对我自己的座位。 */
enum class DoudizhuSeat(val label: String) {
    ME("我"),
    UP("上家"),
    DOWN("下家"),
    UNKNOWN("未知"),
}

/** 桌面上最后一手牌。 */
data class DoudizhuLastPlay(
    val seat: DoudizhuSeat,
    val cards: List<DoudizhuCard>,
) {
    /** 识别不出合法牌型时为 null，提示词会据此让模型保守处理。 */
    val pattern: DoudizhuPattern? get() = DoudizhuPatterns.detect(cards)

    val display: String get() = DoudizhuCardCodec.format(cards)
}

/**
 * 一屏识别出来的斗地主牌局。
 *
 * [lastPlay] 为空或座位是「我」时表示我可以自由出牌；否则需要压过它或选择不出。
 */
data class DoudizhuState(
    val hand: List<DoudizhuCard>,
    val role: DoudizhuRole = DoudizhuRole.UNKNOWN,
    val bottomCards: List<DoudizhuCard> = emptyList(),
    val lastPlay: DoudizhuLastPlay? = null,
    val playedCards: List<DoudizhuCard> = emptyList(),
    val myRemaining: Int? = null,
    val upRemaining: Int? = null,
    val downRemaining: Int? = null,
    val actionHint: String? = null,
    val visualContext: String? = null,
    val validation: DoudizhuValidation = DoudizhuValidation.OK,
    override val moduleId: String = DOUDIZHU_MODULE_ID,
) : GameState {

    /** 上一手是别人出的 → 我必须压过或不出。 */
    val mustBeat: Boolean get() = lastPlay != null && lastPlay.seat != DoudizhuSeat.ME

    override fun toPromptText(): String = buildString {
        appendLine(
            "【我的手牌】" + DoudizhuCardCodec.format(hand.sortedForDisplay()) +
                "（共 ${hand.size} 张）",
        )
        appendLine("【我的身份】${role.label}")
        if (bottomCards.isNotEmpty()) {
            appendLine("【地主底牌】" + DoudizhuCardCodec.format(bottomCards.sortedForDisplay()))
        }
        appendLine(
            when {
                lastPlay == null -> "【上一手】无，我可以自由出牌"
                lastPlay.seat == DoudizhuSeat.ME -> "【上一手】我出的 ${lastPlay.display}"
                else -> "【上一手】${lastPlay.seat.label}出的 ${lastPlay.display}，我需要压过或不出"
            },
        )
        if (playedCards.isNotEmpty()) {
            appendLine(
                "【桌面已出】" + DoudizhuCardCodec.format(playedCards.sortedForDisplay()) +
                    "（共 ${playedCards.size} 张）",
            )
        }
        remainingLine()?.let { appendLine(it) }
        actionHint?.takeIf { it.isNotBlank() }?.let { appendLine("【画面提示】$it") }
        visualContext?.takeIf { it.isNotBlank() }?.let { appendLine("【全局观察】$it") }
        if (validation.hasIssues) {
            appendLine("【识别校验】" + validation.summary())
        }
    }

    override fun summary(): String {
        val turn = when {
            !mustBeat -> "轮到我自由出牌"
            lastPlay?.pattern == null -> "需压上一手（牌型未确认）"
            else -> "需压 ${lastPlay.display}"
        }
        return "手牌 ${hand.size} 张；$turn"
    }

    private fun remainingLine(): String? {
        val parts = buildList {
            myRemaining?.let { add("我剩 $it 张") }
            upRemaining?.let { add("上家剩 $it 张") }
            downRemaining?.let { add("下家剩 $it 张") }
        }
        return if (parts.isEmpty()) null else "【剩余手牌】" + parts.joinToString("，")
    }
}
