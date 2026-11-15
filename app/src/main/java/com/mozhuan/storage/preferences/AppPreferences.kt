package com.mozhuan.storage.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 轻量偏好存储：保存主题模式、SAF 根目录 URI、AI 后端配置等应用级设置。
 * 书稿元数据由 Room 保存，正文由 SAF 文件保存。
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _rootUri = MutableStateFlow(prefs.getString(KEY_ROOT_URI, null))
    val rootUri: StateFlow<String?> = _rootUri.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // ── AI 后端配置 ──────────────────────────────────────────────

    /** 当前选中的后端类型（OPEN_MINIS / OPEN_WRITE）。 */
    private val _aiBackend = MutableStateFlow(prefs.getString(KEY_AI_BACKEND, "OPEN_MINIS") ?: "OPEN_MINIS")
    val aiBackend: StateFlow<String> = _aiBackend.asStateFlow()

    fun setAiBackend(type: String) {
        prefs.edit().putString(KEY_AI_BACKEND, type).apply()
        _aiBackend.value = type
    }

    /** OpenMinis 后端地址。 */
    var openMinisBaseUrl: String
        get() = prefs.getString(KEY_OPEN_MINIS_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPEN_MINIS_URL, value).apply()

    /** OpenMinis API 密钥。 */
    var openMinisApiKey: String
        get() = prefs.getString(KEY_OPEN_MINIS_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPEN_MINIS_KEY, value).apply()

    /** OpenWrite 后端地址。 */
    var openWriteBaseUrl: String
        get() = prefs.getString(KEY_OPEN_WRITE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPEN_WRITE_URL, value).apply()

    /** OpenWrite API 密钥。 */
    var openWriteApiKey: String
        get() = prefs.getString(KEY_OPEN_WRITE_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPEN_WRITE_KEY, value).apply()

    // ── 基础设置 ──────────────────────────────────────────────────

    fun setRootUri(uri: String?) {
        prefs.edit().putString(KEY_ROOT_URI, uri).apply()
        _rootUri.value = uri
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "mozhuan_preferences"
        private const val KEY_ROOT_URI = "storage_root_uri"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
        private const val KEY_AI_BACKEND = "ai_backend"
        private const val KEY_OPEN_MINIS_URL = "openminis_base_url"
        private const val KEY_OPEN_MINIS_KEY = "openminis_api_key"
        private const val KEY_OPEN_WRITE_URL = "openwrite_base_url"
        private const val KEY_OPEN_WRITE_KEY = "openwrite_api_key"
        const val THEME_SYSTEM = "SYSTEM"
    }
}
