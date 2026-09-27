package com.gameocr.app.game.vlm

import com.gameocr.app.game.core.BoardRecognizer
import com.gameocr.app.game.core.BoardZone
import com.gameocr.app.game.core.GameModule
import com.gameocr.app.game.core.PromptPolicy

/**
 * 纯提示词模式的牌类模块。
 *
 * 与跑胡子模块的区别：识别与决策都只靠提示词区分，没有本地规则引擎、
 * 不需要区域标定（VLM 直接看整屏）。新增玩法时准备三份提示词文件
 *（eyes / advisor / rules），在 [com.gameocr.app.di.GameModuleBindings]
 * 里多注册一个 `@IntoSet` 的模块即可。
 */
class VlmPromptGameModule(
    override val id: String,
    override val displayName: String,
    override val promptPolicy: PromptPolicy,
    override val recognizer: BoardRecognizer,
    override val defaultZones: List<BoardZone> = emptyList(),
) : GameModule
