package com.gameocr.app.di

import android.content.Context
import com.gameocr.app.game.advice.LlmAdviceEngine
import com.gameocr.app.game.core.AdviceEngine
import com.gameocr.app.game.core.GameModule
import com.gameocr.app.game.doudizhu.DoudizhuGameModule
import com.gameocr.app.game.doudizhu.DoudizhuPromptPolicy
import com.gameocr.app.game.doudizhu.DoudizhuVlmBoardRecognizer
import com.gameocr.app.game.paohuzi.PaohuziGameModule
import com.gameocr.app.game.paohuzi.PaohuziPromptPolicy
import com.gameocr.app.game.paohuzi.PaohuziVlmBoardRecognizer
import com.gameocr.app.data.SettingsRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
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

    /**
     * 斗地主模块：逐张带花色的结构化识别 + 本地校验 + 本地规则算合法动作。
     * eyes 看牌口径、advisor 决策口径、rules 规则说明都在 `assets/game/doudizhu/` 里。
     */
    @Provides
    @IntoSet
    fun provideDoudizhuModule(
        @ApplicationContext context: Context,
        client: OkHttpClient,
        json: Json,
        settingsRepository: SettingsRepository,
    ): GameModule {
        val rulesSummary = readAssetText(context, DOUDIZHU_RULES_ASSET)
            .ifBlank { FALLBACK_DOUDIZHU_RULES }
        val eyesSystem = readAssetText(context, DOUDIZHU_EYES_ASSET)
            .ifBlank { FALLBACK_DOUDIZHU_EYES }
        val advisorTemplate = readAssetText(context, DOUDIZHU_ADVISOR_ASSET)
            .ifBlank { FALLBACK_DOUDIZHU_ADVISOR }
        return DoudizhuGameModule(
            promptPolicy = DoudizhuPromptPolicy(
                rulesSummary = rulesSummary,
                advisorSystemPrompt = advisorTemplate.replace(RULES_PLACEHOLDER, rulesSummary),
            ),
            recognizer = DoudizhuVlmBoardRecognizer(
                eyesSystemPrompt = eyesSystem,
                eyesUserPrompt = FALLBACK_DOUDIZHU_EYES_USER,
                client = client,
                json = json,
                settingsRepository = settingsRepository,
            ),
        )
    }

    private fun readRulesSummary(context: Context, assetPath: String): String =
        runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: FALLBACK_RULES_SUMMARY

    private fun readAssetText(context: Context, assetPath: String): String =
        runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrNull()?.trim().orEmpty()

    private const val PAOHUZI_RULES_ASSET = "game/paohuzi/rules.md"
    private const val DOUDIZHU_EYES_ASSET = "game/doudizhu/eyes.md"
    private const val DOUDIZHU_ADVISOR_ASSET = "game/doudizhu/advisor.md"
    private const val DOUDIZHU_RULES_ASSET = "game/doudizhu/rules.md"
    private const val RULES_PLACEHOLDER = "{{RULES}}"

    /**
     * assets 缺失时的兜底说明，只写不随地区变化的结构性内容。
     */
    private val FALLBACK_RULES_SUMMARY = """
        全副牌 80 张：小字「一~十」与大字「壹~拾」各 10 种、每种 4 张。
        顺子只在同一门内成立（不跨大小写）；三张相同称坎（碰），四张相同称提。
        胡牌由若干组顺子/坎/提加一对将组成。起胡息数等地区差异请按当地规则另行说明。
    """.trimIndent()

    /** 斗地主 assets 缺失时的兜底提示词，保证模块始终可用。 */
    private val FALLBACK_DOUDIZHU_RULES = """
        一副 54 张牌，留 3 张底牌，其余 17 张/人。地主拿到底牌先出，对抗两名农民。
        牌型：单张、对子、三张、三带一/二、顺子、连对、飞机、炸弹（四张同点）、火箭（双王最大）。
        大小：3 最小……A、2、小王、大王依次增大；炸弹大于一切非炸弹，火箭最大。
        任意一方先出完手牌即获胜。
    """.trimIndent()

    private const val FALLBACK_DOUDIZHU_EYES =
        "你是斗地主牌局识别器。只输出 JSON，字段为 hand/role/bottom_cards/last_play_seat/last_play/played_cards/my_remaining/up_remaining/down_remaining/action_hint/global_observation。" +
            "hand 必须逐张列出并带花色（如 黑桃7、红桃7），同一张牌不能重复；看不清的不要编造。"

    private const val FALLBACK_DOUDIZHU_EYES_USER = "识别这张斗地主牌局截图，按 system 要求的 JSON 格式输出。"

    private val FALLBACK_DOUDIZHU_ADVISOR = """
        你是一名斗地主陪练助手。依据用户消息里的牌局信息，给出下一步怎么出；信息不足时在理由里说明，不要编造没给出的牌。

        【规则要点】
        $RULES_PLACEHOLDER

        【输出格式】
        只输出一个 JSON 对象，不要输出解释性文字，也不要包在 Markdown 代码块里：
        {"action":"play|pass","tile":"要出的牌，用空格分隔；pass 时填空字符串","reason":"一句话理由，40 字以内","alternatives":["备选打法，最多 2 条"]}
    """.trimIndent()
}
