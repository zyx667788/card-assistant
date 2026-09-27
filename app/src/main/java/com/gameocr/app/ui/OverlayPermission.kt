package com.gameocr.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** 打开本应用的悬浮窗权限设置页；失败时回退到系统通用页。 */
internal fun openOverlayPermissionSettings(context: Context) {
    val appSpecificIntent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(appSpecificIntent) }.isFailure) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
