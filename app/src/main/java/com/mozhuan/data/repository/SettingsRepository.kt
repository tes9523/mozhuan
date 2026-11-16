package com.mozhuan.data.repository

import com.mozhuan.data.model.ApiSettings
import com.mozhuan.data.model.ThemeMode
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.StateFlow

/**
 * 设置仓库：外观 + LLM 提供商 + 人格（SOUL.md）。
 */
class SettingsRepository(
    private val preferences: AppPreferences,
) {

    private val _themeMode = preferences.themeMode
    val themeMode: StateFlow<String> = _themeMode

    val fontScale: StateFlow<Float> = preferences.fontScale

    val apiSettings: StateFlow<ApiSettings> = preferences.apiSettings

    val persona: StateFlow<String> = preferences.persona

    fun currentThemeMode(): ThemeMode = ThemeMode.from(_themeMode.value)

    fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode.name)

    fun setFontScale(scale: Float) = preferences.setFontScale(scale)

    fun saveApiSettings(settings: ApiSettings) = preferences.saveApiSettings(settings)

    /** 恢复默认参数（保留接口地址 / Key / 模型等连接信息）。 */
    fun resetApiParameters() {
        val current = preferences.apiSettings.value
        preferences.saveApiSettings(
            current.copy(
                maxTokens = ApiSettings.DEFAULT.maxTokens,
                temperature = ApiSettings.DEFAULT.temperature,
                topP = ApiSettings.DEFAULT.topP,
                contextTurns = ApiSettings.DEFAULT.contextTurns,
            ),
        )
    }

    fun setPersona(text: String) = preferences.setPersona(text)

    fun currentApiSettings(): ApiSettings = preferences.apiSettings.value

    fun currentPersona(): String = preferences.persona.value
}
