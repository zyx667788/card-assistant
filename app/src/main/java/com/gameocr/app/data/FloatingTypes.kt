package com.gameocr.app.data

import kotlinx.serialization.Serializable

/**
 * 悬浮球技能。打牌助手只用 GAME_ASSISTANT；其余枚举值保留以兼容
 * overlay/FloatingButtonManager 内部的 when 分支。
 */
enum class FloatingSkill {
    FULL_SCREEN,
    WORD_SELECT,
    LOOP,
    INPUT_TRANSLATE,
    GAME_ASSISTANT,
}

@Serializable
enum class InputTranslationDoubleAction {
    FULL_SCREEN,
    WORD_SELECT,
}

/**
 * 悬浮球弧菜单按钮 ID。在 `overlay/MenuItemRegistry.kt` 集中绑定到图标 / 文案 / 回调。
 * 打牌助手只用其中几个（GAME_ASSISTANT_SKILL / SETTINGS / HOME），其余保留给 registry。
 */
@Serializable
enum class MenuItemId {
    LOOP,
    REGION,
    LANGUAGE_PAIR,
    PRESET_SWITCH,
    SETTINGS,
    HOME,
    FULL_SCREEN_SKILL,
    INPUT_TRANSLATE_SKILL,
    GAME_ASSISTANT_SKILL,
    GAME_REGION,
    GAME_RECOGNIZER_TOGGLE,
}

/** 弧菜单分页 / 顺序常量。 */
object FloatingMenu {
    /** 每页按钮数范围；该数量包含「下一组」翻页键。 */
    const val MIN_PAGE_SIZE: Int = 2
    const val MAX_PAGE_SIZE: Int = 6
    const val DEFAULT_PAGE_SIZE: Int = 5
    /** 旧调用点兼容别名，等同 [DEFAULT_PAGE_SIZE]。 */
    const val PAGE_SIZE: Int = DEFAULT_PAGE_SIZE

    fun coercePageSize(value: Int): Int = value.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)

    /**
     * 打牌助手的弧菜单：点球展开后只有「分析牌局」「设置」「返回主应用」。
     * GAME_ASSISTANT_SKILL 的点击由 CaptureService 接管为触发一次牌局分析。
     */
    val GAME_ASSISTANT_ORDER: List<MenuItemId> = listOf(
        MenuItemId.GAME_ASSISTANT_SKILL,
        MenuItemId.SETTINGS,
        MenuItemId.HOME,
    )
}
