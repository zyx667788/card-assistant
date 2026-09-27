package com.gameocr.app.game.session

import com.gameocr.app.game.core.GameAdvice
import com.gameocr.app.game.core.GameState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 当局状态。历史通过 [GameSessionStore] 持久化，服务重启后仍能继续参考之前的建议。
 */
@Singleton
class GameSessionManager @Inject constructor(
    private val store: GameSessionStore,
) {
    private val mutex = Mutex()

    suspend fun history(moduleId: String): List<String> = mutex.withLock {
        store.load()[moduleId].orEmpty()
    }

    suspend fun record(moduleId: String, state: GameState, advice: GameAdvice) = mutex.withLock {
        val sessions = store.load().toMutableMap()
        val history = sessions.getOrPut(moduleId) { mutableListOf() }.toMutableList()
        val line = buildString {
            append(state.summary())
            append(" -> ")
            append(advice.action.displayName)
            advice.targetTile?.takeIf { it.isNotBlank() }?.let { append(' ').append(it) }
            if (advice.reason.isNotBlank()) append(": ").append(advice.reason.trim())
        }
        history += line
        while (history.size > MAX_HISTORY) history.removeAt(0)
        sessions[moduleId] = history
        store.save(sessions)
    }

    suspend fun reset(moduleId: String) = mutex.withLock {
        val sessions = store.load().toMutableMap()
        sessions.remove(moduleId)
        store.save(sessions)
    }

    suspend fun resetAll() = mutex.withLock {
        store.save(emptyMap())
    }

    private companion object {
        const val MAX_HISTORY = 12
    }
}
