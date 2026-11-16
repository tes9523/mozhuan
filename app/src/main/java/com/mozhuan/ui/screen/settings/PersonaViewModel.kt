package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 人格系统 ViewModel：编辑全局 SOUL.md，保存后所有对话自动携带。
 */
class PersonaViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _text = MutableStateFlow(settingsRepository.currentPersona())
    val text: StateFlow<String> = _text.asStateFlow()

    fun update(value: String) {
        _text.value = value
    }

    fun save() = settingsRepository.setPersona(_text.value)

    /** 恢复为内置默认人格（需再点保存才生效）。 */
    fun restoreDefault() {
        _text.value = AppPreferences.DEFAULT_PERSONA
    }
}
