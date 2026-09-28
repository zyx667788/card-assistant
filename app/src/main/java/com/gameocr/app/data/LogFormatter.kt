package com.gameocr.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把日志条目渲染成纯文本，用于复制 / 导出。
 *
 * 导出文本刻意用稳定的英文标记（INFO / WARN / ERROR、CAPTURE / RECOGNITION…），
 * 方便贴进 issue 或聊天里直接搜；界面上的中文标签由 UI 层用字符串资源渲染。
 */
object LogFormatter {

    /** 复制 / 导出的完整文本，按时间从旧到新。 */
    fun format(entries: List<LogRepository.Entry>): String =
        entries.joinToString(separator = "\n") { formatLine(it) }

    fun formatLine(entry: LogRepository.Entry): String = buildString {
        append('[').append(formatTimestamp(entry.timestamp)).append(']')
        append(' ').append(entry.level.name)
        append(' ').append(entry.category.name)
        append(' ').append(entry.message)
        entry.elapsedMs?.let { append(" (").append(it).append("ms)") }
    }

    /** 完整时间戳，导出用。 */
    fun formatTimestamp(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))

    /** 界面上显示的短时间。 */
    fun formatClock(timestamp: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))

    /**
     * 按最低级别筛选；[minimumLevel] 为 null 表示全部。
     *
     * 依赖 [LogRepository.Level] 的声明顺序（INFO < WARN < ERROR）。
     */
    fun filter(
        entries: List<LogRepository.Entry>,
        minimumLevel: LogRepository.Level?,
    ): List<LogRepository.Entry> =
        if (minimumLevel == null) entries
        else entries.filter { it.level.ordinal >= minimumLevel.ordinal }
}
