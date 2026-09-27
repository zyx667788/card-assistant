package com.gameocr.app.game.advice

import com.gameocr.app.game.core.AdviceAction
import com.gameocr.app.game.core.GameAdvice
import kotlinx.serialization.json.Json

/**
 * 把模型输出解析成 [GameAdvice]。
 *
 * 模型经常把 JSON 包在 ```json 代码块里，或前后带一句解释，所以先做括号配平提取，
 * 解析失败也不抛异常：退回 [AdviceAction.UNKNOWN] 并保留原文，界面照样能显示。
 */
object AdviceResponseParser {

    fun parse(raw: String, json: Json): GameAdvice {
        val payload = extractJsonObject(raw)?.let { candidate ->
            runCatching { json.decodeFromString<AdvicePayload>(candidate) }.getOrNull()
        }
        if (payload == null) {
            return GameAdvice(
                action = AdviceAction.UNKNOWN,
                reason = raw.trim().take(MAX_RAW_REASON_LENGTH),
                rawText = raw,
            )
        }
        return GameAdvice(
            action = AdviceAction.fromWire(payload.action),
            targetTile = payload.tile?.trim()?.takeIf { it.isNotEmpty() },
            reason = payload.reason?.trim().orEmpty(),
            alternatives = payload.alternatives.map { it.trim() }.filter { it.isNotEmpty() },
            rawText = raw,
        )
    }

    /** 取出文本里第一个括号配平的 JSON 对象，字符串内部的括号会被跳过。 */
    fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var insideString = false
        var escaped = false

        for (index in start until raw.length) {
            val char = raw[index]
            if (insideString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> insideString = false
                }
                continue
            }
            when (char) {
                '"' -> insideString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private const val MAX_RAW_REASON_LENGTH = 200
}
