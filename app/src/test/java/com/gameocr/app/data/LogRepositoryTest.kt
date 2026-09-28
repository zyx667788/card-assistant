package com.gameocr.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRepositoryTest {

    private val logs = LogRepository()

    @Test
    fun `keeps newest entries up to capacity`() {
        repeat(LogRepository.CAPACITY + 5) { index ->
            logs.info(LogRepository.Category.CAPTURE, "message-$index")
        }

        val entries = logs.entries.value
        assertEquals(LogRepository.CAPACITY, entries.size)
        assertEquals("最旧的 5 条应被丢掉", "message-5", entries.first().message)
        assertEquals("message-${LogRepository.CAPACITY + 4}", entries.last().message)
    }

    @Test
    fun `clear removes everything`() {
        logs.info(LogRepository.Category.RECOGNITION, "hello")
        logs.clear()
        assertTrue(logs.entries.value.isEmpty())
    }

    @Test
    fun `error keeps the throwable class and message`() {
        logs.error(LogRepository.Category.ADVICE, "调用模型失败", IllegalStateException("boom"))

        val entry = logs.entries.value.single()
        assertEquals(LogRepository.Level.ERROR, entry.level)
        assertTrue(entry.message.contains("调用模型失败"))
        assertTrue(entry.message.contains("IllegalStateException"))
        assertTrue(entry.message.contains("boom"))
    }

    @Test
    fun `elapsed time is kept on the entry`() {
        logs.info(LogRepository.Category.RECOGNITION, "识别成功", elapsedMs = 1234)
        assertEquals(1234L, logs.entries.value.single().elapsedMs)
    }

    @Test
    fun `ids increase so the list has stable keys`() {
        repeat(3) { logs.warn(LogRepository.Category.CAPTURE, "w$it") }
        val ids = logs.entries.value.map { it.id }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.distinct().size, ids.size)
    }
}
