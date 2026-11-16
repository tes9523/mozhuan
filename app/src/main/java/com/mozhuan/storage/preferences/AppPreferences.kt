package com.mozhuan.storage.preferences

import android.content.Context
import com.mozhuan.data.model.ApiSettings
import com.mozhuan.data.model.DisplaySettings
import com.mozhuan.data.model.Skill
import com.mozhuan.data.model.UsageStat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 轻量偏好存储：外观、服务商、人格、技能与累计 Token 统计。
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

    private val _displaySettings = MutableStateFlow(
        DisplaySettings(
            showTimestamps = prefs.getBoolean(KEY_SHOW_TIMESTAMPS, false),
            compactMessages = prefs.getBoolean(KEY_COMPACT_MESSAGES, false),
            autoScroll = prefs.getBoolean(KEY_AUTO_SCROLL, true),
        ),
    )
    val displaySettings: StateFlow<DisplaySettings> = _displaySettings.asStateFlow()

    fun setDisplaySettings(settings: DisplaySettings) {
        prefs.edit()
            .putBoolean(KEY_SHOW_TIMESTAMPS, settings.showTimestamps)
            .putBoolean(KEY_COMPACT_MESSAGES, settings.compactMessages)
            .putBoolean(KEY_AUTO_SCROLL, settings.autoScroll)
            .apply()
        _displaySettings.value = settings
    }

    // ── LLM 服务商 ───────────────────────────────────────────────

    private val _providers = MutableStateFlow(loadProviders())
    val providers: StateFlow<List<ApiSettings>> = _providers.asStateFlow()

    private val _defaultProviderId = MutableStateFlow(
        prefs.getString(KEY_DEFAULT_PROVIDER_ID, null)
            ?.takeIf { id -> _providers.value.any { it.id == id } }
            ?: _providers.value.firstOrNull()?.id.orEmpty(),
    )
    val defaultProviderId: StateFlow<String> = _defaultProviderId.asStateFlow()

    fun saveProviders(providers: List<ApiSettings>) {
        val defaultId = _defaultProviderId.value
            .takeIf { id -> providers.any { it.id == id } }
            ?: providers.firstOrNull()?.id.orEmpty()
        prefs.edit()
            .putString(KEY_PROVIDERS, providersToJson(providers))
            .putString(KEY_DEFAULT_PROVIDER_ID, defaultId)
            .apply()
        _providers.value = providers
        _defaultProviderId.value = defaultId
    }

    fun setDefaultProvider(providerId: String) {
        if (_providers.value.none { it.id == providerId }) return
        prefs.edit().putString(KEY_DEFAULT_PROVIDER_ID, providerId).apply()
        _defaultProviderId.value = providerId
    }

    private fun loadProviders(): List<ApiSettings> {
        val stored = prefs.getString(KEY_PROVIDERS, null)
        if (stored != null) {
            return runCatching {
                val array = JSONArray(stored)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            ApiSettings(
                                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                                name = item.optString("name"),
                                baseUrl = item.optString("baseUrl"),
                                apiKey = item.optString("apiKey"),
                                model = item.optString("model"),
                                maxTokens = item.optInt("maxTokens", 2048),
                                temperature = item.optDouble("temperature", 0.8).toFloat(),
                                topP = item.optDouble("topP", 1.0).toFloat(),
                                contextTurns = item.optInt("contextTurns", 10),
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        // 从 v0.5.0 的单服务商配置迁移。
        val legacy = ApiSettings(
            id = "legacy_provider",
            name = "默认服务商",
            baseUrl = prefs.getString(KEY_API_BASE_URL, "") ?: "",
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            model = prefs.getString(KEY_API_MODEL, "") ?: "",
            maxTokens = prefs.getInt(KEY_MAX_TOKENS, 2048),
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.8f),
            topP = prefs.getFloat(KEY_TOP_P, 1.0f),
            contextTurns = prefs.getInt(KEY_CONTEXT_TURNS, 10),
        )
        return if (legacy.baseUrl.isNotBlank() || legacy.apiKey.isNotBlank() || legacy.model.isNotBlank()) {
            listOf(legacy)
        } else {
            emptyList()
        }
    }

    private fun providersToJson(providers: List<ApiSettings>): String = JSONArray().apply {
        providers.forEach { settings ->
            put(
                JSONObject()
                    .put("id", settings.id)
                    .put("name", settings.name)
                    .put("baseUrl", settings.baseUrl)
                    .put("apiKey", settings.apiKey)
                    .put("model", settings.model)
                    .put("maxTokens", settings.maxTokens)
                    .put("temperature", settings.temperature.toDouble())
                    .put("topP", settings.topP.toDouble())
                    .put("contextTurns", settings.contextTurns),
            )
        }
    }.toString()

    // ── 人格系统（SOUL.md） ───────────────────────────────────────

    private val _persona = MutableStateFlow(
        prefs.getString(KEY_PERSONA, DEFAULT_PERSONA) ?: DEFAULT_PERSONA,
    )
    val persona: StateFlow<String> = _persona.asStateFlow()

    fun setPersona(text: String) {
        prefs.edit().putString(KEY_PERSONA, text).apply()
        _persona.value = text
    }

    // ── 静态提示词技能 ────────────────────────────────────────────

    private val _skills = MutableStateFlow(loadSkills())
    val skills: StateFlow<List<Skill>> = _skills.asStateFlow()

    fun saveSkills(skills: List<Skill>) {
        val json = JSONArray().apply {
            skills.forEach { skill ->
                put(
                    JSONObject()
                        .put("id", skill.id)
                        .put("name", skill.name)
                        .put("prompt", skill.prompt)
                        .put("enabled", skill.enabled),
                )
            }
        }.toString()
        prefs.edit().putString(KEY_SKILLS, json).apply()
        _skills.value = skills
    }

    private fun loadSkills(): List<Skill> {
        val stored = prefs.getString(KEY_SKILLS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        Skill(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = item.optString("name"),
                            prompt = item.optString("prompt"),
                            enabled = item.optBoolean("enabled", true),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    // ── 累计 Token 统计 ───────────────────────────────────────────

    private val _usageStats = MutableStateFlow(loadUsageStats())
    val usageStats: StateFlow<List<UsageStat>> = _usageStats.asStateFlow()

    fun saveUsageStats(stats: List<UsageStat>) {
        val json = JSONArray().apply {
            stats.forEach { stat ->
                put(
                    JSONObject()
                        .put("providerId", stat.providerId)
                        .put("providerName", stat.providerName)
                        .put("model", stat.model)
                        .put("inputTokens", stat.inputTokens)
                        .put("outputTokens", stat.outputTokens),
                )
            }
        }.toString()
        prefs.edit().putString(KEY_USAGE_STATS, json).apply()
        _usageStats.value = stats
    }

    private fun loadUsageStats(): List<UsageStat> {
        val stored = prefs.getString(KEY_USAGE_STATS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        UsageStat(
                            providerId = item.optString("providerId"),
                            providerName = item.optString("providerName"),
                            model = item.optString("model"),
                            inputTokens = item.optInt("inputTokens"),
                            outputTokens = item.optInt("outputTokens"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
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
        private const val KEY_SHOW_TIMESTAMPS = "show_timestamps"
        private const val KEY_COMPACT_MESSAGES = "compact_messages"
        private const val KEY_AUTO_SCROLL = "auto_scroll"
        private const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
        private const val KEY_PROVIDERS = "providers_json"
        private const val KEY_DEFAULT_PROVIDER_ID = "default_provider_id"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_MODEL = "api_model"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOP_P = "top_p"
        private const val KEY_CONTEXT_TURNS = "context_turns"
        private const val KEY_PERSONA = "persona_soul"
        private const val KEY_SKILLS = "skills_json"
        private const val KEY_USAGE_STATS = "usage_stats_json"

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
