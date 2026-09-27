package com.gameocr.app.game.paohuzi

/** 已亮出的组合类型。 */
enum class PaohuziMeldKind(val displayName: String, val tileCount: Int) {
    /** 顺子：同门连续三张，口语也叫「一句话」。 */
    CHOW("顺子", 3),

    /** 三张相同，口语叫「坎」或「碰」。 */
    PUNG("坎", 3),

    /** 四张相同，口语叫「提」。 */
    KONG("提", 4),
}

/**
 * 一坎/一提/一顺。
 *
 * 这里不做张数校验：识别结果来自 OCR，宁可带着可疑数据往下走并在界面上让用户核对，
 * 也不要在构造阶段抛异常把整局打断。
 */
data class PaohuziMeld(
    val kind: PaohuziMeldKind,
    val tiles: List<PaohuziTile>,
) {
    val display: String get() = "${kind.displayName} ${PaohuziTileCodec.formatSpaced(tiles)}"
}

/**
 * 从「亮出的牌」序列里推断组合。
 *
 * 亮牌区通常是若干组按顺序摆放的牌，这里做贪心切分：先试 4 张相同（提），再试 3 张相同（坎），
 * 最后试同门连续三张（顺子）。识别不准时由用户在界面上修正。
 */
object PaohuziMeldInference {
    fun infer(tiles: List<PaohuziTile>): List<PaohuziMeld> {
        val melds = mutableListOf<PaohuziMeld>()
        var index = 0
        while (index < tiles.size) {
            val remaining = tiles.size - index
            if (remaining >= 4 && fourOfAKind(tiles, index)) {
                melds += PaohuziMeld(PaohuziMeldKind.KONG, tiles.subList(index, index + 4).toList())
                index += 4
                continue
            }
            if (remaining >= 3) {
                val first = tiles[index]
                val second = tiles[index + 1]
                val third = tiles[index + 2]
                when {
                    first == second && second == third -> {
                        melds += PaohuziMeld(
                            PaohuziMeldKind.PUNG,
                            tiles.subList(index, index + 3).toList(),
                        )
                        index += 3
                        continue
                    }

                    isRun(first, second, third) -> {
                        melds += PaohuziMeld(
                            PaohuziMeldKind.CHOW,
                            tiles.subList(index, index + 3).toList(),
                        )
                        index += 3
                        continue
                    }
                }
            }
            index += 1
        }
        return melds
    }

    private fun fourOfAKind(tiles: List<PaohuziTile>, index: Int): Boolean {
        val first = tiles[index]
        return tiles[index + 1] == first && tiles[index + 2] == first && tiles[index + 3] == first
    }

    private fun isRun(first: PaohuziTile, second: PaohuziTile, third: PaohuziTile): Boolean =
        first.case == second.case &&
            second.case == third.case &&
            second.rank == first.rank + 1 &&
            third.rank == second.rank + 1
}
