package com.gameocr.app.game.session

import android.graphics.Bitmap
import com.gameocr.app.data.Settings
import com.gameocr.app.game.core.AdviceContext
import com.gameocr.app.game.core.AdviceEngine
import com.gameocr.app.game.core.AdviceRequest
import com.gameocr.app.game.core.AdviceResult
import com.gameocr.app.game.core.GameModule
import com.gameocr.app.game.core.GameModuleRegistry
import com.gameocr.app.game.core.GameState
import com.gameocr.app.game.integration.toTileTextSpans
import com.gameocr.app.ocr.ModelNotReadyException
import com.gameocr.app.ocr.OcrEngine
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

/** OCR -> 牌局状态 -> 合法动作提示词 -> LLM 建议。 */
@Singleton
class GameTurnCoordinator @Inject constructor(
    private val ocrEngine: OcrEngine,
    private val adviceEngine: AdviceEngine,
    private val modules: GameModuleRegistry,
    private val sessions: GameSessionManager,
) {
    suspend fun analyze(bitmap: Bitmap, settings: Settings): GameTurnOutcome {
        val module = modules.findOrNull(settings.gameModuleId) ?: modules.default
            ?: return GameTurnOutcome.Error("没有可用的牌类模块")
        val zones = settings.gameZonesByModule[module.id].orEmpty()
        if (zones.isEmpty()) {
            return GameTurnOutcome.Error("${module.displayName} 尚未标定区域，请长按悬浮球选择「牌局区域标定」")
        }

        val blocks = try {
            ocrEngine.recognize(bitmap, settings.ocrEngine, settings)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ModelNotReadyException) {
            return GameTurnOutcome.Error(error.message ?: "OCR 模型尚未安装")
        } catch (error: Exception) {
            return GameTurnOutcome.Error("识别失败：${error.message ?: error.javaClass.simpleName}")
        }

        val state = module.recognizer.recognize(
            spans = blocks.toTileTextSpans(),
            zones = zones,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
        ) ?: return GameTurnOutcome.RecognitionFailed(
            module = module,
            message = "没有识别出足够的手牌，请先检查区域标定和画面清晰度。",
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
}
