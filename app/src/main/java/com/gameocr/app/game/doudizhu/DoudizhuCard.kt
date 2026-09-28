package com.gameocr.app.game.doudizhu

/** 斗地主模块的注册 id。 */
const val DOUDIZHU_MODULE_ID = "doudizhu"

/**
 * 斗地主点数。
 *
 * [order] 用于比大小：3 最小，大王最大；顺子 / 连对 / 飞机只允许 3..A（见 [inSequence]）。
 */
enum class DoudizhuRank(val label: String, val order: Int) {
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
    SEVEN("7", 7),
    EIGHT("8", 8),
    NINE("9", 9),
    TEN("10", 10),
    JACK("J", 11),
    QUEEN("Q", 12),
    KING("K", 13),
    ACE("A", 14),
    TWO("2", 15),
    SMALL_JOKER("小王", 16),
    BIG_JOKER("大王", 17),
    ;

    val isJoker: Boolean get() = this == SMALL_JOKER || this == BIG_JOKER

    /** 顺子 / 连对 / 飞机只允许 3..A，2 与大小王不能进连牌。 */
    val inSequence: Boolean get() = order in THREE.order..ACE.order

    companion object {
        /** 记牌用的全部点数（从小到大）。 */
        val ALL: List<DoudizhuRank> = entries.toList()

        /** 每个点数 4 张，大小王各 1 张。 */
        fun maxCopies(rank: DoudizhuRank): Int = if (rank.isJoker) 1 else 4
    }
}

/** 花色。大小王没有花色；模型没给出花色时用 [UNKNOWN]，不参与合法性判断。 */
enum class DoudizhuSuit(val label: String, val code: String) {
    SPADE("黑桃", "s"),
    HEART("红桃", "h"),
    CLUB("梅花", "c"),
    DIAMOND("方块", "d"),
    JOKER("", ""),
    UNKNOWN("", ""),
}

/**
 * 一张牌。
 *
 * 花色只用于「同一张牌不能出现两次」的校验；斗地主的牌型大小只看点数和张数。
 */
data class DoudizhuCard(
    val rank: DoudizhuRank,
    val suit: DoudizhuSuit = DoudizhuSuit.UNKNOWN,
) {
    val isJoker: Boolean get() = rank.isJoker

    /** 提示词与悬浮卡里给人看的写法，例如「黑桃7」「10」「小王」。 */
    val display: String
        get() = when {
            isJoker -> rank.label
            suit == DoudizhuSuit.UNKNOWN -> rank.label
            else -> suit.label + rank.label
        }

    /** 紧凑写法，例如「7s」「10h」「小王」。 */
    val code: String
        get() = if (isJoker) rank.label else rank.label + suit.code
}

/**
 * 牌面文本 <-> [DoudizhuCard]。
 *
 * 同时接受模型可能输出的几种写法，避免因为格式差异判成识别失败：
 * - 中文花色「黑桃7」「红桃10」「草花J」
 * - 花色符号「♠7」「7♥」
 * - 字母花色「7s」「10h」「Jc」「Ad」
 * - 没给花色时按点数解析（花色记 UNKNOWN，校验阶段会提示）
 */
object DoudizhuCardCodec {

    private val SUIT_ALIASES: List<Pair<String, DoudizhuSuit>> = listOf(
        "黑桃" to DoudizhuSuit.SPADE,
        "红桃" to DoudizhuSuit.HEART,
        "梅花" to DoudizhuSuit.CLUB,
        "草花" to DoudizhuSuit.CLUB,
        "方块" to DoudizhuSuit.DIAMOND,
        "方片" to DoudizhuSuit.DIAMOND,
        "♠" to DoudizhuSuit.SPADE,
        "♥" to DoudizhuSuit.HEART,
        "♣" to DoudizhuSuit.CLUB,
        "♦" to DoudizhuSuit.DIAMOND,
        "spade" to DoudizhuSuit.SPADE,
        "heart" to DoudizhuSuit.HEART,
        "club" to DoudizhuSuit.CLUB,
        "diamond" to DoudizhuSuit.DIAMOND,
        "s" to DoudizhuSuit.SPADE,
        "h" to DoudizhuSuit.HEART,
        "c" to DoudizhuSuit.CLUB,
        "d" to DoudizhuSuit.DIAMOND,
    )

    private val RANK_ALIASES: Map<String, DoudizhuRank> = buildMap {
        put("3", DoudizhuRank.THREE)
        put("4", DoudizhuRank.FOUR)
        put("5", DoudizhuRank.FIVE)
        put("6", DoudizhuRank.SIX)
        put("7", DoudizhuRank.SEVEN)
        put("8", DoudizhuRank.EIGHT)
        put("9", DoudizhuRank.NINE)
        put("10", DoudizhuRank.TEN)
        put("T", DoudizhuRank.TEN)
        put("J", DoudizhuRank.JACK)
        put("Q", DoudizhuRank.QUEEN)
        put("K", DoudizhuRank.KING)
        put("A", DoudizhuRank.ACE)
        put("2", DoudizhuRank.TWO)
        put("小王", DoudizhuRank.SMALL_JOKER)
        put("大王", DoudizhuRank.BIG_JOKER)
        put("小丑", DoudizhuRank.SMALL_JOKER)
        put("大丑", DoudizhuRank.BIG_JOKER)
        put("SJ", DoudizhuRank.SMALL_JOKER)
        put("BJ", DoudizhuRank.BIG_JOKER)
    }

    /** 一段文本里的多个牌，按空格 / 逗号 / 顿号等分隔。 */
    fun parseAll(text: String): List<DoudizhuCard> =
        text.split(*SPLITTERS)
            .asSequence()
            .mapNotNull { parse(it) }
            .toList()

    /** 单个牌。解析不出来返回 null，由调用方决定是忽略还是报「看不清」。 */
    fun parse(token: String): DoudizhuCard? {
        val raw = token.trim()
        if (raw.isEmpty()) return null

        var rest = raw
        var suit = DoudizhuSuit.UNKNOWN
        for ((alias, candidate) in SUIT_ALIASES) {
            val index = rest.indexOf(alias, ignoreCase = true)
            if (index < 0) continue
            suit = candidate
            rest = rest.removeRange(index, index + alias.length)
            break
        }

        val rank = RANK_ALIASES[rest.trim().uppercase()] ?: return null
        return DoudizhuCard(
            rank = rank,
            suit = when {
                rank.isJoker -> DoudizhuSuit.JOKER
                suit == DoudizhuSuit.JOKER -> DoudizhuSuit.UNKNOWN
                else -> suit
            },
        )
    }

    /** 人看的写法：「黑桃7 红桃7 梅花7 方块7」。 */
    fun format(cards: List<DoudizhuCard>): String = cards.joinToString(" ") { it.display }

    /** 紧凑写法：「7s 7h 7c 7d」，写进提示词给模型对齐用。 */
    fun formatCodes(cards: List<DoudizhuCard>): String = cards.joinToString(" ") { it.code }

    /** 按点数分组统计，key 为 [DoudizhuRank.order]。 */
    fun countsByRank(cards: List<DoudizhuCard>): Map<Int, Int> =
        cards.groupingBy { it.rank.order }.eachCount()

    private val SPLITTERS = arrayOf(" ", "\t", "\n", ",", "，", "、", ";", "；", "/", "|", "+")
}

/** 展示顺序：先按点数从小到大，同点数按固定花色顺序，保证提示词可复现。 */
fun List<DoudizhuCard>.sortedForDisplay(): List<DoudizhuCard> =
    sortedWith(compareBy({ it.rank.order }, { it.suit.ordinal }))
