package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.data.model.ActiveModel
import com.mozhuan.data.model.DisplaySettings
import com.mozhuan.data.model.ModelEntry
import com.mozhuan.data.model.ModelGroup
import com.mozhuan.data.model.MountedFolder
import com.mozhuan.data.model.NotificationSettings
import com.mozhuan.data.model.ProviderInstance
import com.mozhuan.data.model.ProxySettings
import com.mozhuan.data.model.ThemeMode
import com.mozhuan.data.repository.ProviderRepository
import com.mozhuan.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 设置 ViewModel：外观、显示选项、当前模型 / 模型组与存储、网络状态。 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    providerRepository: ProviderRepository,
) : ViewModel() {

    val themeMode: StateFlow<String> = settingsRepository.themeMode
    val fontScale: StateFlow<Float> = settingsRepository.fontScale
    val displaySettings: StateFlow<DisplaySettings> = settingsRepository.displaySettings
    val proxySettings: StateFlow<ProxySettings> = settingsRepository.proxySettings
    val notificationSettings: StateFlow<NotificationSettings> = settingsRepository.notificationSettings
    val mountedFolders: StateFlow<List<MountedFolder>> = settingsRepository.mountedFolders
    val groups: StateFlow<List<ModelGroup>> = providerRepository.groups

    val activeGroup: StateFlow<ModelGroup?> =
        combine(providerRepository.groups, providerRepository.selectedGroupId) { groups, id ->
            groups.firstOrNull { it.id == id }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeModel: StateFlow<ActiveModel?> =
        combine(
            providerRepository.providers,
            providerRepository.entries,
            providerRepository.selectedEntryId,
            providerRepository.groups,
            providerRepository.selectedGroupId,
        ) { providers, entries, selectedId, groups, groupId ->
            val group = groups.firstOrNull { it.id == groupId }
            if (group != null) {
                groupMember(group, providers, entries)?.let { return@combine it }
            }
            resolveActive(providers, entries, selectedId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun setFontScale(scale: Float) = settingsRepository.setFontScale(scale)

    fun setDisplaySettings(settings: DisplaySettings) = settingsRepository.setDisplaySettings(settings)

    private fun groupMember(
        group: ModelGroup,
        providers: List<ProviderInstance>,
        entries: List<ModelEntry>,
    ): ActiveModel? {
        val byProvider = providers.filter { it.isEnabled }.associateBy { it.id }
        val byEntry = entries.associateBy { it.id }
        return group.memberEntryIds.firstNotNullOfOrNull { id ->
            val entry = byEntry[id] ?: return@firstNotNullOfOrNull null
            byProvider[entry.providerId]?.let { ActiveModel(it, entry) }
        }
    }

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
