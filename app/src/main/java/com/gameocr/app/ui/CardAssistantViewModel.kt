package com.gameocr.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gameocr.app.data.Settings
import com.gameocr.app.data.SettingsRepository
import com.gameocr.app.game.core.GameModule
import com.gameocr.app.game.core.GameModuleRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 打牌助手主界面的 ViewModel：只读写精简后的 Settings。 */
@HiltViewModel
class CardAssistantViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    gameModuleRegistry: GameModuleRegistry,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    /** 首页模式选择器用的已注册牌类模块（跑胡子、斗地主……）。 */
    val gameModules: List<GameModule> = gameModuleRegistry.all

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
