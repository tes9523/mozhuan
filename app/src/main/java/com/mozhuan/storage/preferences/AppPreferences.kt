package com.mozhuan.storage.preferences

import android.content.Context
import com.mozhuan.data.model.ApiSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 轻量偏好存储：主题、字号、LLM 提供商配置、人格 Prompt（SOUL.md）。
 * 全部持久化在 SharedPreferences 中，重启应用后保留。
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── 外观 ─────────────────────────────────────────────────────

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM,
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    private val _fontScale = MutableStateFlow(prefs.getFloat(KEY_FONT_SCALE, 1.0f))
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    fun setFontScale(scale: Float) {
        prefs.edit().putFloat(KEY_FONT_SCALE, scale).apply()
        _fontScale.value = scale
    }

    // ── LLM 提供商配置 ────────────────────────────────────────────

    private val _apiSettings = MutableStateFlow(loadApiSettings())
    val apiSettings: StateFlow<ApiSettings> = _apiSettings.asStateFlow()

    fun saveApiSettings(settings: ApiSettings) {
        prefs.edit()
            .putString(KEY_API_BASE_URL, settings.baseUrl)
            .putString(KEY_API_KEY, settings.apiKey)
            .putString(KEY_API_MODEL, settings.model)
            .putInt(KEY_MAX_TOKENS, settings.maxTokens)
            .putFloat(KEY_TEMPERATURE, settings.temperature)
            .putFloat(KEY_TOP_P, settings.topP)
            .putInt(KEY_CONTEXT_TURNS, settings.contextTurns)
            .apply()
        _apiSettings.value = settings
    }

    private fun loadApiSettings(): ApiSettings = ApiSettings(
        baseUrl = prefs.getString(KEY_API_BASE_URL, "") ?: "",
        apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
        model = prefs.getString(KEY_API_MODEL, "") ?: "",
        maxTokens = prefs.getInt(KEY_MAX_TOKENS, 2048),
        temperature = prefs.getFloat(KEY_TEMPERATURE, 0.8f),
        topP = prefs.getFloat(KEY_TOP_P, 1.0f),
        contextTurns = prefs.getInt(KEY_CONTEXT_TURNS, 10),
    )

    // ── 人格系统（SOUL.md） ───────────────────────────────────────

    private val _persona = MutableStateFlow(
        prefs.getString(KEY_PERSONA, DEFAULT_PERSONA) ?: DEFAULT_PERSONA,
    )
    val persona: StateFlow<String> = _persona.asStateFlow()

    fun setPersona(text: String) {
        prefs.edit().putString(KEY_PERSONA, text).apply()
        _persona.value = text
    }

    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "mozhuan_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_MODEL = "api_model"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOP_P = "top_p"
        private const val KEY_CONTEXT_TURNS = "context_turns"
        private const val KEY_PERSONA = "persona_soul"

        const val THEME_SYSTEM = "SYSTEM"

        /** 默认人格（SOUL.md），用户可自由改写。 */
        const val DEFAULT_PERSONA = """# SOUL

你是一位资深中文小说写作搭档，擅长网文节奏、人物塑造与场景描写。

## 写作偏好
- 语言干净利落，避免翻译腔与「AI 味」
- 对话贴合人物身份，不堆砌形容词
- 描写有画面感，节奏张弛有度

## 协作方式
- 先理解作者意图，再给出可直接使用的内容
- 不确定时先提出 1~2 个关键问题
"""
    }
}
