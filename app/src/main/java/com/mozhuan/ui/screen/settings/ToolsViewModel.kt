package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.ToolSettings
import com.mozhuan.data.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

/** 可用工具开关：控制哪些工具会随请求发送给模型。 */
class ToolsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<ToolSettings> = settingsRepository.toolSettings

    fun setReadFiles(enabled: Boolean) = update { it.copy(readFiles = enabled) }

    fun setWriteFiles(enabled: Boolean) = update { it.copy(writeFiles = enabled) }

    fun setWebFetch(enabled: Boolean) = update { it.copy(webFetch = enabled) }

    fun setMemoryWrite(enabled: Boolean) = update { it.copy(memoryWrite = enabled) }

    private fun update(transform: (ToolSettings) -> ToolSettings) {
        settingsRepository.setToolSettings(transform(settings.value))
    }
}
