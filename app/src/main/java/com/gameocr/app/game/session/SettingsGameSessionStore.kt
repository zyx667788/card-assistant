package com.gameocr.app.game.session

import com.gameocr.app.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** 把当局建议历史写入现有 Settings/DataStore 管线。 */
@Singleton
class SettingsGameSessionStore @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : GameSessionStore {
    override suspend fun load(): Map<String, List<String>> =
        settingsRepository.get().gameSessionHistoryByModule

    override suspend fun save(sessions: Map<String, List<String>>) {
        settingsRepository.update { it.copy(gameSessionHistoryByModule = sessions) }
    }
}
