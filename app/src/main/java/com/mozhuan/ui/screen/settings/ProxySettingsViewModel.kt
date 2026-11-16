package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.ProxySettings
import com.mozhuan.data.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

/** 代理设置：开关、协议、地址与端口。 */
class ProxySettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val proxySettings: StateFlow<ProxySettings> = settingsRepository.proxySettings

    fun save(settings: ProxySettings) = settingsRepository.setProxySettings(settings)
}
