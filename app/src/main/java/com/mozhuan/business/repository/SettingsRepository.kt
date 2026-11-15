package com.mozhuan.business.repository

import android.net.Uri
import com.mozhuan.business.model.ThemeMode
import com.mozhuan.net.AiBackendType
import com.mozhuan.net.model.BackendConfig
import com.mozhuan.storage.preferences.AppPreferences
import com.mozhuan.storage.saf.SafDocumentStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(
    private val preferences: AppPreferences,
    private val safStore: SafDocumentStore,
) {

    private val _themeMode = MutableStateFlow(ThemeMode.from(preferences.themeMode.value))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    val rootUri: StateFlow<String?> = preferences.rootUri

    // ── AI 后端配置 ──────────────────────────────────────────────

    /** 当前选中的后端类型。 */
    private val _aiBackend = MutableStateFlow(AiBackendType.from(preferences.aiBackend.value))
    val aiBackend: StateFlow<AiBackendType> = _aiBackend.asStateFlow()

    /** 切换当前生效的后端。 */
    fun setAiBackend(type: AiBackendType) {
        preferences.setAiBackend(type.name)
        _aiBackend.value = type
    }

    /** 获取指定后端的配置。 */
    fun getBackendConfig(type: AiBackendType): BackendConfig = when (type) {
        AiBackendType.OPEN_MINIS -> BackendConfig(
            baseUrl = preferences.openMinisBaseUrl,
            apiKey = preferences.openMinisApiKey,
        )
        AiBackendType.OPEN_WRITE -> BackendConfig(
            baseUrl = preferences.openWriteBaseUrl,
            apiKey = preferences.openWriteApiKey,
        )
    }

    /** 保存指定后端的地址。 */
    fun setBackendBaseUrl(type: AiBackendType, url: String) {
        when (type) {
            AiBackendType.OPEN_MINIS -> preferences.openMinisBaseUrl = url
            AiBackendType.OPEN_WRITE -> preferences.openWriteBaseUrl = url
        }
    }

    /** 保存指定后端的密钥。 */
    fun setBackendApiKey(type: AiBackendType, key: String) {
        when (type) {
            AiBackendType.OPEN_MINIS -> preferences.openMinisApiKey = key
            AiBackendType.OPEN_WRITE -> preferences.openWriteApiKey = key
        }
    }

    // ── 基础设置 ──────────────────────────────────────────────────

    fun setThemeMode(mode: ThemeMode) {
        preferences.setThemeMode(mode.name)
        _themeMode.value = mode
    }

    fun isStorageSelected(): Boolean = preferences.rootUri.value != null

    fun hasStorageAccess(): Boolean = safStore.hasRootPermission()

    fun storageDisplayName(): String? = safStore.rootDisplayName()

    suspend fun selectStorage(uri: Uri): Result<Unit> = safStore.setRoot(uri)
}
