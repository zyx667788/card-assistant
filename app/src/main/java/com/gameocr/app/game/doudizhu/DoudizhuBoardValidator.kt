package com.gameocr.app.game.doudizhu

/**
 * 识别结果的本地校验。
 *
 * [errors] 是「牌本身自相矛盾」的硬错误（同一张牌出现两次、某点数超过 4 张、
 * 手牌与已出牌重合），这类结果不能拿来决策；[warnings] 只是提示，仍然可以出建议。
 */
data class DoudizhuValidation(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val isFatal: Boolean get() = errors.isNotEmpty()

    val hasIssues: Boolean get() = errors.isNotEmpty() || warnings.isNotEmpty()

    /** 拼进识别结果文本 / 提示词的一行摘要。 */
    fun summary(): String = buildString {
        if (errors.isNotEmpty()) {
            append("⛔ 校验未通过：")
            append(errors.joinToString("；"))
        }
        if (warnings.isNotEmpty()) {
            if (errors.isNotEmpty()) append("　")
            append("⚠ ")
            append(warnings.joinToString("；"))
        }
    }

    companion object {
        val OK = DoudizhuValidation()
    }
}

/**
 * 用 54 张牌的硬约束复核 VLM 的识别结果。
 *
 * 一副牌里每个点数最多 4 张、每张具体牌只有一张，所以「同一张牌出现两次」或
 * 「某个点数超过 4 张」一定说明模型数错了、重复了或把别的东西当成了牌。
 * 花色是这里的关键证据：模型必须逐张报花色，4 个 7 只能是 7♠ 7♥ 7♣ 7♦。
 */
object DoudizhuBoardValidator {

    const val LANDLORD_HAND_SIZE = 20
    const val FARMER_HAND_SIZE = 17
    const val MAX_HAND_SIZE = 20
    const val BOTTOM_CARD_SIZE = 3

    fun validate(
        hand: List<DoudizhuCard>,
        playedCards: List<DoudizhuCard> = emptyList(),
        bottomCards: List<DoudizhuCard> = emptyList(),
        role: DoudizhuRole = DoudizhuRole.UNKNOWN,
    ): DoudizhuValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (hand.isEmpty()) {
            return DoudizhuValidation(errors = listOf("没有识别到任何手牌"))
        }

        // 1) 同一个点数不能超过 4 张（大小王各 1 张），手牌 + 已出牌一起算
        val combined = hand + playedCards
        DoudizhuCardCodec.countsByRank(combined)
            .filter { (rank, count) -> count > maxCopies(rank) }
            .toSortedMap()
            .forEach { (rank, count) ->
                val label = DoudizhuRank.entries.first { it.order == rank }
                errors += "「${label.label}」共出现 $count 张，超过一副牌的上限 ${maxCopies(rank)} 张"
            }

        // 2) 具体到花色的重复：同一张牌不可能出现两次
        val known = combined.filter { it.suit != DoudizhuSuit.UNKNOWN && !it.isJoker }
        known.groupingBy { it.rank to it.suit }
            .eachCount()
            .filterValues { it > 1 }
            .forEach { (key, count) ->
                val (rank, suit) = key
                errors += "${suit.label}${rank.label} 出现 $count 次，同一张牌重复"
            }

        // 3) 手牌与已出牌不能是同一张牌（说明其中一边认错了）
        val handKeys = hand.filter { it.suit != DoudizhuSuit.UNKNOWN && !it.isJoker }
            .map { it.rank to it.suit }
            .toSet()
        playedCards.filter { it.suit != DoudizhuSuit.UNKNOWN && !it.isJoker }
            .map { it.rank to it.suit }
            .filter { it in handKeys }
            .distinct()
            .forEach { (rank, suit) ->
                errors += "${suit.label}${rank.label} 既在手牌里又被当成已出牌"
            }

        // 4) 手牌张数：地主 20 张、农民 17 张
        if (hand.size > MAX_HAND_SIZE) {
            errors += "手牌识别到 ${hand.size} 张，超过一副牌里单人可能的上限 $MAX_HAND_SIZE 张"
        }
        when (role) {
            DoudizhuRole.LANDLORD -> if (hand.size != LANDLORD_HAND_SIZE) {
                warnings += "地主应有 $LANDLORD_HAND_SIZE 张手牌，识别到 ${hand.size} 张"
            }
            DoudizhuRole.FARMER -> if (hand.size != FARMER_HAND_SIZE) {
                warnings += "农民应有 $FARMER_HAND_SIZE 张手牌，识别到 ${hand.size} 张"
            }
            DoudizhuRole.UNKNOWN -> warnings += "没识别出身份（地主 / 农民）"
        }

        // 5) 没给花色时无法核对重复，只能提示
        val unknownSuits = hand.count { it.suit == DoudizhuSuit.UNKNOWN && !it.isJoker }
        if (unknownSuits > 0) {
            warnings += "有 $unknownSuits 张没识别出花色，无法核对是否重复"
        }

        // 6) 底牌
        if (bottomCards.isNotEmpty() && bottomCards.size != BOTTOM_CARD_SIZE) {
            warnings += "地主底牌应有 $BOTTOM_CARD_SIZE 张，识别到 ${bottomCards.size} 张"
        }

        return DoudizhuValidation(errors = errors, warnings = warnings)
    }

    private fun maxCopies(rank: Int): Int =
        DoudizhuRank.entries.first { it.order == rank }.let(DoudizhuRank::maxCopies)
}
