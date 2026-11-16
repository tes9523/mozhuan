package com.mozhuan.data.repository

import com.mozhuan.data.model.DisplaySettings
import com.mozhuan.data.model.ThemeMode
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.StateFlow

/** 设置仓库：外观、显示选项与人格（SOUL.md）。 */
class SettingsRepository(
    private val preferences: AppPreferences,
) {

    val themeMode: StateFlow<String> = preferences.themeMode
    val fontScale: StateFlow<Float> = preferences.fontScale
    val displaySettings: StateFlow<DisplaySettings> = preferences.displaySettings
    val persona: StateFlow<String> = preferences.persona

    fun currentThemeMode(): ThemeMode = ThemeMode.from(themeMode.value)

    fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode.name)

    fun setFontScale(scale: Float) = preferences.setFontScale(scale)

    fun setDisplaySettings(settings: DisplaySettings) = preferences.setDisplaySettings(settings)

    fun setPersona(text: String) = preferences.setPersona(text)

    fun currentPersona(): String = preferences.persona.value
}
