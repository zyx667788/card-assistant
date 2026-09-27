package com.gameocr.app.game.paohuzi

/**
 * VLM 返回的牌局 JSON -> [PaohuziState]。
 *
 * 纯 Kotlin，不依赖 Android，方便单测。
 * 组合类型不信任模型的字面描述，一律用 [PaohuziMeldInference] 从牌重新推断，
 * 避免「四张被说成两个对子」之类的口径漂移。
 */
object VlmBoardParser {

    fun parse(payload: VlmBoardPayload): PaohuziState? {
        val hand = payload.hand.flatMap { PaohuziTileCodec.parse(it) }
        if (hand.size < MIN_PLAUSIBLE_HAND) return null

        val melds = payload.selfMelds.flatMap { group ->
            PaohuziMeldInference.infer(group.flatMap { PaohuziTileCodec.parse(it) })
        }
        val tableDiscards = payload.tableDiscards.flatMap { PaohuziTileCodec.parse(it) }
        val opponents = listOf(
            payload.opponentLeftMelds to "左家",
            payload.opponentRightMelds to "右家",
        ).mapNotNull { (groups, label) ->
            val meldsOf = groups.flatMap { group ->
                PaohuziMeldInference.infer(group.flatMap { PaohuziTileCodec.parse(it) })
            }
            if (meldsOf.isEmpty()) null else PaohuziOpponentView(label, meldsOf)
        }

        val notes = buildList {
            if (hand.size > MAX_PLAUSIBLE_HAND) {
                add("手牌识别到 ${hand.size} 张，可能有多认的牌，请核对")
            }
        }

        return PaohuziState(
            hand = hand,
            melds = melds,
            incomingTile = payload.incomingTile
                ?.let { PaohuziTileCodec.parse(it).distinct().singleOrNull() },
            tableDiscards = tableDiscards,
            opponents = opponents,
            remainingTileCount = payload.remainingCount,
            actionHint = payload.actionHint?.takeIf { it.isNotBlank() },
            visualContext = payload.globalObservation?.takeIf { it.isNotBlank() },
            notes = notes,
        )
    }

    /** 少于两张不可能构成任何牌局，直接判定识别失败。 */
    const val MIN_PLAUSIBLE_HAND = 2

    /** 远超庄家 21 张说明混入了别的内容。 */
    const val MAX_PLAUSIBLE_HAND = 21
}
