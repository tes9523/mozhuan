package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.ApiSettings
import com.mozhuan.data.model.DisplaySettings
import com.mozhuan.data.model.ThemeMode
import com.mozhuan.data.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

/** 设置 ViewModel：外观、显示选项与服务商列表。 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<String> = settingsRepository.themeMode
    val fontScale: StateFlow<Float> = settingsRepository.fontScale
    val displaySettings: StateFlow<DisplaySettings> = settingsRepository.displaySettings
    val providers: StateFlow<List<ApiSettings>> = settingsRepository.providers
    val defaultProviderId: StateFlow<String> = settingsRepository.defaultProviderId

    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun setFontScale(scale: Float) = settingsRepository.setFontScale(scale)

    fun setDisplaySettings(settings: DisplaySettings) = settingsRepository.setDisplaySettings(settings)
}
