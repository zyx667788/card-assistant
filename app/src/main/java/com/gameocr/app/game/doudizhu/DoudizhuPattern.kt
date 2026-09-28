package com.gameocr.app.game.doudizhu

/** 斗地主牌型。 */
enum class DoudizhuPatternType(val label: String) {
    SINGLE("单张"),
    PAIR("对子"),
    TRIPLE("三张"),
    TRIPLE_SINGLE("三带一"),
    TRIPLE_PAIR("三带二"),
    STRAIGHT("顺子"),
    CONSECUTIVE_PAIRS("连对"),
    AIRPLANE("飞机"),
    AIRPLANE_SINGLE("飞机带单"),
    AIRPLANE_PAIR("飞机带对"),
    FOUR_TWO_SINGLE("四带二"),
    FOUR_TWO_PAIR("四带两对"),
    BOMB("炸弹"),
    ROCKET("火箭"),
}

/**
 * 一手牌的牌型。
 *
 * [primary] 是比大小用的主牌点数（顺子/连对/飞机取最大那张，三带取三张的点数，四带取四张的点数）。
 * [length] 是连牌长度（顺子的张数、连对的对数、飞机的三张组数），单张/对子等固定为 1。
 */
data class DoudizhuPattern(
    val type: DoudizhuPatternType,
    val primary: Int,
    val length: Int = 1,
)

/**
 * 牌型识别。
 *
 * 只认标准规则：顺子 ≥5 张、连对 ≥3 对、飞机 ≥2 组三张且可带等量单/对，
 * 连牌限定 3..A（2 与大小王不能进连牌）。
 */
object DoudizhuPatterns {

    /** 识别一组牌的牌型；不是合法牌型返回 null。 */
    fun detect(cards: List<DoudizhuCard>): DoudizhuPattern? {
        if (cards.isEmpty()) return null
        val counts = DoudizhuCardCodec.countsByRank(cards)
        val size = cards.size
        val distinct = counts.size
        val sortedCounts = counts.values.sortedDescending()

        // 火箭：只有一对大小王
        if (size == 2 && counts.keys.all { it >= DoudizhuRank.SMALL_JOKER.order }) {
            return DoudizhuPattern(DoudizhuPatternType.ROCKET, DoudizhuRank.BIG_JOKER.order)
        }

        if (distinct == 1) {
            val rank = counts.keys.first()
            val type = when (size) {
                1 -> DoudizhuPatternType.SINGLE
                2 -> DoudizhuPatternType.PAIR
                3 -> DoudizhuPatternType.TRIPLE
                4 -> DoudizhuPatternType.BOMB
                else -> null
            } ?: return null
            return DoudizhuPattern(type, rank)
        }

        // 三带一 / 三带二
        if (size == 4 && sortedCounts == listOf(3, 1)) {
            return DoudizhuPattern(DoudizhuPatternType.TRIPLE_SINGLE, tripleRank(counts) ?: return null)
        }
        if (size == 5 && sortedCounts == listOf(3, 2)) {
            return DoudizhuPattern(DoudizhuPatternType.TRIPLE_PAIR, tripleRank(counts) ?: return null)
        }

        // 四带二 / 四带两对
        if (size == 6 && sortedCounts == listOf(4, 1, 1)) {
            val quad = counts.entries.first { it.value == 4 }.key
            return DoudizhuPattern(DoudizhuPatternType.FOUR_TWO_SINGLE, quad)
        }
        if (size == 8 && sortedCounts.first() == 4 && sortedCounts.drop(1).all { it == 2 }) {
            val quad = counts.entries.first { it.value == 4 }.key
            return DoudizhuPattern(DoudizhuPatternType.FOUR_TWO_PAIR, quad)
        }

        // 顺子：≥5 张、点数连续且都在 3..A
        if (size >= 5 && distinct == size && isSequence(counts.keys)) {
            return DoudizhuPattern(
                type = DoudizhuPatternType.STRAIGHT,
                primary = counts.keys.max(),
                length = size,
            )
        }

        // 连对：≥3 对、点数连续且都在 3..A
        if (size >= 6 && size % 2 == 0 && sortedCounts.all { it == 2 } &&
            distinct >= MIN_PAIR_CHAIN && isSequence(counts.keys)
        ) {
            return DoudizhuPattern(
                type = DoudizhuPatternType.CONSECUTIVE_PAIRS,
                primary = counts.keys.max(),
                length = distinct,
            )
        }

        return detectAirplane(counts, size)
    }

    /** 候选牌型能否压过 [target]；[target] 为 null 表示自由出牌。 */
    fun canBeat(candidate: DoudizhuPattern, target: DoudizhuPattern?): Boolean {
        if (target == null) return true
        if (candidate.type == DoudizhuPatternType.ROCKET) return true
        if (target.type == DoudizhuPatternType.ROCKET) return false
        if (candidate.type == DoudizhuPatternType.BOMB && target.type != DoudizhuPatternType.BOMB) return true
        if (target.type == DoudizhuPatternType.BOMB && candidate.type != DoudizhuPatternType.BOMB) return false
        if (candidate.type != target.type) return false
        if (candidate.length != target.length) return false
        return candidate.primary > target.primary
    }

    private fun tripleRank(counts: Map<Int, Int>): Int? =
        counts.entries.firstOrNull { it.value == 3 }?.key

    private fun isSequence(ranks: Set<Int>): Boolean {
        if (ranks.any { it !in SEQUENCE_RANGE }) return false
        val sorted = ranks.sorted()
        return sorted.zipWithNext().all { (a, b) -> b == a + 1 }
    }

    /**
     * 飞机：k 组连续三张（k≥2），可以不带、带 k 张单牌、或带 k 对。
     * 翅膀里不允许再出现三张（不能把另一组三张当单牌用）。
     */
    private fun detectAirplane(counts: Map<Int, Int>, size: Int): DoudizhuPattern? {
        val tripleRanks = counts
            .filterValues { it == TRIPLE_SIZE }
            .keys
            .filter { it in SEQUENCE_RANGE }
            .sorted()
        if (tripleRanks.size < MIN_AIRPLANE) return null

        // 找出所有连续的三张段，从最长开始尝试，保证 333444555 这类先按最长飞机算。
        for (start in tripleRanks.indices) {
            var end = start
            while (end + 1 < tripleRanks.size && tripleRanks[end + 1] == tripleRanks[end] + 1) end++
            for (length in (end - start + 1) downTo MIN_AIRPLANE) {
                val run = tripleRanks.subList(start, start + length).toSet()
                val wings = counts.filterKeys { it !in run }
                val wingCount = wings.values.sum()

                if (wingCount == 0 && size == length * TRIPLE_SIZE) {
                    return DoudizhuPattern(
                        DoudizhuPatternType.AIRPLANE,
                        primary = run.max(),
                        length = length,
                    )
                }
                if (wingCount == length && size == length * (TRIPLE_SIZE + 1) && wings.values.all { it <= 2 }) {
                    return DoudizhuPattern(
                        DoudizhuPatternType.AIRPLANE_SINGLE,
                        primary = run.max(),
                        length = length,
                    )
                }
                if (wingCount == length * 2 && size == length * (TRIPLE_SIZE + 2) && wings.values.all { it == 2 }) {
                    return DoudizhuPattern(
                        DoudizhuPatternType.AIRPLANE_PAIR,
                        primary = run.max(),
                        length = length,
                    )
                }
            }
        }
        return null
    }

    private const val TRIPLE_SIZE = 3
    private const val MIN_PAIR_CHAIN = 3
    private const val MIN_AIRPLANE = 2
    private val SEQUENCE_RANGE = DoudizhuRank.THREE.order..DoudizhuRank.ACE.order
}
