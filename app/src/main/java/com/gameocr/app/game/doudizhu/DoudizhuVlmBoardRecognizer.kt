package com.gameocr.app.game.doudizhu

import android.util.Base64
import com.gameocr.app.data.SettingsRepository
import com.gameocr.app.data.withApiTimeout
import com.gameocr.app.game.advice.AdviceChatResponse
import com.gameocr.app.game.core.BoardRecognizer
import com.gameocr.app.game.core.GameState
import com.gameocr.app.game.core.VlmRecognitionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 斗地主识别器：整屏截图 → VLM 按 eyes 提示词输出结构化 JSON → 本地校验。
 *
 * 与跑胡子的区别只有提示词和解析目标；两者都要求模型给出逐张、带花色的牌面，
 * 再由本地用 54 张牌的硬约束复核，避免「4 个 7 数成 3 个」这类计数错误。
 */
class DoudizhuVlmBoardRecognizer(
    private val eyesSystemPrompt: String,
    private val eyesUserPrompt: String,
    private val client: OkHttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
) : BoardRecognizer {

    override suspend fun recognize(
        screenshotJpeg: ByteArray?,
    ): GameState? = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.first()
        if (settings.apiKey.isBlank()) {
            throw VlmRecognitionException("还没有填写模型 API Key，请先到设置里配置")
        }
        val jpeg = screenshotJpeg?.takeIf { it.isNotEmpty() }
            ?: throw VlmRecognitionException("VLM 识别缺少截图数据")

        val requestBody = buildRequestJson(
            model = settings.model.ifBlank { DEFAULT_VLM_MODEL },
            imageBase64 = Base64.encodeToString(jpeg, Base64.NO_WRAP),
        ).toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = Request.Builder()
            .url(ensureTrailingSlash(settings.baseUrl) + CHAT_COMPLETIONS_PATH)
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("Accept", "application/json")
            .post(requestBody)
            .build()

        val raw = try {
            client.withApiTimeout(settings.apiTimeoutSeconds)
                .newCall(httpRequest).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw VlmRecognitionException("模型接口返回 ${response.code}")
                    }
                    body
                }
        } catch (error: CancellationException) {
            throw error
        } catch (error: VlmRecognitionException) {
            throw error
        } catch (error: Exception) {
            throw VlmRecognitionException(error.message ?: "调用模型失败", error)
        }
        parseBoard(raw)
    }

    private fun buildRequestJson(model: String, imageBase64: String): String = buildJsonObject {
        put("model", model)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", eyesSystemPrompt)
            }
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", eyesUserPrompt)
                    }
                    addJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", "data:image/jpeg;base64,$imageBase64")
                        }
                    }
                }
            }
        }
        put("temperature", 0.1)
        put("max_tokens", 2048)
        put("stream", false)
        putJsonObject("response_format") { put("type", "json_object") }
        // 结构化抽取不需要深度思考，关掉可以显著降低延迟；兼容接口会忽略未知字段。
        putJsonObject("thinking") { put("type", "disabled") }
    }.toString()

    private fun parseBoard(raw: String): DoudizhuState {
        val content = runCatching {
            json.decodeFromString<AdviceChatResponse>(raw)
                .choices.firstOrNull()?.message?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: throw VlmRecognitionException("模型返回了空内容")

        val payload = runCatching {
            json.decodeFromString<DoudizhuVlmPayload>(stripCodeFences(content))
        }.getOrNull() ?: throw VlmRecognitionException("模型返回的牌局数据解析失败，请重试")

        val state = DoudizhuVlmBoardParser.parse(payload)
            ?: throw VlmRecognitionException("没识别出手牌，请确认画面里能看到自己的牌")

        // 硬错误（同一张牌重复 / 某点数超过 4 张 / 手牌与已出牌重合）说明这次识别不可信，
        // 直接要求重新识别，不要拿矛盾数据去算建议。
        if (state.validation.isFatal) {
            val detail = state.validation.errors.take(2).joinToString("；")
            throw VlmRecognitionException("识别结果自相矛盾（$detail），请重新截图识别")
        }
        return state
    }

    private fun stripCodeFences(content: String): String {
        val trimmed = content.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```")
            .removePrefix("json")
            .removeSuffix("```")
            .trim()
    }

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    private companion object {
        const val CHAT_COMPLETIONS_PATH = "chat/completions"
        const val DEFAULT_VLM_MODEL = "deepseek-flash"

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
