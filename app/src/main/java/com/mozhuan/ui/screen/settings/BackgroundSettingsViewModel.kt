package com.mozhuan.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.NotificationSettings
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.system.power.BatteryOptimizationHelper
import com.mozhuan.system.service.WritingSessionService
import kotlinx.coroutines.flow.StateFlow

/** 后台与通知设置：通知开关、后台保活与电池优化白名单。 */
class BackgroundSettingsViewModel(
    private val context: Context,
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

    fun setKeepAlive(enabled: Boolean) {
        val current = notificationSettings.value
        if (current.keepAlive == enabled) return
        settingsRepository.setNotificationSettings(current.copy(keepAlive = enabled))
        if (enabled) {
            WritingSessionService.startKeepAlive(context)
        } else {
            WritingSessionService.stop(context)
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean =
        BatteryOptimizationHelper.isIgnoringOptimizations(context)
}
