package com.gameocr.app.game.session

import android.graphics.Bitmap
import com.gameocr.app.data.Settings
import com.gameocr.app.game.core.AdviceContext
import com.gameocr.app.game.core.AdviceEngine
import com.gameocr.app.game.core.AdviceRequest
import com.gameocr.app.game.core.AdviceResult
import com.gameocr.app.game.core.BoardRecognizer
import com.gameocr.app.game.core.GameModule
import com.gameocr.app.game.core.GameModuleRegistry
import com.gameocr.app.game.core.GameRecognizerKind
import com.gameocr.app.game.core.GameState
import com.gameocr.app.game.core.TileTextSpan
import com.gameocr.app.game.integration.toTileTextSpans
import com.gameocr.app.game.paohuzi.PaohuziVlmBoardRecognizer
import com.gameocr.app.game.paohuzi.VlmRecognitionException
import com.gameocr.app.ocr.ModelNotReadyException
import com.gameocr.app.ocr.OcrEngine
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
 * 识别有两条路径，由 [Settings.gameRecognizer] 选择：
 * - OCR：本地 OCR 文字 -> [BoardRecognizer] 按标定区域还原牌局；
 * - VLM：整张截图直发云端视觉模型，不跑 OCR、也不要求标定区域。
 * 后面合法动作计算与决策建议两条路径完全一致。
 */
@Singleton
class GameTurnCoordinator @Inject constructor(
    private val ocrEngine: OcrEngine,
    private val adviceEngine: AdviceEngine,
    private val modules: GameModuleRegistry,
    private val sessions: GameSessionManager,
    private val vlmRecognizer: PaohuziVlmBoardRecognizer,
) {
    suspend fun analyze(bitmap: Bitmap, settings: Settings): GameTurnOutcome {
        val module = modules.findOrNull(settings.gameModuleId) ?: modules.default
            ?: return GameTurnOutcome.Error("没有可用的牌类模块")
        val useVlm = settings.gameRecognizer == GameRecognizerKind.VLM
        val zones = settings.gameZonesByModule[module.id].orEmpty()
        if (!useVlm && zones.isEmpty()) {
            return GameTurnOutcome.Error("${module.displayName} 尚未标定区域，请长按悬浮球选择「牌局区域标定」")
        }

        val recognizer: BoardRecognizer = if (useVlm) vlmRecognizer else module.recognizer

        // VLM 路径跳过本地 OCR：省一次识别耗时，也不需要提前装 OCR 模型。
        val spans: List<TileTextSpan>
        val screenshotJpeg: ByteArray?
        if (useVlm) {
            spans = emptyList()
            screenshotJpeg = encodeScreenshotForVlm(bitmap)
        } else {
            val blocks = try {
                ocrEngine.recognize(bitmap, settings.ocrEngine, settings)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ModelNotReadyException) {
                return GameTurnOutcome.Error(error.message ?: "OCR 模型尚未安装")
            } catch (error: Exception) {
                return GameTurnOutcome.Error("识别失败：${error.message ?: error.javaClass.simpleName}")
            }
            spans = blocks.toTileTextSpans()
            screenshotJpeg = null
        }

        val state = try {
            recognizer.recognize(
                spans = spans,
                zones = zones,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                screenshotJpeg = screenshotJpeg,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: VlmRecognitionException) {
            return GameTurnOutcome.Error(error.message ?: "VLM 识别失败")
        } catch (error: Exception) {
            return GameTurnOutcome.Error("识别失败：${error.message ?: error.javaClass.simpleName}")
        } ?: return GameTurnOutcome.RecognitionFailed(
            module = module,
            message = "没有识别出足够的手牌，请重试或检查画面是否清晰。",
        )

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
                GameTurnOutcome.AdviceReady(module, state, result.advice)
            }
            is AdviceResult.Failure -> GameTurnOutcome.AdviceFailed(
                module = module,
                state = state,
                message = result.message,
            )
        }
    }

    /**
     * 给 VLM 的截图预处理：长边压到 768px、JPEG 质量 75。
     * 云端视觉模型内部本来就会缩图，先压小可以省流量和上传时间，
     * 牌面字在这个尺寸下依然清晰可辨。
     */
    private fun encodeScreenshotForVlm(bitmap: Bitmap): ByteArray {
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > VLM_MAX_SIDE_PX) {
            val scale = VLM_MAX_SIDE_PX.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
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

    private companion object {
        const val VLM_MAX_SIDE_PX = 768
        const val VLM_JPEG_QUALITY = 75
    }
}
