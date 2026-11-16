package com.mozhuan.data.repository

import com.mozhuan.data.model.DisplaySettings
import com.mozhuan.data.model.MountedFolder
import com.mozhuan.data.model.NotificationSettings
import com.mozhuan.data.model.ProxySettings
import com.mozhuan.data.model.ThemeMode
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.StateFlow

/** 设置仓库：外观、显示选项、人格、代理、通知与外部文件夹。 */
class SettingsRepository(
    private val preferences: AppPreferences,
) {

    val themeMode: StateFlow<String> = preferences.themeMode
    val fontScale: StateFlow<Float> = preferences.fontScale
    val displaySettings: StateFlow<DisplaySettings> = preferences.displaySettings
    val persona: StateFlow<String> = preferences.persona
    val proxySettings: StateFlow<ProxySettings> = preferences.proxySettings
    val notificationSettings: StateFlow<NotificationSettings> = preferences.notificationSettings
    val mountedFolders: StateFlow<List<MountedFolder>> = preferences.mountedFolders

    fun currentThemeMode(): ThemeMode = ThemeMode.from(themeMode.value)

    fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode.name)

    fun setFontScale(scale: Float) = preferences.setFontScale(scale)

    fun setDisplaySettings(settings: DisplaySettings) = preferences.setDisplaySettings(settings)

    fun setPersona(text: String) = preferences.setPersona(text)

    fun currentPersona(): String = preferences.persona.value

    fun setProxySettings(settings: ProxySettings) = preferences.setProxySettings(settings)

    fun setNotificationSettings(settings: NotificationSettings) =
        preferences.setNotificationSettings(settings)

    fun addMountedFolder(folder: MountedFolder) {
        if (mountedFolders.value.any { it.uri == folder.uri }) return
        preferences.saveMountedFolders(mountedFolders.value + folder)
    }

    fun removeMountedFolder(folderId: String) {
        preferences.saveMountedFolders(mountedFolders.value.filterNot { it.id == folderId })
    }
}
