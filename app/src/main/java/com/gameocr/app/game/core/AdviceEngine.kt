package com.gameocr.app.game.core

/** 一次决策请求所需的全部输入。 */
data class AdviceRequest(
    val moduleId: String,
    val systemPrompt: String,
    val userPrompt: String,
)

/** 决策结果。失败不抛异常，交给调用方决定怎么提示用户。 */
sealed interface AdviceResult {
    data class Success(val advice: GameAdvice) : AdviceResult

    data class Failure(val message: String, val cause: Throwable? = null) : AdviceResult
}

/** 决策引擎：把牌局状态交给模型并拿回建议。传输实现与牌类模块解耦。 */
interface AdviceEngine {
    suspend fun advise(request: AdviceRequest): AdviceResult
}
