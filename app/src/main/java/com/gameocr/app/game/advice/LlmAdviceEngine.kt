package com.gameocr.app.game.advice

import com.gameocr.app.data.Settings
import com.gameocr.app.data.SettingsRepository
import com.gameocr.app.data.withApiTimeout
import com.gameocr.app.game.core.AdviceEngine
import com.gameocr.app.game.core.AdviceRequest
import com.gameocr.app.game.core.AdviceResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 用 OpenAI 兼容接口产出决策建议。
 *
 * 复用应用已有的 OkHttpClient、API 地址 / 密钥 / 模型配置和超时策略，
 * 但走独立的请求与解析路径，不复用翻译的提示词与后处理。
 */
@Singleton
class LlmAdviceEngine @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
) : AdviceEngine {

    override suspend fun advise(request: AdviceRequest): AdviceResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.first()
        if (settings.apiKey.isBlank()) {
            return@withContext AdviceResult.Failure(MISSING_API_KEY_MESSAGE)
        }
        execute(settings, request)
    }

    private fun execute(settings: Settings, request: AdviceRequest): AdviceResult {
        val payload = AdviceChatRequest(
            model = settings.model,
            messages = listOf(
                AdviceMessage(role = "system", content = request.systemPrompt),
                AdviceMessage(role = "user", content = request.userPrompt),
            ),
        )
        val body = json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = Request.Builder()
            .url(ensureTrailingSlash(settings.baseUrl) + CHAT_COMPLETIONS_PATH)
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(body)
            .build()

        val timedClient = client.withApiTimeout(settings.apiTimeoutSeconds)
        return try {
            timedClient.newCall(httpRequest).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    AdviceResult.Failure("模型接口返回 ${response.code}")
                } else {
                    parseContent(raw, response.code)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AdviceResult.Failure(error.message ?: "调用模型失败", error)
        }
    }

    private fun parseContent(raw: String, statusCode: Int): AdviceResult {
        val content = runCatching {
            json.decodeFromString<AdviceChatResponse>(raw)
                .choices
                .firstOrNull()
                ?.message
                ?.content
        }.getOrNull()

        if (content.isNullOrBlank()) {
            return AdviceResult.Failure("模型返回了空内容（HTTP $statusCode）")
        }
        return AdviceResult.Success(AdviceResponseParser.parse(content, json))
    }

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    private companion object {
        const val CHAT_COMPLETIONS_PATH = "chat/completions"
        const val MISSING_API_KEY_MESSAGE = "还没有填写模型 API Key，请先到设置里配置"

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
