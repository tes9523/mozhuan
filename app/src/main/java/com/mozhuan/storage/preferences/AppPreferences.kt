package com.mozhuan.storage.preferences

import android.content.Context
import com.mozhuan.data.model.DisplaySettings
import com.mozhuan.data.model.ModelEntry
import com.mozhuan.data.model.ProviderInstance
import com.mozhuan.data.model.ProviderType
import com.mozhuan.data.model.UsageStat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 轻量偏好存储：外观、服务商 / 模型、人格、技能启用状态与累计 Token 统计。
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

    // ── LLM 服务商与模型 ─────────────────────────────────────────

    private val migrated = migrateLegacyProviders()

    private val _providers = MutableStateFlow(loadProviders())
    val providers: StateFlow<List<ProviderInstance>> = _providers.asStateFlow()

    private val _modelEntries = MutableStateFlow(loadModelEntries())
    val modelEntries: StateFlow<List<ModelEntry>> = _modelEntries.asStateFlow()

    private val _selectedEntryId = MutableStateFlow(
        prefs.getString(KEY_SELECTED_ENTRY_ID, null)
            ?.takeIf { id -> _modelEntries.value.any { it.id == id } }
            ?: _modelEntries.value.firstOrNull()?.id.orEmpty(),
    )
    val selectedEntryId: StateFlow<String> = _selectedEntryId.asStateFlow()

    init {
        if (migrated) {
            persistProviders(_providers.value)
            persistEntries(_modelEntries.value)
            prefs.edit().putString(KEY_SELECTED_ENTRY_ID, _selectedEntryId.value).apply()
        }
    }

    fun saveProviders(providers: List<ProviderInstance>) {
        persistProviders(providers)
        _providers.value = providers
    }

    fun saveModelEntries(entries: List<ModelEntry>) {
        persistEntries(entries)
        _modelEntries.value = entries
    }

    fun setSelectedEntry(entryId: String) {
        if (entryId.isNotBlank() && _modelEntries.value.none { it.id == entryId }) return
        prefs.edit().putString(KEY_SELECTED_ENTRY_ID, entryId).apply()
        _selectedEntryId.value = entryId
    }

    private fun persistProviders(providers: List<ProviderInstance>) {
        val json = JSONArray().apply {
            providers.forEach { provider ->
                put(
                    JSONObject()
                        .put("id", provider.id)
                        .put("label", provider.label)
                        .put("providerType", provider.type.name)
                        .put("apiKey", provider.apiKey)
                        .put("customBaseUrl", provider.customBaseUrl)
                        .put("appendV1Suffix", provider.appendV1Suffix)
                        .put("isEnabled", provider.isEnabled)
                        .put("createdAt", provider.createdAt),
                )
            }
        }.toString()
        prefs.edit().putString(KEY_PROVIDER_INSTANCES, json).apply()
    }

    private fun persistEntries(entries: List<ModelEntry>) {
        val json = JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("providerId", entry.providerId)
                        .put("modelId", entry.modelId)
                        .put("displayName", entry.displayName)
                        .put("maxTokens", entry.maxTokens)
                        .put("contextTurns", entry.contextTurns)
                        .put("temperature", entry.temperature.toDouble())
                        .put("topP", entry.topP.toDouble())
                        .put("isCustom", entry.isCustom)
                        .put("createdAt", entry.createdAt),
                )
            }
        }.toString()
        prefs.edit().putString(KEY_MODEL_ENTRIES, json).apply()
    }

    private fun loadProviders(): List<ProviderInstance> {
        val stored = prefs.getString(KEY_PROVIDER_INSTANCES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        ProviderInstance(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            label = item.optString("label"),
                            type = ProviderType.from(item.optString("providerType")),
                            apiKey = item.optString("apiKey"),
                            customBaseUrl = item.optString("customBaseUrl"),
                            appendV1Suffix = item.optBoolean("appendV1Suffix", true),
                            isEnabled = item.optBoolean("isEnabled", true),
                            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadModelEntries(): List<ModelEntry> {
        val stored = prefs.getString(KEY_MODEL_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        ModelEntry(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            providerId = item.optString("providerId"),
                            modelId = item.optString("modelId"),
                            displayName = item.optString("displayName"),
                            maxTokens = item.optInt("maxTokens", 4096),
                            contextTurns = item.optInt("contextTurns", 10),
                            temperature = item.optDouble("temperature", 0.8).toFloat(),
                            topP = item.optDouble("topP", 1.0).toFloat(),
                            isCustom = item.optBoolean("isCustom", false),
                            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * v0.8.0 及更早版本按「完整请求地址 + 单模型」保存服务商。
     * 迁移为「预设类型 + 自定义基础地址 + 模型条目」，地址保留原样发送语义。
     */
    private fun migrateLegacyProviders(): Boolean {
        if (prefs.contains(KEY_PROVIDER_INSTANCES)) return false
        val stored = prefs.getString(KEY_PROVIDERS, null) ?: return false
        val legacy = runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index))
                }
            }
        }.getOrDefault(emptyList())
        if (legacy.isEmpty()) return false

        val providers = mutableListOf<ProviderInstance>()
        val entries = mutableListOf<ModelEntry>()
        var selectedId = ""

        legacy.forEach { item ->
            val providerId = item.optString("id").ifBlank { UUID.randomUUID().toString() }
            var baseUrl = item.optString("baseUrl").trim().trimEnd('/')
            if (baseUrl.endsWith("/chat/completions", ignoreCase = true)) {
                baseUrl = baseUrl.dropLast("/chat/completions".length).trimEnd('/')
            } else if (baseUrl.endsWith("/messages", ignoreCase = true)) {
                baseUrl = baseUrl.dropLast("/messages".length).trimEnd('/')
            }
            providers += ProviderInstance(
                id = providerId,
                label = item.optString("name").ifBlank { "默认服务商" },
                type = ProviderType.CUSTOM,
                apiKey = item.optString("apiKey"),
                customBaseUrl = baseUrl,
                appendV1Suffix = false,
                isEnabled = true,
            )
            val modelId = item.optString("model").trim()
            if (modelId.isNotEmpty()) {
                val entry = ModelEntry(
                    providerId = providerId,
                    modelId = modelId,
                    maxTokens = item.optInt("maxTokens", 4096),
                    contextTurns = item.optInt("contextTurns", 10),
                    temperature = item.optDouble("temperature", 0.8).toFloat(),
                    topP = item.optDouble("topP", 1.0).toFloat(),
                )
                entries += entry
                if (selectedId.isEmpty()) selectedId = entry.id
            }
        }
        if (providers.isEmpty()) return false

        persistProviders(providers)
        persistEntries(entries)
        prefs.edit()
            .putString(KEY_SELECTED_ENTRY_ID, selectedId)
            .remove(KEY_PROVIDERS)
            .remove(KEY_DEFAULT_PROVIDER_ID)
            .apply()
        return true
    }

    // ── 人格系统（SOUL.md） ───────────────────────────────────────

    private val _persona = MutableStateFlow(
        prefs.getString(KEY_PERSONA, DEFAULT_PERSONA) ?: DEFAULT_PERSONA,
    )
    val persona: StateFlow<String> = _persona.asStateFlow()

    fun setPersona(text: String) {
        prefs.edit().putString(KEY_PERSONA, text).apply()
        _persona.value = text
    }

    // ── 技能启用状态（技能正文以 SKILL.md 存盘） ─────────────────

    private val _disabledSkillIds = MutableStateFlow(
        prefs.getStringSet(KEY_DISABLED_SKILL_IDS, emptySet())?.toSet().orEmpty(),
    )
    val disabledSkillIds: StateFlow<Set<String>> = _disabledSkillIds.asStateFlow()

    fun setSkillEnabled(skillId: String, enabled: Boolean) {
        val updated = _disabledSkillIds.value.toMutableSet().apply {
            if (enabled) remove(skillId) else add(skillId)
        }
        prefs.edit().putStringSet(KEY_DISABLED_SKILL_IDS, updated).apply()
        _disabledSkillIds.value = updated
    }

    /** v0.8.0 及更早版本的技能存在 SharedPreferences 中，由 SkillRepository 迁移为 SKILL.md。 */
    fun legacySkillsJson(): String? = prefs.getString(KEY_SKILLS, null)

    fun clearLegacySkills() {
        prefs.edit().remove(KEY_SKILLS).apply()
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
        private const val KEY_PROVIDER_INSTANCES = "provider_instances_json"
        private const val KEY_MODEL_ENTRIES = "model_entries_json"
        private const val KEY_SELECTED_ENTRY_ID = "selected_model_entry_id"
        private const val KEY_PROVIDERS = "providers_json"
        private const val KEY_DEFAULT_PROVIDER_ID = "default_provider_id"
        private const val KEY_PERSONA = "persona_soul"
        private const val KEY_DISABLED_SKILL_IDS = "disabled_skill_ids"
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
