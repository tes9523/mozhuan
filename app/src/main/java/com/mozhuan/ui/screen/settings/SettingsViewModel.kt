package com.mozhuan.ui.screen.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.business.model.ThemeMode
import com.mozhuan.business.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode

    private val _storageName = MutableStateFlow(settingsRepository.storageDisplayName())
    val storageName: StateFlow<String?> = _storageName.asStateFlow()

    private val _storageReady = MutableStateFlow(settingsRepository.hasStorageAccess())
    val storageReady: StateFlow<Boolean> = _storageReady.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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
