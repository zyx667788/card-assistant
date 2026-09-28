package com.gameocr.app.game.doudizhu

import com.gameocr.app.game.core.BoardRecognizer
import com.gameocr.app.game.core.GameModule
import com.gameocr.app.game.core.PromptPolicy

/**
 * 斗地主模块。
 *
 * [promptPolicy] 的规则与决策口径来自 `assets/game/doudizhu/`，改提示词不用改代码；
 * [recognizer] 是结构化 VLM 识别器：逐张带花色输出，再由本地校验复核张数与重复。
 */
class DoudizhuGameModule(
    override val promptPolicy: PromptPolicy,
    override val recognizer: BoardRecognizer,
) : GameModule {

    override val id: String = DOUDIZHU_MODULE_ID

    override val displayName: String = "斗地主"
}
