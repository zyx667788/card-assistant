package com.gameocr.app.trigger

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.gameocr.app.service.CaptureService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * 打牌助手的无障碍服务：两件事。
 * 1. 截屏后端之一（Android 12+ 的 takeScreenshot，无需 Shizuku / 投屏授权）。
 * 2. 音量上+下组合长按 → 触发一次牌局分析。
 */
@AndroidEntryPoint
class GameOcrAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var volumeUpDown = false
    private var volumeDownDown = false
    private var comboLatched = false

    private val triggerRunnable = Runnable {
        if (comboLatched) {
            Timber.i("A11y combo fired: vol+ vol- long-press ${COMBO_HOLD_MS}ms")
            triggerCapture()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedInstance = this
        connection.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 打牌助手不需要处理无障碍事件。
    }

    override fun onInterrupt() {
        mainHandler.removeCallbacks(triggerRunnable)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isVolUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (!isVolUp && !isVolDown) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    if (isVolUp) volumeUpDown = true
                    if (isVolDown) volumeDownDown = true
                    if (!comboLatched && volumeUpDown && volumeDownDown) {
                        comboLatched = true
                        mainHandler.removeCallbacks(triggerRunnable)
                        mainHandler.postDelayed(triggerRunnable, COMBO_HOLD_MS)
                    }
                }
                return comboLatched
            }
            KeyEvent.ACTION_UP -> {
                if (isVolUp) volumeUpDown = false
                if (isVolDown) volumeDownDown = false
                val wasLatched = comboLatched
                if (!volumeUpDown && !volumeDownDown) {
                    comboLatched = false
                    mainHandler.removeCallbacks(triggerRunnable)
                }
                return wasLatched
            }
        }
        return false
    }

    private fun triggerCapture() {
        val svc = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_TRIGGER_ONCE
        }
        startService(svc)
    }

    override fun onDestroy() {
        if (connectedInstance === this) {
            connectedInstance = null
            connection.value = false
        }
        mainHandler.removeCallbacks(triggerRunnable)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val COMBO_HOLD_MS = 300L

        @Volatile
        private var connectedInstance: GameOcrAccessibilityService? = null
        private val connection = MutableStateFlow(false)
        val connected = connection.asStateFlow()

        fun isConnected(): Boolean = connectedInstance != null

        fun isScreenshotReady(): Boolean = screenshotServiceOrNull() != null

        fun screenshotServiceOrNull(): GameOcrAccessibilityService? {
            if (Build.VERSION.SDK_INT < 30) return null
            return connectedInstance?.takeIf {
                (it.serviceInfo?.capabilities ?: 0) and
                    AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT != 0
            }
        }
    }
}
