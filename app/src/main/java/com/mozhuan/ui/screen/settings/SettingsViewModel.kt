package com.mozhuan.ui.screen.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.business.model.ThemeMode
import com.mozhuan.business.repository.SettingsRepository
import com.mozhuan.net.AiBackendType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
    val aiBackend: StateFlow<AiBackendType> = settingsRepository.aiBackend

    private val _storageName = MutableStateFlow(settingsRepository.storageDisplayName())
    val storageName: StateFlow<String?> = _storageName.asStateFlow()

    private val _storageReady = MutableStateFlow(settingsRepository.hasStorageAccess())
    val storageReady: StateFlow<Boolean> = _storageReady.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ── AI 后端配置 ──────────────────────────────────────────────

    /** 当前后端的地址输入框内容。 */
    private val _backendBaseUrl = MutableStateFlow("")
    val backendBaseUrl: StateFlow<String> = _backendBaseUrl.asStateFlow()

    /** 当前后端的密钥输入框内容。 */
    private val _backendApiKey = MutableStateFlow("")
    val backendApiKey: StateFlow<String> = _backendApiKey.asStateFlow()

    init {
        loadBackendConfig(settingsRepository.aiBackend.value)
    }

    /** 切换后端时重新加载对应配置。 */
    fun setAiBackend(type: AiBackendType) {
        settingsRepository.setAiBackend(type)
        loadBackendConfig(type)
    }

    /** 加载指定后端的地址和密钥到输入框。 */
    private fun loadBackendConfig(type: AiBackendType) {
        val config = settingsRepository.getBackendConfig(type)
        _backendBaseUrl.value = config.baseUrl
        _backendApiKey.value = config.apiKey
    }

    /** 地址输入框内容变更时实时保存。 */
    fun setBackendBaseUrl(url: String) {
        _backendBaseUrl.value = url
        settingsRepository.setBackendBaseUrl(settingsRepository.aiBackend.value, url)
    }

    /** 密钥输入框内容变更时实时保存。 */
    fun setBackendApiKey(key: String) {
        _backendApiKey.value = key
        settingsRepository.setBackendApiKey(settingsRepository.aiBackend.value, key)
    }

    // ── 基础设置 ──────────────────────────────────────────────────

    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
    }

    fun selectStorage(uri: Uri) {
        viewModelScope.launch {
            settingsRepository.selectStorage(uri)
                .onSuccess {
                    refreshStorage()
                    _message.value = "存储目录已更新"
                }
                .onFailure { error ->
                    _message.value = error.message ?: "存储目录授权失败"
                }
        }
    }

    fun refreshStorage() {
        _storageName.value = settingsRepository.storageDisplayName()
        _storageReady.value = settingsRepository.hasStorageAccess()
    }

    fun clearMessage() {
        _message.value = null
    }
}
