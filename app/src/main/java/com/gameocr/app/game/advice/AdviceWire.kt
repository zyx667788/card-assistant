package com.gameocr.app.game.advice

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** OpenAI 兼容 chat completions 的最小请求体。 */
@Serializable
internal data class AdviceChatRequest(
    val model: String,
    val messages: List<AdviceMessage>,
    val temperature: Double = 0.2,
    val stream: Boolean = false,
    @SerialName("max_tokens") val maxTokens: Int? = null,
)

@Serializable
internal data class AdviceMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class AdviceChatResponse(
    val choices: List<AdviceChoice> = emptyList(),
)

@Serializable
internal data class AdviceChoice(
    val index: Int = 0,
    val message: AdviceMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

/** 模型约定返回的 JSON 结构。 */
@Serializable
internal data class AdvicePayload(
    val action: String? = null,
    val tile: String? = null,
    val reason: String? = null,
    val alternatives: List<String> = emptyList(),
)
