package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.ApiSettings
import com.mozhuan.data.model.ThemeMode
import com.mozhuan.data.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 设置 ViewModel：主题 + 字号 + LLM 提供商配置。
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<String> = settingsRepository.themeMode
    val fontScale: StateFlow<Float> = settingsRepository.fontScale
    val apiSettings: StateFlow<ApiSettings> = settingsRepository.apiSettings

    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun setFontScale(scale: Float) = settingsRepository.setFontScale(scale)
}
