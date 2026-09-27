package com.gameocr.app.game.session

import com.gameocr.app.game.core.AdviceAction
import com.gameocr.app.game.core.GameAdvice
import com.gameocr.app.game.core.GameState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionManagerTest {
    @Test
    fun recordsAndResetsPerModule() = runBlocking {
        val store = FakeGameSessionStore()
        val manager = GameSessionManager(store)
        val state = FakeState("paohuzi", "手牌 3 张")
        manager.record("paohuzi", state, GameAdvice(AdviceAction.PLAY, "一", "先出一"))
        manager.record("paohuzi", state, GameAdvice(AdviceAction.PUNG, "二", "碰二"))

        assertEquals(2, manager.history("paohuzi").size)
        assertTrue(manager.history("paohuzi").last().contains("碰 二"))

        manager.reset("paohuzi")
        assertTrue(manager.history("paohuzi").isEmpty())
    }

    @Test
    fun historyIsPersistedAndBounded() = runBlocking {
        val store = FakeGameSessionStore()
        val manager = GameSessionManager(store)
        val state = FakeState("paohuzi", "手牌")
        repeat(20) { index ->
            manager.record(
                moduleId = "paohuzi",
                state = state,
                advice = GameAdvice(AdviceAction.PLAY, index.toString(), "第${index}手"),
            )
        }

        assertEquals(12, manager.history("paohuzi").size)
        assertTrue(store.snapshot().containsKey("paohuzi"))
    }

    private class FakeGameSessionStore(
        private var sessions: Map<String, List<String>> = emptyMap(),
    ) : GameSessionStore {
        override suspend fun load(): Map<String, List<String>> = sessions

        override suspend fun save(sessions: Map<String, List<String>>) {
            this.sessions = sessions.mapValues { (_, value) -> value.toList() }
        }

        fun snapshot(): Map<String, List<String>> = sessions
    }

    private data class FakeState(
        override val moduleId: String,
        private val summaryText: String,
    ) : GameState {
        override fun toPromptText(): String = summaryText
        override fun summary(): String = summaryText
    }
}
