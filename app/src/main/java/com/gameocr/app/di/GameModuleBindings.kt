package com.gameocr.app.di

import android.content.Context
import com.gameocr.app.game.advice.LlmAdviceEngine
import com.gameocr.app.game.core.AdviceEngine
import com.gameocr.app.game.core.GameModule
import com.gameocr.app.game.paohuzi.PaohuziGameModule
import com.gameocr.app.game.paohuzi.PaohuziPromptPolicy
import com.gameocr.app.game.paohuzi.PaohuziVlmBoardRecognizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * 牌类模块与决策引擎的注入绑定。
 *
 * 新增玩法时在这里多提供一个 `@IntoSet` 的 [GameModule] 即可，其余链路不用改。
 */
@Module
@InstallIn(SingletonComponent::class)
object GameModuleBindings {

    @Provides
    @IntoSet
    fun providePaohuziModule(
        @ApplicationContext context: Context,
        recognizer: PaohuziVlmBoardRecognizer,
    ): GameModule =
        PaohuziGameModule(
            promptPolicy = PaohuziPromptPolicy(
                rulesSummary = readRulesSummary(context, PAOHUZI_RULES_ASSET),
            ),
            recognizer = recognizer,
        )

    @Provides
    @Singleton
    fun provideAdviceEngine(engine: LlmAdviceEngine): AdviceEngine = engine

    private fun readRulesSummary(context: Context, assetPath: String): String =
        runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: FALLBACK_RULES_SUMMARY

    private const val PAOHUZI_RULES_ASSET = "game/paohuzi/rules.md"

    /**
     * assets 缺失时的兜底说明，只写不随地区变化的结构性内容。
     */
    private val FALLBACK_RULES_SUMMARY = """
        全副牌 80 张：小字「一~十」与大字「壹~拾」各 10 种、每种 4 张。
        顺子只在同一门内成立（不跨大小写）；三张相同称坎（碰），四张相同称提。
        胡牌由若干组顺子/坎/提加一对将组成。起胡息数等地区差异请按当地规则另行说明。
    """.trimIndent()
}
