package com.gameocr.app.game.core

/** 决策建议的动作类型。 */
enum class AdviceAction(val displayName: String) {
    PLAY("出牌"),
    CHOW("吃"),
    PUNG("碰"),
    KONG("提"),
    WIN("胡"),
    PASS("过"),
    UNKNOWN("待确认"),
    ;

    companion object {
        /** 兼容模型输出的中英文写法。 */
        fun fromWire(value: String?): AdviceAction = when (value?.trim()?.lowercase()) {
            "play", "discard", "出牌", "打", "打牌" -> PLAY
            "chow", "chi", "吃" -> CHOW
            "pung", "peng", "碰", "坎" -> PUNG
            "kong", "ti", "提", "招", "开招", "杠" -> KONG
            "win", "hu", "胡", "胡牌" -> WIN
            "pass", "skip", "过", "不要", "放弃" -> PASS
            else -> UNKNOWN
        }
    }
}

/**
 * 一条决策建议。
 *
 * [rawText] 在模型输出无法解析成结构时保留原文，界面仍然能把内容展示出来。
 */
data class GameAdvice(
    val action: AdviceAction,
    val targetTile: String? = null,
    val reason: String = "",
    val alternatives: List<String> = emptyList(),
    val rawText: String? = null,
)
