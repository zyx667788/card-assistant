package com.gameocr.app.game.paohuzi

import com.gameocr.app.game.core.BoardZone
import com.gameocr.app.game.core.BoardZoneRole
import com.gameocr.app.game.core.BoardRecognizer
import com.gameocr.app.game.core.GameModule
import com.gameocr.app.game.core.NormalizedRect
import com.gameocr.app.game.core.PromptPolicy

/**
 * 湖南跑胡子模块。
 *
 * [rulesSummary] 由调用方从 assets 读取后传入，方便不改代码就调整规则说明与提示词口径。
 */
class PaohuziGameModule(
    override val promptPolicy: PromptPolicy,
    override val recognizer: BoardRecognizer = PaohuziBoardRecognizer(),
    override val defaultZones: List<BoardZone> = DEFAULT_ZONES,
) : GameModule {

    override val id: String = PAOHUZI_MODULE_ID

    override val displayName: String = "湖南跑胡子"

    companion object {
        /**
         * 竖屏手机的初始布局。只是第一次使用时的起点，
         * 用户会在自己手机上重新框选各区域，实际坐标以标定结果为准。
         */
        val DEFAULT_ZONES: List<BoardZone> = listOf(
            BoardZone(
                id = "self_hand",
                label = "我的手牌",
                role = BoardZoneRole.SELF_HAND,
                rect = NormalizedRect(left = 0.04f, top = 0.80f, right = 0.96f, bottom = 0.90f),
            ),
            BoardZone(
                id = "self_melds",
                label = "我亮出的组合",
                role = BoardZoneRole.SELF_MELDS,
                rect = NormalizedRect(left = 0.04f, top = 0.70f, right = 0.96f, bottom = 0.79f),
            ),
            BoardZone(
                id = "table_discards",
                label = "桌面已出的牌",
                role = BoardZoneRole.TABLE_DISCARDS,
                rect = NormalizedRect(left = 0.12f, top = 0.28f, right = 0.88f, bottom = 0.60f),
            ),
            BoardZone(
                id = "action_prompt",
                label = "当前操作提示",
                role = BoardZoneRole.ACTION_PROMPT,
                rect = NormalizedRect(left = 0.20f, top = 0.62f, right = 0.80f, bottom = 0.72f),
            ),
            BoardZone(
                id = "opponent_left",
                label = "左家亮牌",
                role = BoardZoneRole.OPPONENT_LEFT_MELDS,
                rect = NormalizedRect(left = 0.00f, top = 0.20f, right = 0.16f, bottom = 0.70f),
            ),
            BoardZone(
                id = "opponent_right",
                label = "右家亮牌",
                role = BoardZoneRole.OPPONENT_RIGHT_MELDS,
                rect = NormalizedRect(left = 0.84f, top = 0.20f, right = 1.00f, bottom = 0.70f),
            ),
            BoardZone(
                id = "remaining_count",
                label = "剩余牌数",
                role = BoardZoneRole.REMAINING_COUNT,
                rect = NormalizedRect(left = 0.40f, top = 0.18f, right = 0.60f, bottom = 0.26f),
            ),
        )
    }
}
