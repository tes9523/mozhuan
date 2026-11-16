package com.mozhuan.data.repository

import com.mozhuan.data.model.ApiSettings
import com.mozhuan.data.model.DisplaySettings
import com.mozhuan.data.model.ThemeMode
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.StateFlow

/** 设置仓库：外观、多个 LLM 服务商与人格（SOUL.md）。 */
class SettingsRepository(
    private val preferences: AppPreferences,
) {

    val themeMode: StateFlow<String> = preferences.themeMode
    val fontScale: StateFlow<Float> = preferences.fontScale
    val displaySettings: StateFlow<DisplaySettings> = preferences.displaySettings
    val providers: StateFlow<List<ApiSettings>> = preferences.providers
    val defaultProviderId: StateFlow<String> = preferences.defaultProviderId
    val persona: StateFlow<String> = preferences.persona

    fun currentThemeMode(): ThemeMode = ThemeMode.from(themeMode.value)

    fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode.name)

    fun setFontScale(scale: Float) = preferences.setFontScale(scale)

    fun setDisplaySettings(settings: DisplaySettings) = preferences.setDisplaySettings(settings)

    /** 当前默认服务商；未配置时返回空白配置。 */
    fun currentApiSettings(): ApiSettings {
        val all = providers.value
        return all.firstOrNull { it.id == defaultProviderId.value }
            ?: all.firstOrNull()
            ?: ApiSettings.DEFAULT
    }

    fun saveProvider(settings: ApiSettings) {
        val current = providers.value
        val exists = current.any { it.id == settings.id }
        val updated = if (exists) {
            current.map { if (it.id == settings.id) settings else it }
        } else {
            current + settings
        }
        preferences.saveProviders(updated)
    }

    fun deleteProvider(providerId: String) {
        preferences.saveProviders(providers.value.filterNot { it.id == providerId })
    }

    fun setDefaultProvider(providerId: String) = preferences.setDefaultProvider(providerId)

    fun setPersona(text: String) = preferences.setPersona(text)

    fun currentPersona(): String = preferences.persona.value
}
