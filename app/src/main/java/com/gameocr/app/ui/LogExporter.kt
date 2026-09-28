package com.gameocr.app.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.gameocr.app.data.LogFormatter
import com.gameocr.app.data.LogRepository
import java.io.File

/**
 * 把运行日志导出成 txt 并生成分享 Intent。
 *
 * 复用 manifest 里已注册的 FileProvider（authority = `<applicationId>.logfileprovider`，
 * 路径 = `cache/log_exports/`），所以不需要额外的读取权限。
 */
object LogExporter {

    private const val EXPORT_DIR = "log_exports"

    fun export(context: Context, entries: List<LogRepository.Entry>): File? = runCatching {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        File(dir, "card-assistant-log-${System.currentTimeMillis()}.txt")
            .apply { writeText(LogFormatter.format(entries)) }
    }.getOrNull()

    fun shareIntent(
        context: Context,
        entries: List<LogRepository.Entry>,
        subject: String,
    ): Intent? {
        val file = export(context, entries) ?: return null
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.logfileprovider", file)
        }.getOrNull() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
