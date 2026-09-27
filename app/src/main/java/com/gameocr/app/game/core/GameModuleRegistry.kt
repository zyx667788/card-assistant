package com.gameocr.app.game.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 已注册的牌类模块。
 *
 * 模块通过 Hilt 的多绑定（`@IntoSet`）注入，新增玩法不需要改这里。
 */
@Singleton
class GameModuleRegistry @Inject constructor(
    modules: Set<@JvmSuppressWildcards GameModule>,
) {
    private val modulesById: Map<String, GameModule> = modules.associateBy { it.id }

    /** 按模块 id 排序，保证设置页顺序稳定。 */
    val all: List<GameModule> get() = modulesById.values.sortedBy { it.id }

    val default: GameModule? get() = findOrNull(DEFAULT_MODULE_ID) ?: all.firstOrNull()

    fun findOrNull(id: String?): GameModule? = id?.let { modulesById[it] }

    companion object {
        const val DEFAULT_MODULE_ID = "paohuzi"
    }
}
