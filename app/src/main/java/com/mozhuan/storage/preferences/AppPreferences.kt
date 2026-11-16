package com.mozhuan.storage.preferences

import android.content.Context
import com.mozhuan.data.model.ChatMessage
import com.mozhuan.data.model.ChatRole
import com.mozhuan.data.model.ChatSession
import com.mozhuan.data.model.DisplaySettings
import com.mozhuan.data.model.GroupRoutingStrategy
import com.mozhuan.data.model.ModelEntry
import com.mozhuan.data.model.ModelGroup
import com.mozhuan.data.model.MountedFolder
import com.mozhuan.data.model.NotificationSettings
import com.mozhuan.data.model.ProviderInstance
import com.mozhuan.data.model.ProviderType
import com.mozhuan.data.model.ProxySettings
import com.mozhuan.data.model.ThinkingLevel
import com.mozhuan.data.model.ToolSettings
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
    private val maxTokensDefaulted = migrateMaxTokensToProviderDefault()

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

    private val _modelGroups = MutableStateFlow(loadModelGroups())
    val modelGroups: StateFlow<List<ModelGroup>> = _modelGroups.asStateFlow()

    private val _selectedGroupId = MutableStateFlow(
        prefs.getString(KEY_SELECTED_GROUP_ID, "").orEmpty()
            .takeIf { id -> _modelGroups.value.any { it.id == id } }
            .orEmpty(),
    )
    val selectedGroupId: StateFlow<String> = _selectedGroupId.asStateFlow()

    init {
        if (migrated) {
            prefs.edit().putString(KEY_SELECTED_ENTRY_ID, _selectedEntryId.value).apply()
        }
        if (maxTokensDefaulted) {
            persistEntries(_modelEntries.value)
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
        prefs.edit()
            .putString(KEY_SELECTED_ENTRY_ID, entryId)
            .putString(KEY_SELECTED_GROUP_ID, "")
            .apply()
        _selectedEntryId.value = entryId
        _selectedGroupId.value = ""
    }

    fun saveModelGroups(groups: List<ModelGroup>) {
        persistGroups(groups)
        _modelGroups.value = groups
    }

    fun setSelectedGroup(groupId: String) {
        if (groupId.isNotBlank() && _modelGroups.value.none { it.id == groupId }) return
        prefs.edit().putString(KEY_SELECTED_GROUP_ID, groupId).apply()
        _selectedGroupId.value = groupId
    }

    /** 备份 / 恢复时整体重新加载所有服务商、模型与模型组。 */
    fun reloadModelConfig() {
        _providers.value = loadProviders()
        _modelEntries.value = loadModelEntries()
        _modelGroups.value = loadModelGroups()
        _selectedEntryId.value = prefs.getString(KEY_SELECTED_ENTRY_ID, "").orEmpty()
            .takeIf { id -> _modelEntries.value.any { it.id == id } }.orEmpty()
        _selectedGroupId.value = prefs.getString(KEY_SELECTED_GROUP_ID, "").orEmpty()
            .takeIf { id -> _modelGroups.value.any { it.id == id } }.orEmpty()
    }

    /** 导出可备份的配置（服务商 / 模型 / 模型组 / 人格）。 */
    fun exportConfig(): JSONObject = JSONObject()
        .put("providers", prefs.getString(KEY_PROVIDER_INSTANCES, "[]"))
        .put("entries", prefs.getString(KEY_MODEL_ENTRIES, "[]"))
        .put("groups", prefs.getString(KEY_MODEL_GROUPS, "[]"))
        .put("selectedEntryId", _selectedEntryId.value)
        .put("selectedGroupId", _selectedGroupId.value)
        .put("persona", persona.value)

    fun restoreConfig(config: JSONObject) {
        prefs.edit()
            .putString(KEY_PROVIDER_INSTANCES, config.optString("providers", "[]"))
            .putString(KEY_MODEL_ENTRIES, config.optString("entries", "[]"))
            .putString(KEY_MODEL_GROUPS, config.optString("groups", "[]"))
            .putString(KEY_SELECTED_ENTRY_ID, config.optString("selectedEntryId", ""))
            .putString(KEY_SELECTED_GROUP_ID, config.optString("selectedGroupId", ""))
            .putString(KEY_PERSONA, config.optString("persona", DEFAULT_PERSONA))
            .apply()
        _persona.value = config.optString("persona", DEFAULT_PERSONA)
        reloadModelConfig()
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
                        .put("thinkingLevel", entry.thinkingLevel.name)
                        .put("isCustom", entry.isCustom)
                        .put("createdAt", entry.createdAt),
                )
            }
        }.toString()
        prefs.edit().putString(KEY_MODEL_ENTRIES, json).apply()
    }

    private fun persistGroups(groups: List<ModelGroup>) {
        val json = JSONArray().apply {
            groups.forEach { group ->
                put(
                    JSONObject()
                        .put("id", group.id)
                        .put("name", group.name)
                        .put("memberEntryIds", JSONArray(group.memberEntryIds))
                        .put("strategy", group.strategy.name)
                        .put("createdAt", group.createdAt),
                )
            }
        }.toString()
        prefs.edit().putString(KEY_MODEL_GROUPS, json).apply()
    }

    private fun loadModelGroups(): List<ModelGroup> {
        val stored = prefs.getString(KEY_MODEL_GROUPS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val members = item.optJSONArray("memberEntryIds")
                    add(
                        ModelGroup(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = item.optString("name"),
                            memberEntryIds = buildList {
                                if (members != null) {
                                    for (m in 0 until members.length()) {
                                        members.optString(m).takeIf { it.isNotBlank() }?.let(::add)
                                    }
                                }
                            },
                            strategy = GroupRoutingStrategy.entries
                                .firstOrNull { it.name == item.optString("strategy") }
                                ?: GroupRoutingStrategy.FALLBACK,
                            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * v0.9.0 默认给每个模型写入 max_tokens=4096。
     * 本版本改为「留空即服务商默认」，把未改动的 4096 一次性迁移为 0。
     */
    private fun migrateMaxTokensToProviderDefault(): Boolean {
        if (prefs.getBoolean(KEY_MAX_TOKENS_DEFAULTED, false)) return false
        prefs.edit().putBoolean(KEY_MAX_TOKENS_DEFAULTED, true).apply()
        val stored = prefs.getString(KEY_MODEL_ENTRIES, null) ?: return false
        val migrated = runCatching {
            val array = JSONArray(stored)
            var changed = false
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                if (item.optInt("maxTokens", 0) == 4096) {
                    item.put("maxTokens", 0)
                    changed = true
                }
            }
            if (changed) array.toString() else null
        }.getOrNull() ?: return false
        prefs.edit().putString(KEY_MODEL_ENTRIES, migrated).apply()
        return true
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
                            maxTokens = item.optInt("maxTokens", 0),
                            contextTurns = item.optInt("contextTurns", 10),
                            temperature = item.optDouble("temperature", 0.8).toFloat(),
                            topP = item.optDouble("topP", 1.0).toFloat(),
                            thinkingLevel = ThinkingLevel.from(item.optString("thinkingLevel")),
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
                    maxTokens = item.optInt("maxTokens", 0),
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

    // ── 代理、通知与外部文件夹 ───────────────────────────────────

    private val _proxySettings = MutableStateFlow(
        ProxySettings(
            enabled = prefs.getBoolean(KEY_PROXY_ENABLED, false),
            type = prefs.getString(KEY_PROXY_TYPE, ProxySettings.PROXY_HTTP)
                ?: ProxySettings.PROXY_HTTP,
            host = prefs.getString(KEY_PROXY_HOST, "").orEmpty(),
            port = prefs.getInt(KEY_PROXY_PORT, 7890),
        ),
    )
    val proxySettings: StateFlow<ProxySettings> = _proxySettings.asStateFlow()

    fun setProxySettings(settings: ProxySettings) {
        prefs.edit()
            .putBoolean(KEY_PROXY_ENABLED, settings.enabled)
            .putString(KEY_PROXY_TYPE, settings.type)
            .putString(KEY_PROXY_HOST, settings.host.trim())
            .putInt(KEY_PROXY_PORT, settings.port)
            .apply()
        _proxySettings.value = settings.copy(host = settings.host.trim())
    }

    private val _notificationSettings = MutableStateFlow(
        NotificationSettings(
            notifyOnComplete = prefs.getBoolean(KEY_NOTIFY_COMPLETE, true),
            notifyOnError = prefs.getBoolean(KEY_NOTIFY_ERROR, true),
            keepAlive = prefs.getBoolean(KEY_KEEP_ALIVE, false),
        ),
    )
    val notificationSettings: StateFlow<NotificationSettings> = _notificationSettings.asStateFlow()

    fun setNotificationSettings(settings: NotificationSettings) {
        prefs.edit()
            .putBoolean(KEY_NOTIFY_COMPLETE, settings.notifyOnComplete)
            .putBoolean(KEY_NOTIFY_ERROR, settings.notifyOnError)
            .putBoolean(KEY_KEEP_ALIVE, settings.keepAlive)
            .apply()
        _notificationSettings.value = settings
    }

    /** 可用工具开关。 */
    private val _toolSettings = MutableStateFlow(
        ToolSettings(
            readFiles = prefs.getBoolean(KEY_TOOL_READ, true),
            writeFiles = prefs.getBoolean(KEY_TOOL_WRITE, false),
            webFetch = prefs.getBoolean(KEY_TOOL_WEB, true),
            memoryWrite = prefs.getBoolean(KEY_TOOL_MEMORY, true),
        ),
    )
    val toolSettings: StateFlow<ToolSettings> = _toolSettings.asStateFlow()

    fun setToolSettings(settings: ToolSettings) {
        prefs.edit()
            .putBoolean(KEY_TOOL_READ, settings.readFiles)
            .putBoolean(KEY_TOOL_WRITE, settings.writeFiles)
            .putBoolean(KEY_TOOL_WEB, settings.webFetch)
            .putBoolean(KEY_TOOL_MEMORY, settings.memoryWrite)
            .apply()
        _toolSettings.value = settings
    }

    /** 记忆总开关：关闭后不再把记忆文件注入系统提示词。 */
    private val _memoryEnabled = MutableStateFlow(prefs.getBoolean(KEY_MEMORY_ENABLED, true))
    val memoryEnabled: StateFlow<Boolean> = _memoryEnabled.asStateFlow()

    fun setMemoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEMORY_ENABLED, enabled).apply()
        _memoryEnabled.value = enabled
    }

    // ── 会话 ─────────────────────────────────────────────────────

    private val _sessions = MutableStateFlow(loadSessions())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_SESSION_ID, "").orEmpty()
            .takeIf { id -> _sessions.value.any { it.id == id } }
            ?: _sessions.value.firstOrNull()?.id.orEmpty(),
    )
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    fun saveSessions(sessions: List<ChatSession>) {
        _sessions.value = sessions
        prefs.edit().putString(KEY_SESSIONS, encodeSessions(sessions)).apply()
    }

    fun setActiveSession(sessionId: String) {
        prefs.edit().putString(KEY_ACTIVE_SESSION_ID, sessionId).apply()
        _activeSessionId.value = sessionId
    }

    private fun encodeSessions(sessions: List<ChatSession>): String = JSONArray().apply {
        sessions.forEach { session ->
            put(
                JSONObject()
                    .put("id", session.id)
                    .put("title", session.title)
                    .put("createdAt", session.createdAt)
                    .put("updatedAt", session.updatedAt)
                    .put(
                        "messages",
                        JSONArray().apply {
                            session.messages.forEach { message ->
                                put(
                                    JSONObject()
                                        .put("id", message.id)
                                        .put("role", message.role.name)
                                        .put("content", message.content)
                                        .put("reasoning", message.reasoning)
                                        .put("createdAt", message.createdAt),
                                )
                            }
                        },
                    ),
            )
        }
    }.toString()

    private fun loadSessions(): List<ChatSession> {
        val stored = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val messages = item.optJSONArray("messages")
                    add(
                        ChatSession(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            title = item.optString("title"),
                            messages = buildList {
                                if (messages != null) {
                                    for (m in 0 until messages.length()) {
                                        val message = messages.optJSONObject(m) ?: continue
                                        add(
                                            ChatMessage(
                                                id = message.optString("id")
                                                    .ifBlank { UUID.randomUUID().toString() },
                                                role = ChatRole.entries
                                                    .firstOrNull { it.name == message.optString("role") }
                                                    ?: ChatRole.ASSISTANT,
                                                content = message.optString("content"),
                                                reasoning = message.optString("reasoning"),
                                                createdAt = message.optLong(
                                                    "createdAt",
                                                    System.currentTimeMillis(),
                                                ),
                                            ),
                                        )
                                    }
                                }
                            },
                            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private val _mountedFolders = MutableStateFlow(loadMountedFolders())
    val mountedFolders: StateFlow<List<MountedFolder>> = _mountedFolders.asStateFlow()

    fun saveMountedFolders(folders: List<MountedFolder>) {
        val json = JSONArray().apply {
            folders.forEach { folder ->
                put(
                    JSONObject()
                        .put("id", folder.id)
                        .put("name", folder.name)
                        .put("uri", folder.uri)
                        .put("addedAt", folder.addedAt),
                )
            }
        }.toString()
        prefs.edit().putString(KEY_MOUNTED_FOLDERS, json).apply()
        _mountedFolders.value = folders
    }

    private fun loadMountedFolders(): List<MountedFolder> {
        val stored = prefs.getString(KEY_MOUNTED_FOLDERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val uri = item.optString("uri")
                    if (uri.isBlank()) continue
                    add(
                        MountedFolder(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = item.optString("name").ifBlank { "外部文件夹" },
                            uri = uri,
                            addedAt = item.optLong("addedAt", System.currentTimeMillis()),
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
        private const val KEY_PROVIDER_INSTANCES = "provider_instances_json"
        private const val KEY_MODEL_ENTRIES = "model_entries_json"
        private const val KEY_MODEL_GROUPS = "model_groups_json"
        private const val KEY_SELECTED_ENTRY_ID = "selected_model_entry_id"
        private const val KEY_SELECTED_GROUP_ID = "selected_model_group_id"
        private const val KEY_MAX_TOKENS_DEFAULTED = "max_tokens_provider_default_migrated"
        private const val KEY_PROXY_ENABLED = "proxy_enabled"
        private const val KEY_PROXY_TYPE = "proxy_type"
        private const val KEY_PROXY_HOST = "proxy_host"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_NOTIFY_COMPLETE = "notify_on_complete"
        private const val KEY_NOTIFY_ERROR = "notify_on_error"
        private const val KEY_KEEP_ALIVE = "keep_alive_enabled"
        private const val KEY_TOOL_READ = "tool_read_files"
        private const val KEY_TOOL_WRITE = "tool_write_files"
        private const val KEY_TOOL_WEB = "tool_web_fetch"
        private const val KEY_TOOL_MEMORY = "tool_memory_write"
        private const val KEY_MEMORY_ENABLED = "memory_enabled"
        private const val KEY_SESSIONS = "chat_sessions_json"
        private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
        private const val KEY_MOUNTED_FOLDERS = "mounted_folders_json"
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
