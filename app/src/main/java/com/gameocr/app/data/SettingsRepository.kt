package com.gameocr.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("game_ocr_settings")

/**
 * 打牌助手设置的 DataStore 持久化。只保留打牌助手需要的字段；
 * 旧版本残留的翻译/OCR/TTS 相关 key 会被直接忽略（读不到就用默认值）。
 */
@Singleton
class SettingsRepository internal constructor(
    private val context: Context,
    secretCipher: SettingsSecretCipher,
    private val settingsStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context, secretCipher: SettingsSecretCipher) :
        this(context, secretCipher, context.dataStore)

    private val secretCodec = SettingsSecretCodec(secretCipher)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private object Keys {
        val ApiKey = stringPreferencesKey("api_key")
        val BaseUrl = stringPreferencesKey("base_url")
        val Model = stringPreferencesKey("model")
        val ApiTimeoutSeconds = intPreferencesKey("api_timeout_seconds")
        val GameModuleId = stringPreferencesKey("game_module_id")
        val GameSessionHistory = stringPreferencesKey("game_session_history_json")
        val FloatingButtonSizeDp = intPreferencesKey("floating_button_size_dp")
        val FloatingButtonAlpha = floatPreferencesKey("floating_button_alpha")
        val FloatingButtonX = intPreferencesKey("floating_button_x")
        val FloatingButtonY = intPreferencesKey("floating_button_y")
        val FloatingButtonSnapToEdge = booleanPreferencesKey("floating_button_snap_to_edge")
        val FloatingButtonAutoDock = booleanPreferencesKey("floating_button_auto_dock")
        val FloatingButtonDockInsetDp = intPreferencesKey("floating_button_dock_inset_dp")
        val OverlayTextSizeSp = intPreferencesKey("overlay_text_size_sp")
        val OverlayAlpha = floatPreferencesKey("overlay_alpha")
        val OverlayTheme = stringPreferencesKey("overlay_theme")
        val OverlayTextStyle = stringPreferencesKey("overlay_text_style_json")
        val OverlayFontFileName = stringPreferencesKey("overlay_font_file_name")
        val CustomBgColor = intPreferencesKey("custom_bg_color")
        val CustomFgColor = intPreferencesKey("custom_fg_color")
        val CustomBorderColor = intPreferencesKey("custom_border_color")
        val CustomBorderWidth = intPreferencesKey("custom_border_width")
        val CustomBorderStyle = stringPreferencesKey("custom_border_style")
    }

    /** API Key 等凭据走加密存储。 */
    private val credentialStringKeys = setOf(Keys.ApiKey)
    private val secureStringKeys = setOf(Keys.ApiKey, Keys.BaseUrl)

    val settings: Flow<Settings> = settingsStore.data
        .map { prefs -> prefs.toSettings() }
        .flowOn(Dispatchers.IO)

    suspend fun get(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        val current = get()
        val next = transform(current)
        settingsStore.edit { prefs -> prefs.writeSettings(next) }
    }

    /** 把内存里的 API Key 等凭据迁移为加密存储（幂等）。 */
    suspend fun migratePlaintextSecretsIfNeeded(): Int {
        var migrated = 0
        settingsStore.edit { prefs ->
            secureStringKeys.forEach { key ->
                val raw = prefs[key]
                if (secretCodec.needsMigration(raw)) {
                    prefs.putSecure(key, raw.orEmpty())
                    migrated++
                }
            }
        }
        return migrated
    }

    private fun MutablePreferences.putSecure(key: Preferences.Key<String>, value: String) {
        this[key] = if (key in credentialStringKeys) secretCodec.encryptCredential(value)
            else secretCodec.encryptPlainText(value)
    }

    private fun MutablePreferences.writeSettings(s: Settings) {
        putSecure(Keys.ApiKey, s.apiKey)
        putSecure(Keys.BaseUrl, s.baseUrl)
        this[Keys.Model] = s.model
        this[Keys.ApiTimeoutSeconds] = s.apiTimeoutSeconds
        this[Keys.GameModuleId] = s.gameModuleId
        this[Keys.GameSessionHistory] = json.encodeToString(
            MapSerializer(String.serializer(), ListSerializer(String.serializer())),
            s.gameSessionHistoryByModule,
        )
        this[Keys.FloatingButtonSizeDp] = s.floatingButtonSizeDp
        this[Keys.FloatingButtonAlpha] = s.floatingButtonAlpha
        this[Keys.FloatingButtonX] = s.floatingButtonX
        this[Keys.FloatingButtonY] = s.floatingButtonY
        this[Keys.FloatingButtonSnapToEdge] = s.floatingButtonSnapToEdge
        this[Keys.FloatingButtonAutoDock] = s.floatingButtonAutoDock
        this[Keys.FloatingButtonDockInsetDp] = s.floatingButtonDockInsetDp
        this[Keys.OverlayTextSizeSp] = s.overlayTextSizeSp
        this[Keys.OverlayAlpha] = s.overlayAlpha
        this[Keys.OverlayTheme] = s.overlayTheme.name
        this[Keys.OverlayTextStyle] = json.encodeToString(
            OverlayTextStyle.serializer(), s.overlayTextStyle.normalized(),
        )
        this[Keys.OverlayFontFileName] = s.overlayFontFileName
        this[Keys.CustomBgColor] = s.customBgColor
        this[Keys.CustomFgColor] = s.customFgColor
        this[Keys.CustomBorderColor] = s.customBorderColor
        this[Keys.CustomBorderWidth] = s.customBorderWidth
        this[Keys.CustomBorderStyle] = s.customBorderStyle.name
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        fun secure(key: Preferences.Key<String>): String? =
            this[key]?.let(secretCodec::decodeStored)
        return d.copy(
            apiKey = secure(Keys.ApiKey) ?: d.apiKey,
            baseUrl = secure(Keys.BaseUrl) ?: d.baseUrl,
            model = this[Keys.Model] ?: d.model,
            apiTimeoutSeconds = this[Keys.ApiTimeoutSeconds] ?: d.apiTimeoutSeconds,
            gameModuleId = this[Keys.GameModuleId] ?: d.gameModuleId,
            gameSessionHistoryByModule = this[Keys.GameSessionHistory]
                ?.let { raw ->
                    runCatching {
                        json.decodeFromString(
                            MapSerializer(String.serializer(), ListSerializer(String.serializer())),
                            raw,
                        )
                    }.getOrNull()
                } ?: d.gameSessionHistoryByModule,
            floatingButtonSizeDp = this[Keys.FloatingButtonSizeDp] ?: d.floatingButtonSizeDp,
            floatingButtonAlpha = this[Keys.FloatingButtonAlpha] ?: d.floatingButtonAlpha,
            floatingButtonX = this[Keys.FloatingButtonX] ?: d.floatingButtonX,
            floatingButtonY = this[Keys.FloatingButtonY] ?: d.floatingButtonY,
            floatingButtonSnapToEdge = this[Keys.FloatingButtonSnapToEdge] ?: d.floatingButtonSnapToEdge,
            floatingButtonAutoDock = this[Keys.FloatingButtonAutoDock] ?: d.floatingButtonAutoDock,
            floatingButtonDockInsetDp = this[Keys.FloatingButtonDockInsetDp] ?: d.floatingButtonDockInsetDp,
            overlayTextSizeSp = this[Keys.OverlayTextSizeSp] ?: d.overlayTextSizeSp,
            overlayAlpha = this[Keys.OverlayAlpha] ?: d.overlayAlpha,
            overlayTheme = this[Keys.OverlayTheme]?.let {
                runCatching { OverlayTheme.valueOf(it) }.getOrNull()
            } ?: d.overlayTheme,
            overlayTextStyle = this[Keys.OverlayTextStyle]
                ?.let { raw ->
                    runCatching {
                        json.decodeFromString(OverlayTextStyle.serializer(), raw)
                    }.getOrNull()
                }?.normalized() ?: d.overlayTextStyle,
            overlayFontFileName = this[Keys.OverlayFontFileName] ?: d.overlayFontFileName,
            customBgColor = this[Keys.CustomBgColor] ?: d.customBgColor,
            customFgColor = this[Keys.CustomFgColor] ?: d.customFgColor,
            customBorderColor = this[Keys.CustomBorderColor] ?: d.customBorderColor,
            customBorderWidth = this[Keys.CustomBorderWidth] ?: d.customBorderWidth,
            customBorderStyle = this[Keys.CustomBorderStyle]?.let {
                runCatching { BorderStyle.valueOf(it) }.getOrNull()
            } ?: d.customBorderStyle,
        )
    }
}
