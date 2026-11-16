package com.mozhuan.data.repository

import com.mozhuan.data.model.AiConfig
import com.mozhuan.data.model.ThemeMode
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置仓库：主题模式 + 单套 API 配置（对话与续写共用）。
 */
class SettingsRepository(
    private val preferences: AppPreferences,
) {

    private val _themeMode = MutableStateFlow(ThemeMode.from(preferences.themeMode.value))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _apiBaseUrl = MutableStateFlow(preferences.apiBaseUrl)
    val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()

    private val _apiKey = MutableStateFlow(preferences.apiKey)
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _modelName = MutableStateFlow(preferences.modelName)
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        preferences.setThemeMode(mode.name)
        _themeMode.value = mode
    }

    fun setApiBaseUrl(url: String) {
        preferences.apiBaseUrl = url
        _apiBaseUrl.value = url
    }

    fun setApiKey(key: String) {
        preferences.apiKey = key
        _apiKey.value = key
    }

    fun setModelName(name: String) {
        preferences.modelName = name
        _modelName.value = name
    }

    /** 当前生效的 API 配置。 */
    fun aiConfig(): AiConfig = AiConfig(
        baseUrl = _apiBaseUrl.value,
        apiKey = _apiKey.value,
        model = _modelName.value,
    )

    /** 配置是否完整，用于 UI 提示。 */
    fun isApiConfigured(): Boolean = _apiBaseUrl.value.isNotBlank() && _apiKey.value.isNotBlank()
}
