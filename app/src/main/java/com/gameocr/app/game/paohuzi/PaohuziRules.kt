package com.gameocr.app.game.paohuzi

/**
 * 跑胡子规则配置。
 *
 * 字牌在不同地区的规则差异很大（起胡息数、吃/碰/提是否允许、是否必须有将……），
 * 凡是会随地区变化的点都做成显式配置。默认值只保证「牌型判定能跑通」，
 * 不替用户决定当地玩法，具体口径以 assets 里的规则说明和用户设置为主。
 */
data class PaohuziRuleConfig(
    /** 胡牌是否必须有一对将牌。 */
    val requiresPair: Boolean = true,

    /** 是否允许顺子（吃）。 */
    val allowChow: Boolean = true,

    /** 手上 4 张相同是否可以直接作为「提」计入胡牌牌型。 */
    val allowKongInHand: Boolean = true,
)

/**
 * 跑胡子牌型判定与可选动作计算。
 *
 * 纯函数实现，不依赖 Android，便于单元测试；也只负责「结构是否成立」，
 * 不把地区性的息数/番数规则硬编码进来。
 */
class PaohuziRules(private val config: PaohuziRuleConfig = PaohuziRuleConfig()) {

    /** 手牌（不含已亮出的组合）能否组成「若干顺子/坎/提 + 一对将」。 */
    fun canWin(hand: List<PaohuziTile>): Boolean = canWinCounts(hand.toCounts())

    private fun canWinCounts(counts: IntArray): Boolean {
        if (counts.sum() == 0) return false
        if (!config.requiresPair) return canDecompose(counts)

        for (index in counts.indices) {
            if (counts[index] < 2) continue
            counts[index] -= 2
            val restDecomposes = canDecompose(counts)
            counts[index] += 2
            if (restDecomposes) return true
        }
        return false
    }

    /** 把手上的牌全部拆成顺子/坎/提。会临时修改 [counts]，每条路径都会复原。 */
    private fun canDecompose(counts: IntArray): Boolean {
        var index = 0
        while (index < counts.size && counts[index] == 0) index++
        if (index == counts.size) return true

        if (counts[index] >= 3) {
            counts[index] -= 3
            val ok = canDecompose(counts)
            counts[index] += 3
            if (ok) return true
        }

        if (config.allowKongInHand && counts[index] >= 4) {
            counts[index] -= 4
            val ok = canDecompose(counts)
            counts[index] += 4
            if (ok) return true
        }

        if (config.allowChow && canStartRunAt(counts, index)) {
            counts[index]--
            counts[index + 1]--
            counts[index + 2]--
            val ok = canDecompose(counts)
            counts[index]++
            counts[index + 1]++
            counts[index + 2]++
            if (ok) return true
        }

        return false
    }

    /** 顺子只在同一门内成立，且起点点数最多到 8。 */
    private fun canStartRunAt(counts: IntArray, index: Int): Boolean {
        val rankOffset = index % PaohuziTile.RANKS_PER_CASE
        if (rankOffset > PaohuziTile.RANKS_PER_CASE - 3) return false
        return counts[index] > 0 && counts[index + 1] > 0 && counts[index + 2] > 0
    }

    /** 进哪些牌可以让这手牌成胡。用于提示词里的「听牌」信息。 */
    fun waitingTiles(hand: List<PaohuziTile>): List<PaohuziTile> {
        val counts = hand.toCounts()
        return PaohuziTile.ALL.filter { candidate ->
            val index = candidate.indexOf()
            if (counts[index] >= PaohuziTile.COPIES_PER_TILE) return@filter false
            counts[index]++
            val wins = canWinCounts(counts)
            counts[index]--
            wins
        }
    }

    /** 别人打出 [tile] 时我能不能吃，返回需要用手上哪两张来搭。 */
    fun chowOptions(hand: List<PaohuziTile>, tile: PaohuziTile): List<List<PaohuziTile>> {
        if (!config.allowChow) return emptyList()
        val handCounts = hand.toCounts()
        val options = mutableListOf<List<PaohuziTile>>()
        for (offset in 0..2) {
            val startRank = tile.rank - offset
            if (startRank < PaohuziTile.MIN_RANK) continue
            if (startRank + 2 > PaohuziTile.MAX_RANK) continue
            val window = (0..2).map { PaohuziTile(startRank + it, tile.case) }
            val needed = window.filter { it != tile }
            if (needed.size != 2) continue
            val neededCounts = needed.toCounts()
            val handHasBoth = neededCounts.indices.all { neededCounts[it] <= handCounts[it] }
            if (handHasBoth) options += needed
        }
        return options.distinct()
    }

    /** 手上两张 + 别人打的一张 = 坎。 */
    fun canPung(hand: List<PaohuziTile>, tile: PaohuziTile): Boolean =
        hand.count { it == tile } >= 2

    /** 手上三张 + 别人打的一张 = 提（部分地区叫开招）。 */
    fun canKong(hand: List<PaohuziTile>, tile: PaohuziTile): Boolean =
        hand.count { it == tile } >= 3

    /** 当前手牌可以打出的所有牌（按去重后的种类）。 */
    fun discardOptions(hand: List<PaohuziTile>): List<PaohuziTile> =
        hand.distinct().sortedForDisplay()
}
