package com.gameocr.app.game.doudizhu

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * VLM 按 `assets/game/doudizhu/eyes.md` 输出的 JSON。
 *
 * 字段名与提示词里的 schema 一一对应；花色必须逐张给出，这是本地校验能发现
 * 「4 个 7 数成 3 个」的关键。
 */
@Serializable
data class DoudizhuVlmPayload(
    val hand: List<String> = emptyList(),
    val role: String? = null,
    @SerialName("bottom_cards") val bottomCards: List<String> = emptyList(),
    @SerialName("last_play_seat") val lastPlaySeat: String? = null,
    @SerialName("last_play") val lastPlay: List<String> = emptyList(),
    @SerialName("played_cards") val playedCards: List<String> = emptyList(),
    @SerialName("my_remaining") val myRemaining: Int? = null,
    @SerialName("up_remaining") val upRemaining: Int? = null,
    @SerialName("down_remaining") val downRemaining: Int? = null,
    @SerialName("action_hint") val actionHint: String? = null,
    @SerialName("global_observation") val globalObservation: String? = null,
)

/** 把 VLM 的 JSON 转成 [DoudizhuState]，并顺手跑一遍本地校验。 */
object DoudizhuVlmBoardParser {

    fun parse(payload: DoudizhuVlmPayload): DoudizhuState? {
        val hand = payload.hand.flatMap { DoudizhuCardCodec.parseAll(it) }
        if (hand.isEmpty()) return null

        val played = payload.playedCards.flatMap { DoudizhuCardCodec.parseAll(it) }
        val bottom = payload.bottomCards.flatMap { DoudizhuCardCodec.parseAll(it) }
        val role = parseRole(payload.role)
        val lastCards = payload.lastPlay.flatMap { DoudizhuCardCodec.parseAll(it) }

        return DoudizhuState(
            hand = hand,
            role = role,
            bottomCards = bottom,
            lastPlay = lastCards.takeIf { it.isNotEmpty() }
                ?.let { DoudizhuLastPlay(seat = parseSeat(payload.lastPlaySeat), cards = it) },
            playedCards = played,
            myRemaining = payload.myRemaining,
            upRemaining = payload.upRemaining,
            downRemaining = payload.downRemaining,
            actionHint = payload.actionHint?.takeIf { it.isNotBlank() },
            visualContext = payload.globalObservation?.takeIf { it.isNotBlank() },
            validation = DoudizhuBoardValidator.validate(
                hand = hand,
                playedCards = played,
                bottomCards = bottom,
                role = role,
                myRemaining = payload.myRemaining,
            ),
        )
    }

    fun parseRole(raw: String?): DoudizhuRole = when (raw?.trim()?.lowercase()) {
        null, "", "未识别", "未知", "unknown" -> DoudizhuRole.UNKNOWN
        "地主", "landlord", "dizhu" -> DoudizhuRole.LANDLORD
        "农民", "farmer", "nongmin" -> DoudizhuRole.FARMER
        else -> DoudizhuRole.UNKNOWN
    }

    fun parseSeat(raw: String?): DoudizhuSeat = when (raw?.trim()?.lowercase()) {
        "我", "me", "self", "mine" -> DoudizhuSeat.ME
        "上家", "左", "left", "up", "landlord_up" -> DoudizhuSeat.UP
        "下家", "右", "right", "down", "landlord_down" -> DoudizhuSeat.DOWN
        else -> DoudizhuSeat.UNKNOWN
    }
}
