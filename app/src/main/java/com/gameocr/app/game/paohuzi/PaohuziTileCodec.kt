package com.gameocr.app.game.paohuzi

/**
 * 牌面文本与 [PaohuziTile] 之间的转换。
 *
 * OCR 经常把形近字认错（叁/參/参、贰/貳、陆/陸……），这里统一做归一化。
 * 无法识别的字符（分隔符、牌框、噪点）直接忽略，不报错。
 */
object PaohuziTileCodec {

    /** 小字门：正字 + 常见 OCR 变体。 */
    private val SMALL_ALIASES: Map<Char, Int> = buildMap {
        "一二三四五六七八九十".forEachIndexed { index, char -> put(char, index + 1) }
        // 形近 / 手写变体
        put('幺', 1)
        put('—', 1)
        put('－', 1)
    }

    /** 大字门：正字 + 常见 OCR 变体。 */
    private val BIG_ALIASES: Map<Char, Int> = buildMap {
        "壹贰叁肆伍陆柒捌玖拾".forEachIndexed { index, char -> put(char, index + 1) }
        // 形近 / 异体变体
        put('壱', 1)
        put('貳', 2)
        put('貮', 2)
        put('弍', 2)
        put('參', 3)
        put('叄', 3)
        put('参', 3)
        put('陸', 6)
        put('柒', 7)
        put('仈', 8)
        put('玖', 9)
        put('什', 10)
    }

    /**
     * 单个字符解析。
     *
     * 大写门优先：`壹`~`拾` 只在打牌场景里出现，落到小字门会把大字误判成小字。
     * 若某个字在两门里都成立（例如 `壹`），按大字处理，调用方需要小字时应使用正字「一」。
     */
    fun parseChar(char: Char): PaohuziTile? {
        BIG_ALIASES[char]?.let { return PaohuziTile(it, TileCase.BIG) }
        SMALL_ALIASES[char]?.let { return PaohuziTile(it, TileCase.SMALL) }
        // 阿拉伯数字兜底：1..9 视为小字，0 忽略
        if (char in '1'..'9') return PaohuziTile(char - '0', TileCase.SMALL)
        return null
    }

    /** 解析一段文本，返回其中所有能识别的牌，顺序按文本顺序。 */
    fun parse(text: String): List<PaohuziTile> {
        if (text.isEmpty()) return emptyList()
        return text.mapNotNull { parseChar(it) }
    }

    /** 把若干张牌拼成紧凑文本，用于提示词与日志。 */
    fun format(tiles: List<PaohuziTile>): String = tiles.joinToString("") { it.display }

    /** 带分隔的展示形式，例如「一 二 三」。 */
    fun formatSpaced(tiles: List<PaohuziTile>): String =
        tiles.joinToString(" ") { it.display }
}
