package com.gameocr.app.game.paohuzi

/** 跑胡子的大小写门。字牌分小字（一~十）与大字（壹~拾）两门，顺子只在同一门内成立。 */
enum class TileCase(val displayName: String) {
    SMALL("小字"),
    BIG("大字"),
}

/**
 * 跑胡子的一张牌：由「门」和「点数」唯一确定。
 *
 * 全副牌 10 个点数 × 2 门 × 每种 4 张 = 80 张。
 */
data class PaohuziTile(val rank: Int, val case: TileCase) {
    init {
        require(rank in MIN_RANK..MAX_RANK) { "rank out of range: $rank" }
    }

    val display: String
        get() = if (case == TileCase.SMALL) SMALL_NUMERALS[rank - 1] else BIG_NUMERALS[rank - 1]

    /** 小字在前、大字在后，同门内按点数升序。 */
    val sortKey: Int get() = case.ordinal * RANKS_PER_CASE + rank

    override fun toString(): String = display

    companion object {
        const val MIN_RANK = 1
        const val MAX_RANK = 10
        const val RANKS_PER_CASE = 10
        const val COPIES_PER_TILE = 4

        /** 不同牌的种类数：10 点数 × 2 门。 */
        const val DISTINCT_TILE_COUNT = RANKS_PER_CASE * 2

        /** 全副牌张数：80。 */
        const val TOTAL_TILE_COUNT = DISTINCT_TILE_COUNT * COPIES_PER_TILE

        val SMALL_NUMERALS: List<String> =
            listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

        val BIG_NUMERALS: List<String> =
            listOf("壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖", "拾")

        val ALL: List<PaohuziTile> = TileCase.entries.flatMap { tileCase ->
            (MIN_RANK..MAX_RANK).map { rank -> PaohuziTile(rank, tileCase) }
        }

        fun of(rank: Int, big: Boolean = false): PaohuziTile =
            PaohuziTile(rank, if (big) TileCase.BIG else TileCase.SMALL)
    }
}

/** 把牌转成计算用的下标：小字 0..9，大字 10..19。 */
fun PaohuziTile.indexOf(): Int = case.ordinal * PaohuziTile.RANKS_PER_CASE + (rank - 1)

/** 把下标还原成牌。 */
fun tileAtIndex(index: Int): PaohuziTile = PaohuziTile(
    rank = index % PaohuziTile.RANKS_PER_CASE + 1,
    case = if (index < PaohuziTile.RANKS_PER_CASE) TileCase.SMALL else TileCase.BIG,
)

/** 统计一手牌里每种牌的张数。 */
fun List<PaohuziTile>.toCounts(): IntArray {
    val counts = IntArray(PaohuziTile.DISTINCT_TILE_COUNT)
    forEach { counts[it.indexOf()]++ }
    return counts
}

/** 稳定的展示顺序：小字升序在前，大字升序在后。 */
fun List<PaohuziTile>.sortedForDisplay(): List<PaohuziTile> = sortedWith(
    compareBy({ it.case.ordinal }, { it.rank }),
)
