package com.gameocr.app.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.gameocr.app.data.SettingsRepository
import com.gameocr.app.game.core.BoardZone
import com.gameocr.app.game.core.GameModule
import com.gameocr.app.game.core.GameModuleRegistry
import com.gameocr.app.game.core.NormalizedRect
import com.gameocr.app.util.physicalDisplaySize
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 按牌类模块依次标定 7 个语义区域。
 *
 * 每个区域确认后只写进内存，最后一步完成后一次性持久化，避免用户中途取消留下半套坐标。
 */
@AndroidEntryPoint
class GameRegionPickerActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.gameocr.app.data.AppLocalePrefs.wrap(newBase))
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var gameModules: GameModuleRegistry

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var module: GameModule
    private var zones: List<BoardZone> = emptyList()
    private var stepIndex = 0
    private var hadSavedZones = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureFullscreenWindow()

        val settings = runBlocking { settingsRepository.get() }
        val moduleId = intent.getStringExtra(EXTRA_MODULE_ID) ?: settings.gameModuleId
        module = gameModules.findOrNull(moduleId)
            ?: gameModules.default
            ?: run { finish(); return }
        val saved = settings.gameZonesByModule[module.id].orEmpty()
        hadSavedZones = saved.isNotEmpty()
        zones = mergeZones(module.defaultZones, saved)
        if (zones.isEmpty()) {
            finish()
            return
        }
        renderStep()
    }

    private fun configureFullscreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    private fun renderStep() {
        if (stepIndex >= zones.size) {
            saveAndFinish()
            return
        }
        val current = zones[stepIndex]
        val screen = physicalDisplaySize(this)
        val initial = if (hadSavedZones) {
            current.rect.toPixelRect(screen.width, screen.height)
        } else {
            null
        }
        val picker = RegionPickerView(
            context = this,
            initial = initial,
            onCancel = { finish() },
            onClearAllRequested = null,
        )
        val prompt = TextView(this).apply {
            text = getString(
                com.gameocr.app.R.string.game_region_step_format,
                stepIndex + 1,
                zones.size,
                current.label,
            )
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xDD111827.toInt())
            }
        }
        val hint = TextView(this).apply {
            text = getString(com.gameocr.app.R.string.game_region_step_hint)
            setTextColor(0xFFE0E0E0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(16), dp(4), dp(16), dp(10))
        }
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xCC222222.toInt())
            }
            addView(button(com.gameocr.app.R.string.region_picker_btn_redo) { picker.resetToDrawing() })
            addView(button(com.gameocr.app.R.string.region_picker_btn_cancel) { finish() })
            addView(
                button(
                    if (stepIndex == zones.lastIndex) {
                        com.gameocr.app.R.string.game_region_btn_finish
                    } else {
                        com.gameocr.app.R.string.game_region_btn_next
                    },
                    primary = true,
                ) {
                    val rect = picker.currentRect()
                    if (rect == null || rect.width() < MIN_SIDE_PX || rect.height() < MIN_SIDE_PX) {
                        return@button
                    }
                    val width = picker.width.takeIf { it > 0 } ?: screen.width
                    val height = picker.height.takeIf { it > 0 } ?: screen.height
                    zones = zones.toMutableList().also { mutable ->
                        mutable[stepIndex] = current.copy(
                            rect = normalizedRect(rect, width, height),
                        )
                    }
                    stepIndex += 1
                    renderStep()
                },
            )
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(prompt)
            addView(hint)
        }
        val container = FrameLayout(this).apply {
            addView(picker, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            addView(topBar, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(34)
            })
            addView(actionRow, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(28)
            })
        }
        setContentView(container)
    }

    private fun saveAndFinish() {
        scope.launch {
            settingsRepository.update {
                it.copy(
                    gameModuleId = module.id,
                    gameZonesByModule = it.gameZonesByModule + (module.id to zones),
                )
            }
            finish()
        }
    }

    private fun button(
        textRes: Int,
        primary: Boolean = false,
        onClick: () -> Unit,
    ): Button = Button(this).apply {
        setText(textRes)
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(if (primary) 0xFF1976D2.toInt() else 0xFF424242.toInt())
        }
        minWidth = dp(76)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = dp(4); marginEnd = dp(4) }
    }

    private fun mergeZones(defaults: List<BoardZone>, saved: List<BoardZone>): List<BoardZone> {
        if (saved.isEmpty()) return defaults
        val savedById = saved.associateBy { it.id }
        return defaults.map { default -> savedById[default.id] ?: default }
    }

    private fun normalizedRect(rect: Rect, width: Int, height: Int): NormalizedRect {
        val safeWidth = width.coerceAtLeast(1).toFloat()
        val safeHeight = height.coerceAtLeast(1).toFloat()
        return NormalizedRect(
            left = (rect.left / safeWidth).coerceIn(0f, 1f),
            top = (rect.top / safeHeight).coerceIn(0f, 1f),
            right = (rect.right / safeWidth).coerceIn(0f, 1f),
            bottom = (rect.bottom / safeHeight).coerceIn(0f, 1f),
        )
    }

    private fun NormalizedRect.toPixelRect(width: Int, height: Int): Rect = Rect(
        (left * width).toInt(),
        (top * height).toInt(),
        (right * width).toInt(),
        (bottom * height).toInt(),
    )

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_MODULE_ID = "extra_game_module_id"
        private const val MIN_SIDE_PX = 20

        fun newIntent(context: Context, moduleId: String?): Intent =
            Intent(context, GameRegionPickerActivity::class.java)
                .putExtra(EXTRA_MODULE_ID, moduleId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
