package com.gameocr.app.game.paohuzi

import com.gameocr.app.game.core.BoardRecognizer
import com.gameocr.app.game.core.GameModule
import com.gameocr.app.game.core.PromptPolicy

/**
 * 湖南跑胡子模块。
 *
 * [rulesSummary] 由调用方从 assets 读取后传入，方便不改代码就调整规则说明与提示词口径。
 * [recognizer] 由 DI 传入 VLM 识别器：整屏截图直发云端视觉模型，不再依赖本地 OCR 与区域标定。
 */
class PaohuziGameModule(
    override val promptPolicy: PromptPolicy,
    override val recognizer: BoardRecognizer,
) : GameModule {

    override val id: String = PAOHUZI_MODULE_ID

    override val displayName: String = "湖南跑胡子"
}
