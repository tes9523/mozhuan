package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.NotificationSettings
import com.mozhuan.data.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

/** 后台与通知设置。 */
class BackgroundSettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val notificationSettings: StateFlow<NotificationSettings> =
        settingsRepository.notificationSettings

    fun setNotifyOnComplete(enabled: Boolean) {
        val current = notificationSettings.value
        settingsRepository.setNotificationSettings(current.copy(notifyOnComplete = enabled))
    }

    fun setNotifyOnError(enabled: Boolean) {
        val current = notificationSettings.value
        settingsRepository.setNotificationSettings(current.copy(notifyOnError = enabled))
    }
}
