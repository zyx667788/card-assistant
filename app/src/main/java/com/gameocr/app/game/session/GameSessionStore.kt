package com.gameocr.app.game.session

/** 会话历史的可替换存储，便于逻辑层单测与后续换存储实现。 */
interface GameSessionStore {
    suspend fun load(): Map<String, List<String>>
    suspend fun save(sessions: Map<String, List<String>>)
}
