package com.gameocr.app.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gameocr.app.data.AppLocalePrefs
import com.gameocr.app.data.ThemeModePrefs
import com.gameocr.app.ui.theme.GameOcrTheme
import com.gameocr.app.ui.theme.LocalThemeMode
import com.gameocr.app.ui.theme.ThemeModeController
import dagger.hilt.android.AndroidEntryPoint

/** 打牌助手主入口：单页，服务开关 + API 配置 + 使用说明。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocalePrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableIntStateOf(ThemeModePrefs.read(this)) }
            val controller = ThemeModeController(
                mode = themeMode,
                setMode = { newMode ->
                    themeMode = newMode
                    ThemeModePrefs.write(this, newMode)
                }
            )
            CompositionLocalProvider(LocalThemeMode provides controller) {
                GameOcrTheme(themeMode = themeMode) {
                    MainScreen()
                }
            }
        }
    }
}
