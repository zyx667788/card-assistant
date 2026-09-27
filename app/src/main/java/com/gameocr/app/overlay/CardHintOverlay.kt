package com.gameocr.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import timber.log.Timber

/**
 * 打牌助手的轻量提示条：loading 转圈 / 错误红条 / 信息蓝条。
 *
 * 直接走 WindowManager 加 TYPE_APPLICATION_OVERLAY 悬浮窗——国产 ROM 对后台
 * Service 的 Toast 会静默丢弃，全屏游戏沉浸模式下也只有 overlay 可靠。
 * 截图前由调用方负责 dismiss，避免提示条被截进牌面。
 */
class CardHintOverlay(context: Context) {
    private val appContext = context.applicationContext
    private val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var loadingView: View? = null
    private var hintView: View? = null

    private val overlayType: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun newLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

    /** 顶部 loading 转圈。截图流程开始时调用。 */
    fun showLoadingHint() {
        dismissLoading()
        val density = appContext.resources.displayMetrics.density
        val size = (40 * density).toInt()
        val pad = (8 * density).toInt()
        val container = FrameLayout(appContext).apply {
            background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(0xC0000000.toInt())
            }
            setPadding(pad, pad, pad, pad)
        }
        val pb = android.widget.ProgressBar(appContext).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
        }
        container.addView(pb, FrameLayout.LayoutParams(size, size))
        val params = newLayoutParams().apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (24 * density).toInt()
        }
        runCatching {
            wm.addView(container, params)
            loadingView = container
        }.onFailure {
            Timber.w(it, "Failed to show loading overlay")
        }
    }

    fun dismissLoading() {
        loadingView?.let { runCatching { wm.removeView(it) } }
        loadingView = null
    }

    /** 红底错误条，点击或超时自动关闭。 */
    fun showErrorHint(message: String, durationMs: Long = 4500L) {
        showHint(message, bgColor = 0xF0B71C1C.toInt(), durationMs = durationMs)
    }

    /** 深底信息条，点击或超时自动关闭。 */
    fun showInfoHint(message: String, durationMs: Long = 1800L) {
        showHint(message, bgColor = 0xE6303030.toInt(), durationMs = durationMs)
    }

    private fun showHint(message: String, bgColor: Int, durationMs: Long) {
        hintView?.let { runCatching { wm.removeView(it) } }
        hintView = null

        val density = appContext.resources.displayMetrics.density
        val padH = (16 * density).toInt()
        val padV = (12 * density).toInt()
        val maxW = (appContext.resources.displayMetrics.widthPixels * 0.92f).toInt()

        val tv = TextView(appContext).apply {
            text = message
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 4
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = maxW
        }
        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(bgColor)
            }
            setPadding(padH, padV, padH, padV)
            addView(tv)
        }
        val params = newLayoutParams().apply {
            // 屏幕下方 1/4 处，避开 loading 圈（顶部）与导航栏
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (96 * density).toInt()
        }
        runCatching { wm.addView(container, params) }
        hintView = container

        container.setOnClickListener {
            if (hintView === container) {
                runCatching { wm.removeView(container) }
                hintView = null
            }
        }
        container.postDelayed({
            if (hintView === container) {
                runCatching { wm.removeView(container) }
                hintView = null
            }
        }, durationMs)
    }

    /** 截图前清掉所有提示，保证截到的是干净牌面。 */
    fun clearForCapture() {
        dismissLoading()
        hintView?.let { runCatching { wm.removeView(it) } }
        hintView = null
    }

    fun clear() = clearForCapture()
}
