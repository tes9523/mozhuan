package com.mozhuan.business.repository

import android.net.Uri
import com.mozhuan.business.model.ThemeMode
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

    fun setThemeMode(mode: ThemeMode) {
        preferences.setThemeMode(mode.name)
        _themeMode.value = mode
    }

    fun isStorageSelected(): Boolean = preferences.rootUri.value != null

    fun hasStorageAccess(): Boolean = safStore.hasRootPermission()

    fun storageDisplayName(): String? = safStore.rootDisplayName()

    suspend fun selectStorage(uri: Uri): Result<Unit> = safStore.setRoot(uri)
}
