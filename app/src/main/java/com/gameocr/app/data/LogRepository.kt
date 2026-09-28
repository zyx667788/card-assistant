package com.gameocr.app.data

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App 内可查看的运行日志。
 *
 * 区别于 [timber.log.Timber]（只写 logcat，用户看不到），这里的日志会在「运行日志」页里
 * 展示，也能复制 / 导出，方便在手机上直接排查「这次为什么没识别出来」。
 *
 * 设计取舍：
 * - **仅内存**：不落盘，App 重启清空。识别结果含牌局信息，落盘有隐私风险。
 * - **环形缓冲 [CAPACITY]**：超过容量丢最旧的，避免长时间运行占用内存。
 * - **StateFlow**：UI 订阅后每加一条自动刷新。
 */
@Singleton
class LogRepository @Inject constructor() {

    /** 日志级别，声明顺序即严重程度（INFO < WARN < ERROR）。 */
    enum class Level { INFO, WARN, ERROR }

    /** 日志来源，对应应用里的几条主链路。 */
    enum class Category { CAPTURE, RECOGNITION, ADVICE, CRASH }

    data class Entry(
        /** 全局递增 id：同毫秒并发写日志也不会撞，给列表当稳定 key。 */
        val id: Long,
        val timestamp: Long,
        val level: Level,
        val category: Category,
        val message: String,
        /** 这一步耗时，例如一次 VLM 识别用了多久。 */
        val elapsedMs: Long? = null,
    )

    private val idGen = AtomicLong(0)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())

    /** 最近 [CAPACITY] 条日志，按时间从旧到新。 */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun info(
        category: Category,
        message: String,
        elapsedMs: Long? = null,
        timestamp: Long = System.currentTimeMillis(),
    ) = add(Level.INFO, category, message, elapsedMs, timestamp)

    fun warn(
        category: Category,
        message: String,
        elapsedMs: Long? = null,
        timestamp: Long = System.currentTimeMillis(),
    ) = add(Level.WARN, category, message, elapsedMs, timestamp)

    fun error(
        category: Category,
        message: String,
        t: Throwable? = null,
        elapsedMs: Long? = null,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        val full = if (t != null) "$message: ${t.javaClass.simpleName}: ${t.message}" else message
        add(Level.ERROR, category, full, elapsedMs, timestamp)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun add(
        level: Level,
        category: Category,
        message: String,
        elapsedMs: Long?,
        timestamp: Long,
    ) {
        push(
            Entry(
                id = idGen.incrementAndGet(),
                timestamp = timestamp,
                level = level,
                category = category,
                message = message,
                elapsedMs = elapsedMs,
            )
        )
    }

    private fun push(e: Entry) {
        val current = _entries.value
        val next = if (current.size >= CAPACITY) {
            // 丢最旧的，保留最近 CAPACITY 条
            current.drop(current.size - CAPACITY + 1) + e
        } else {
            current + e
        }
        _entries.value = next
    }

    companion object {
        internal const val CAPACITY = 200
    }
}
