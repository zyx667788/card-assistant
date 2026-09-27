package com.gameocr.app.game.paohuzi

import android.util.Base64
import com.gameocr.app.data.SettingsRepository
import com.gameocr.app.data.withApiTimeout
import com.gameocr.app.game.advice.AdviceChatResponse
import com.gameocr.app.game.core.BoardRecognizer
import com.gameocr.app.game.core.BoardZone
import com.gameocr.app.game.core.GameState
import com.gameocr.app.game.core.TileTextSpan
import javax.inject.Inject
import javax.inject.Singleton
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
 * 用云端 VLM 直接看整张截图识别跑胡子牌局。
 *
 * 与 OCR 路径的区别：
 * 1. 不需要用户标定区域，模型自己看全局找牌；
 * 2. 不跑本地 OCR，直接输出结构化牌局 JSON；
 * 3. 额外产出一段 [VlmBoardPayload.globalObservation]「全局观察」，
 *    拼进决策提示词，补上结构化牌局之外的局势信息。
 *
 * 识别只负责「看清牌面」，合法动作仍由 [PaohuziRules] 在本地计算，
 * 决策仍由文本 LLM 在合法动作里选——VLM 当眼睛，不当裁判。
 */
@Singleton
class PaohuziVlmBoardRecognizer @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
) : BoardRecognizer {

    override suspend fun recognize(
        spans: List<TileTextSpan>,
        zones: List<BoardZone>,
        imageWidth: Int,
        imageHeight: Int,
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
                put("content", VLM_SYSTEM_PROMPT)
            }
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", VLM_USER_PROMPT)
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
        // DeepSeek V4.1 的思考模式默认开启；识别是结构化抽取任务不需要深度思考，
        // 关掉可以显著降低延迟。大多数 OpenAI 兼容接口会直接忽略未知字段。
        putJsonObject("thinking") { put("type", "disabled") }
    }.toString()

    private fun parseBoard(raw: String): PaohuziState? {
        val content = runCatching {
            json.decodeFromString<AdviceChatResponse>(raw)
                .choices.firstOrNull()?.message?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: throw VlmRecognitionException("模型返回了空内容")
        val payload = runCatching {
            json.decodeFromString<VlmBoardPayload>(stripCodeFences(content))
        }.getOrNull() ?: throw VlmRecognitionException("模型返回的牌局数据解析失败，请重试")
        return VlmBoardParser.parse(payload)
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

        const val VLM_SYSTEM_PROMPT = """
你是跑胡子（字牌）牌局识别器。看用户发来的手机截图，识别牌局并只输出 JSON，不要输出任何解释文字。

牌面说明：
- 跑胡子共 80 张牌：小写「一二三四五六七八九十」和大写「壹贰叁肆伍陆柒捌玖拾」各 10 种，每种 4 张。
- 小写牌面通常是黑色字，大写牌面通常是红色字（不同 App 配色可能不同，以字形为准）。

画面布局（竖屏跑胡子 App 的常见布局，仅供参考，一切以实际画面为准）：
- 屏幕下方横排的是「我的手牌」。
- 手牌上方、桌面中央散放的是已打出的牌。
- 左右两侧竖排的是对手亮出的组合。
- 屏幕上可能出现「吃」「碰」「提」「胡」「过」等操作按钮，按钮附近可能显示刚打出的那张牌。
- 某处可能显示剩余牌数（如「余 36 张」）。

输出 JSON（只输出 JSON 本体，不要用代码块包裹）：
{
  "hand": ["一","二","三","壹","贰"],
  "self_melds": [["一","二","三"],["伍","伍","伍"]],
  "table_discards": ["四","五"],
  "opponent_left_melds": [["壹","贰","叁"]],
  "opponent_right_melds": [],
  "incoming_tile": "六",
  "remaining_count": 36,
  "action_hint": "画面上的操作提示原文，没有则为空字符串",
  "global_observation": "用一句话描述全局局势，例如谁攻势猛、轮到谁、桌面关键信息"
}

规则：
- hand 按从左到右列出全部手牌单字。
- 每个亮出的组合用单字数组表示；看不清的组合不要编造，宁可返回空数组。
- 只有当画面明确显示别人刚打出某张牌、等我决策时，incoming_tile 才填那张牌，否则填 null。
- 看不到剩余牌数时 remaining_count 填 null。
"""

        const val VLM_USER_PROMPT = "识别这张跑胡子牌局截图，按 system 要求的 JSON 格式输出。"
    }
}
