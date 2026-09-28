package com.gameocr.app.overlay

import android.annotation.SuppressLint
import android.os.Build
import android.view.WindowManager

/**
 * 悬浮窗的 `layoutInDisplayCutoutMode`。
 *
 * `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` 是 API 30 才加入的取值；API 28 / 29 上
 * 该字段只接受 DEFAULT / SHORT_EDGES / NEVER，传 3 越界。低版本退回 SHORT_EDGES
 * ——同样允许窗口延伸到短边 cutout，悬浮球仍能贴到屏幕物理边。
 *
 * `layoutInDisplayCutoutMode` 字段本身是 API 28 加入的，调用方必须先判断
 * `SDK_INT >= P` 再赋值，这里不负责那次判断。
 */
// 两个常量都是编译期内联的 static final int，不会在低版本上触发字段查找；
// 运行时风险只有「传入平台不认识的取值」，由调用方的 SDK_INT 判断拦住。
@SuppressLint("InlinedApi")
internal fun floatingWindowCutoutMode(sdkInt: Int): Int =
    if (sdkInt >= Build.VERSION_CODES.R) {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
