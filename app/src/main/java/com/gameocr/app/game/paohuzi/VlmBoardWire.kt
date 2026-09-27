package com.gameocr.app.game.paohuzi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** VLM 识别失败时的异常，由调用方转成面向用户的错误提示。 */
class VlmRecognitionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 约定 VLM 返回的牌局 JSON 结构。
 *
 * 牌用单字表示，例如 "一"、"伍"、"壹"；解析时走 [PaohuziTileCodec]，
 * 形近字 / 异体字同样能归一化。
 */
@Serializable
data class VlmBoardPayload(
    /** 我的手牌，从左到右。 */
    val hand: List<String> = emptyList(),
    /** 我已亮出的组合，每组一个数组。 */
    @SerialName("self_melds") val selfMelds: List<List<String>> = emptyList(),
    /** 桌面已打出的牌。 */
    @SerialName("table_discards") val tableDiscards: List<String> = emptyList(),
    /** 左家亮出的组合。 */
    @SerialName("opponent_left_melds") val opponentLeftMelds: List<List<String>> = emptyList(),
    /** 右家亮出的组合。 */
    @SerialName("opponent_right_melds") val opponentRightMelds: List<List<String>> = emptyList(),
    /** 别人刚打出、等我决策的那张牌；没有则为 null。 */
    @SerialName("incoming_tile") val incomingTile: String? = null,
    /** 剩余未摸牌数；看不到则为 null。 */
    @SerialName("remaining_count") val remainingCount: Int? = null,
    /** 画面上的操作提示原文；没有则为空。 */
    @SerialName("action_hint") val actionHint: String? = null,
    /** VLM 对全局局势的一句话观察，会原样拼进决策提示词。 */
    @SerialName("global_observation") val globalObservation: String? = null,
)
