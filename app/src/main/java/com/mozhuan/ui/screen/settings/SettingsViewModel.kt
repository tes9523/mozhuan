package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.ThemeMode
import com.mozhuan.data.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 设置 ViewModel：主题 + 单套 API 配置。
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
    val apiBaseUrl: StateFlow<String> = settingsRepository.apiBaseUrl
    val apiKey: StateFlow<String> = settingsRepository.apiKey
    val modelName: StateFlow<String> = settingsRepository.modelName

    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun setApiBaseUrl(url: String) = settingsRepository.setApiBaseUrl(url)

    fun setApiKey(key: String) = settingsRepository.setApiKey(key)

    fun setModelName(name: String) = settingsRepository.setModelName(name)
}
