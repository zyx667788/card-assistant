package com.gameocr.app.game.session

import android.graphics.Bitmap
import android.os.SystemClock
import com.gameocr.app.data.LogRepository
import com.gameocr.app.data.Settings
import com.gameocr.app.game.core.AdviceContext
import com.gameocr.app.game.core.AdviceEngine
import com.gameocr.app.game.core.AdviceRequest
import com.gameocr.app.game.core.AdviceResult
import com.gameocr.app.game.core.BoardRecognizer
import com.gameocr.app.game.core.GameModule
import com.gameocr.app.game.core.GameModuleRegistry
import com.gameocr.app.game.core.GameState
import com.gameocr.app.game.core.VlmRecognitionException
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

sealed interface GameTurnOutcome {
    data class AdviceReady(
        val module: GameModule,
        val state: GameState,
        val advice: com.gameocr.app.game.core.GameAdvice,
    ) : GameTurnOutcome

    data class RecognitionFailed(
        val module: GameModule,
        val message: String,
    ) : GameTurnOutcome

    data class AdviceFailed(
        val module: GameModule,
        val state: GameState,
        val message: String,
    ) : GameTurnOutcome

    data class Error(val message: String) : GameTurnOutcome
}

/**
 * 牌局识别 -> 牌局状态 -> 合法动作提示词 -> LLM 建议。
 *
 * 识别只走 VLM 一条路：整张截图直发云端视觉模型，不跑本地 OCR、也不要求标定区域。
 * 后面合法动作计算（本地规则引擎）与决策建议（文本 LLM）保持不变。
 */
@Singleton
class GameTurnCoordinator @Inject constructor(
    private val adviceEngine: AdviceEngine,
    private val modules: GameModuleRegistry,
    private val sessions: GameSessionManager,
    private val logs: LogRepository,
) {
    suspend fun analyze(bitmap: Bitmap, settings: Settings): GameTurnOutcome {
        val module = modules.findOrNull(settings.gameModuleId) ?: modules.default
            ?: return GameTurnOutcome.Error("没有可用的牌类模块")

        val recognizer: BoardRecognizer = module.recognizer
        val screenshotJpeg = encodeScreenshotForVlm(bitmap)

        val recognizeStartedAt = SystemClock.elapsedRealtime()
        val state = try {
            recognizer.recognize(screenshotJpeg)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: VlmRecognitionException) {
            logs.error(
                category = LogRepository.Category.RECOGNITION,
                message = error.message ?: "VLM 识别失败",
                elapsedMs = SystemClock.elapsedRealtime() - recognizeStartedAt,
            )
            return GameTurnOutcome.Error(error.message ?: "VLM 识别失败")
        } catch (error: Exception) {
            logs.error(
                category = LogRepository.Category.RECOGNITION,
                message = "识别失败：${error.message ?: error.javaClass.simpleName}",
                t = error,
                elapsedMs = SystemClock.elapsedRealtime() - recognizeStartedAt,
            )
            return GameTurnOutcome.Error("识别失败：${error.message ?: error.javaClass.simpleName}")
        } ?: return GameTurnOutcome.RecognitionFailed(
            module = module,
            message = "没有识别出足够的手牌，请重试或检查画面是否清晰。",
        )

        // 识别明细写进日志：排查「这次为什么认错牌」时，这一行就是现场。
        logs.info(
            category = LogRepository.Category.RECOGNITION,
            message = "识别成功：" + state.toPromptText()
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("；"),
            elapsedMs = SystemClock.elapsedRealtime() - recognizeStartedAt,
        )

        val adviceStartedAt = SystemClock.elapsedRealtime()
        val request = AdviceRequest(
            moduleId = module.id,
            systemPrompt = module.promptPolicy.systemPrompt(),
            userPrompt = module.promptPolicy.userPrompt(
                state = state,
                context = AdviceContext(history = sessions.history(module.id)),
            ),
        )
        return when (val result = adviceEngine.advise(request)) {
            is AdviceResult.Success -> {
                sessions.record(module.id, state, result.advice)
                logs.info(
                    category = LogRepository.Category.ADVICE,
                    message = "建议：" + result.advice.action.displayName +
                        result.advice.targetTile?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty(),
                    elapsedMs = SystemClock.elapsedRealtime() - adviceStartedAt,
                )
                GameTurnOutcome.AdviceReady(module, state, result.advice)
            }
            is AdviceResult.Failure -> {
                logs.error(
                    category = LogRepository.Category.ADVICE,
                    message = result.message,
                    elapsedMs = SystemClock.elapsedRealtime() - adviceStartedAt,
                )
                GameTurnOutcome.AdviceFailed(
                    module = module,
                    state = state,
                    message = result.message,
                )
            }
        }
    }

    /**
     * 给 VLM 的截图预处理：长边压到 [VLM_MAX_SIDE_PX]、JPEG 质量 [VLM_JPEG_QUALITY]。
     * 斗地主整屏横排十几张牌，768px 时每张牌太窄，VLM 容易把同点同色牌合并；
     * 1152px 下牌面点数与颜色边界仍清晰，同时体积和上传延迟可控。
     */
    private fun encodeScreenshotForVlm(bitmap: Bitmap): ByteArray {
        val (targetW, targetH) = vlmTargetSize(bitmap.width, bitmap.height)
        val scaled = if (targetW != bitmap.width || targetH != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }
        return try {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, VLM_JPEG_QUALITY, out)
            out.toByteArray()
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    companion object {
        const val VLM_MAX_SIDE_PX = 1152
        const val VLM_JPEG_QUALITY = 85

        /**
         * 纯函数：给定原图尺寸，算出压到 [VLM_MAX_SIDE_PX] 后的目标尺寸（不放大，保持比例）。
         * 放在 JVM 里直接可测，避免单测依赖 Bitmap。
         */
        @JvmStatic
        fun vlmTargetSize(width: Int, height: Int, maxSide: Int = VLM_MAX_SIDE_PX): Pair<Int, Int> {
            val longest = maxOf(width, height)
            if (longest <= maxSide) return width to height
            val scale = maxSide.toFloat() / longest
            return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
        }
    }
}
