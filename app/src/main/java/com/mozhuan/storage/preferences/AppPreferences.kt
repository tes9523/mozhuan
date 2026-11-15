package com.mozhuan.storage.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 轻量偏好存储：只保存主题模式、SAF 根目录 URI 等应用级设置。
 * 书稿元数据由 Room 保存，正文由 SAF 文件保存。
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _rootUri = MutableStateFlow(prefs.getString(KEY_ROOT_URI, null))
    val rootUri: StateFlow<String?> = _rootUri.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

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
        const val THEME_SYSTEM = "SYSTEM"
    }
}
