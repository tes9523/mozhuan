package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.data.model.ActiveModel
import com.mozhuan.data.model.DisplaySettings
import com.mozhuan.data.model.ModelEntry
import com.mozhuan.data.model.ProviderInstance
import com.mozhuan.data.model.ThemeMode
import com.mozhuan.data.repository.ProviderRepository
import com.mozhuan.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 设置 ViewModel：外观、显示选项与当前模型。 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    providerRepository: ProviderRepository,
) : ViewModel() {

    val themeMode: StateFlow<String> = settingsRepository.themeMode
    val fontScale: StateFlow<Float> = settingsRepository.fontScale
    val displaySettings: StateFlow<DisplaySettings> = settingsRepository.displaySettings

    val activeModel: StateFlow<ActiveModel?> =
        combine(
            providerRepository.providers,
            providerRepository.entries,
            providerRepository.selectedEntryId,
        ) { providers, entries, selectedId ->
            resolveActive(providers, entries, selectedId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun setFontScale(scale: Float) = settingsRepository.setFontScale(scale)

    fun setDisplaySettings(settings: DisplaySettings) = settingsRepository.setDisplaySettings(settings)

    private fun resolveActive(
        providers: List<ProviderInstance>,
        entries: List<ModelEntry>,
        selectedId: String,
    ): ActiveModel? {
        entries.firstOrNull { it.id == selectedId }?.let { entry ->
            providers.firstOrNull { it.id == entry.providerId && it.isEnabled }?.let {
                return ActiveModel(it, entry)
            }
        }
        providers.filter { it.isEnabled }.forEach { provider ->
            entries.firstOrNull { it.providerId == provider.id }?.let {
                return ActiveModel(provider, it)
            }
        }
        return null
    }
}
