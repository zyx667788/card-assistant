package com.gameocr.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFormatterTest {

    private var nextId = 1L

    private fun entry(
        level: LogRepository.Level,
        category: LogRepository.Category = LogRepository.Category.RECOGNITION,
        message: String = "识别成功",
        elapsedMs: Long? = null,
    ) = LogRepository.Entry(
        id = nextId++,
        timestamp = 1_790_000_000_000L,
        level = level,
        category = category,
        message = message,
        elapsedMs = elapsedMs,
    )

    @Test
    fun `format line carries level category message and elapsed`() {
        val line = LogFormatter.formatLine(
            entry(
                level = LogRepository.Level.WARN,
                category = LogRepository.Category.CAPTURE,
                message = "截图失败",
                elapsedMs = 1234,
            ),
        )
        assertTrue(line.startsWith("["))
        assertTrue(line.contains("WARN"))
        assertTrue(line.contains("CAPTURE"))
        assertTrue(line.contains("截图失败"))
        assertTrue(line.contains("(1234ms)"))
    }

    @Test
    fun `format keeps one line per entry`() {
        val text = LogFormatter.format(
            listOf(
                entry(level = LogRepository.Level.INFO, message = "第一条"),
                entry(level = LogRepository.Level.ERROR, message = "第二条"),
            ),
        )
        assertEquals(2, text.lines().size)
        assertTrue(text.lines()[1].contains("ERROR"))
    }

    @Test
    fun `filter keeps entries at or above the given level`() {
        val entries = listOf(
            entry(level = LogRepository.Level.INFO, message = "info"),
            entry(level = LogRepository.Level.WARN, message = "warn"),
            entry(level = LogRepository.Level.ERROR, message = "error"),
        )

        assertEquals(3, LogFormatter.filter(entries, null).size)
        assertEquals(2, LogFormatter.filter(entries, LogRepository.Level.WARN).size)
        assertEquals(1, LogFormatter.filter(entries, LogRepository.Level.ERROR).size)
        assertEquals(
            "error",
            LogFormatter.filter(entries, LogRepository.Level.ERROR).single().message,
        )
    }

    @Test
    fun `clock format is short and fixed width`() {
        val clock = LogFormatter.formatClock(1_790_000_000_000L)
        assertEquals(8, clock.length)
        assertEquals(2, clock.count { it == ':' })
    }
}
