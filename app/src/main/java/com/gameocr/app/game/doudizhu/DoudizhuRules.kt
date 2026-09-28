package com.gameocr.app.game.doudizhu

/** 一手候选出牌。 */
data class DoudizhuMove(
    val cards: List<DoudizhuCard>,
    val pattern: DoudizhuPattern,
) {
    /** 写进提示词的写法，例如「三带一：8 8 8 3」。 */
    val display: String
        get() = "${pattern.type.label}：" + DoudizhuCardCodec.format(cards)
}

/**
 * 斗地主合法动作计算。
 *
 * 纯函数、不依赖 Android，方便单测。职责是「算出这手牌现在能出什么」，
 * 不负责策略（选哪手交给模型 / 用户）。
 *
 * 翅膀（三带一、飞机带单等）只生成「最小可用的一组」代表，
 * 避免组合爆炸把提示词塞满——这类选择对牌型合法性和大小判定没有影响。
 */
class DoudizhuRules {

    /**
     * 当前手牌能出的动作。
     *
     * [target] 为 null 表示自由出牌（首家 / 上两家都过），否则只返回能压过它的大小。
     */
    fun legalMoves(hand: List<DoudizhuCard>, target: DoudizhuMove?): List<DoudizhuMove> {
        if (hand.isEmpty()) return emptyList()
        val byRank = hand.groupBy { it.rank.order }.toSortedMap()
        val candidates = buildList {
            addAll(simpleMoves(byRank))
            addAll(tripleWithWingMoves(byRank))
            addAll(fourWithWingMoves(byRank))
            addAll(chainMoves(byRank))
            addAll(airplaneMoves(byRank))
        }

        return candidates
            .filter { DoudizhuPatterns.canBeat(it.pattern, target?.pattern) }
            .distinctBy { it.pattern to it.cards.map { card -> card.rank } }
            // 每个牌型最多留几条：手牌多时单张 / 对子能凑出几十条，直接截断会把顺子、
            // 飞机这类大牌型挤掉，模型就看不到它们了。按牌型分组后再限量。
            .groupBy { it.pattern.type }
            .entries
            .sortedBy { it.key.ordinal }
            .flatMap { (_, moves) ->
                moves.sortedWith(compareBy({ it.pattern.primary }, { it.cards.size }))
                    .take(MAX_MOVES_PER_TYPE)
            }
            .take(MAX_MOVES)
    }

    /** 某个出牌是否是当前局面下的合法动作（牌够、牌型成立、能压过上一手）。 */
    fun isLegal(hand: List<DoudizhuCard>, cards: List<DoudizhuCard>, target: DoudizhuMove?): Boolean {
        if (cards.isEmpty()) return false
        val handCounts = DoudizhuCardCodec.countsByRank(hand)
        val playCounts = DoudizhuCardCodec.countsByRank(cards)
        if (playCounts.any { (rank, count) -> count > (handCounts[rank] ?: 0) }) return false
        val pattern = DoudizhuPatterns.detect(cards) ?: return false
        return DoudizhuPatterns.canBeat(pattern, target?.pattern)
    }

    private fun simpleMoves(byRank: Map<Int, List<DoudizhuCard>>): List<DoudizhuMove> = buildList {
        byRank.forEach { (_, cards) ->
            addPlay(cards.take(1))
            if (cards.size >= 2) addPlay(cards.take(2))
            if (cards.size >= 3) addPlay(cards.take(3))
            if (cards.size >= 4) addPlay(cards.take(4)) // 炸弹
        }
        rocket(byRank)?.let { add(it) }
    }

    /** 三带一 / 三带二：每种三张配一组最小翅膀。 */
    private fun tripleWithWingMoves(byRank: Map<Int, List<DoudizhuCard>>): List<DoudizhuMove> = buildList {
        byRank.forEach { (rank, cards) ->
            if (cards.size < 3) return@forEach
            val triple = cards.take(3)
            pickSingleWings(byRank, exclude = setOf(rank), count = 1)?.let { wings ->
                addPlay(triple + wings)
            }
            pickPairWings(byRank, exclude = setOf(rank), count = 1)?.let { wings ->
                addPlay(triple + wings)
            }
        }
    }

    /** 四带二（两单）/ 四带两对。 */
    private fun fourWithWingMoves(byRank: Map<Int, List<DoudizhuCard>>): List<DoudizhuMove> = buildList {
        byRank.forEach { (rank, cards) ->
            if (cards.size != 4) return@forEach
            pickSingleWings(byRank, exclude = setOf(rank), count = 2)?.let { wings ->
                addPlay(cards + wings)
            }
            pickPairWings(byRank, exclude = setOf(rank), count = 2)?.let { wings ->
                addPlay(cards + wings)
            }
        }
    }

    /** 顺子与连对。 */
    private fun chainMoves(byRank: Map<Int, List<DoudizhuCard>>): List<DoudizhuMove> = buildList {
        val ranks = byRank.keys.filter { it in SEQUENCE_RANGE }
        // 顺子：起点 3..A，长度 5..12
        ranks.forEach { start ->
            for (length in MIN_STRAIGHT..MAX_STRAIGHT) {
                val run = (start until start + length).toList()
                if (run.any { it !in SEQUENCE_RANGE }) break
                if (run.any { (byRank[it]?.size ?: 0) < 1 }) break
                addPlay(run.flatMap { byRank.getValue(it).take(1) })
            }
        }
        // 连对：起点 3..A，长度 3..10 对
        ranks.forEach { start ->
            for (length in MIN_PAIR_CHAIN..MAX_PAIR_CHAIN) {
                val run = (start until start + length).toList()
                if (run.any { it !in SEQUENCE_RANGE }) break
                if (run.any { (byRank[it]?.size ?: 0) < 2 }) break
                addPlay(run.flatMap { byRank.getValue(it).take(2) })
            }
        }
    }

    /** 飞机（不带 / 带单 / 带对）。 */
    private fun airplaneMoves(byRank: Map<Int, List<DoudizhuCard>>): List<DoudizhuMove> = buildList {
        val tripleRanks = byRank.filterValues { it.size >= 3 }.keys.filter { it in SEQUENCE_RANGE }.sorted()
        for (start in tripleRanks.indices) {
            var end = start
            while (end + 1 < tripleRanks.size && tripleRanks[end + 1] == tripleRanks[end] + 1) end++
            for (length in MIN_AIRPLANE..(end - start + 1)) {
                val run = tripleRanks.subList(start, start + length)
                val body = run.flatMap { byRank.getValue(it).take(3) }
                addPlay(body)
                pickSingleWings(byRank, exclude = run.toSet(), count = length)?.let { addPlay(body + it) }
                pickPairWings(byRank, exclude = run.toSet(), count = length)?.let { addPlay(body + it) }
            }
        }
    }

    /** 大小王齐 = 火箭，压一切。 */
    private fun rocket(byRank: Map<Int, List<DoudizhuCard>>): DoudizhuMove? {
        val small = byRank[DoudizhuRank.SMALL_JOKER.order]?.firstOrNull() ?: return null
        val big = byRank[DoudizhuRank.BIG_JOKER.order]?.firstOrNull() ?: return null
        val cards = listOf(small, big)
        val pattern = DoudizhuPatterns.detect(cards) ?: return null
        return DoudizhuMove(cards, pattern)
    }

    private fun MutableList<DoudizhuMove>.addPlay(cards: List<DoudizhuCard>) {
        val pattern = DoudizhuPatterns.detect(cards) ?: return
        add(DoudizhuMove(cards, pattern))
    }

    /**
     * 取 [count] 张最小的单牌当翅膀。
     *
     * 优先用散牌（1 张的点数），其次对子、三张，尽量不拆炸弹；
     * 数量不够时返回 null，表示这种带法不成立。
     */
    private fun pickSingleWings(
        byRank: Map<Int, List<DoudizhuCard>>,
        exclude: Set<Int>,
        count: Int,
    ): List<DoudizhuCard>? {
        val ranked = byRank.filterKeys { it !in exclude }
            .entries
            .sortedWith(compareBy({ wingCost(it.value.size) }, { it.key }))
        val chosen = mutableListOf<DoudizhuCard>()
        for ((_, cards) in ranked) {
            for (card in cards) {
                if (chosen.size == count) break
                chosen += card
            }
            if (chosen.size == count) break
        }
        return if (chosen.size == count) chosen else null
    }

    /** 取 [count] 组最小的对子当翅膀；不拆三张与炸弹。 */
    private fun pickPairWings(
        byRank: Map<Int, List<DoudizhuCard>>,
        exclude: Set<Int>,
        count: Int,
    ): List<DoudizhuCard>? {
        val pairs = byRank.filterKeys { it !in exclude }
            .filterValues { it.size == 2 }
            .toSortedMap()
        if (pairs.size < count) return null
        return pairs.entries.take(count).flatMap { it.value }
    }

    /** 散牌最便宜，其次对子、三张；炸弹留到最后拆。 */
    private fun wingCost(size: Int): Int = when {
        size <= 1 -> 0
        size == 2 -> 1
        size == 3 -> 2
        else -> 3
    }

    private companion object {
        const val MAX_MOVES = 30
        const val MAX_MOVES_PER_TYPE = 6
        const val MIN_STRAIGHT = 5
        const val MAX_STRAIGHT = 12
        const val MIN_PAIR_CHAIN = 3
        const val MAX_PAIR_CHAIN = 10
        const val MIN_AIRPLANE = 2
        val SEQUENCE_RANGE = DoudizhuRank.THREE.order..DoudizhuRank.ACE.order
    }
}

