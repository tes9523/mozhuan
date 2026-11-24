package com.mozhuan.data.model

import java.util.UUID

/** 对话角色。 */
enum class ChatRole(val apiValue: String, val displayName: String) {
    USER("user", "我"),
    ASSISTANT("assistant", "AI"),
}

/** 单条对话消息。 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    /** 推理模型的思考过程（reasoning_content），仅展示，不参与后续上下文。 */
    val reasoning: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** 独立会话：每个会话拥有自己的消息列表，互不影响。 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新会话",
    val messages: List<ChatMessage> = emptyList(),
    /** 本会话的思考强度；null 表示跟随当前模型的默认强度。 */
    val thinkingLevel: ThinkingLevel? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val displayTitle: String
        get() = title.trim().ifBlank { "新会话" }
}

/** 主题模式。 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun from(name: String): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** 对话显示设置。 */
data class DisplaySettings(
    val showTimestamps: Boolean = false,
    val compactMessages: Boolean = false,
    val autoScroll: Boolean = true,
)

/** 服务商使用的接口协议。 */
enum class ApiStyle {
    /** OpenAI Chat Completions（/chat/completions）。 */
    OPENAI_CHAT,

    /** Anthropic Messages（/messages）。 */
    ANTHROPIC_MESSAGES,
}

/**
 * 服务商类型预设，对齐 OpenMinis 的内置服务商列表。
 * 自定义地址留空时使用 [defaultBaseUrl]。
 */
enum class ProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModels: List<String>,
    val apiStyle: ApiStyle,
    val defaultAppendV1: Boolean = true,
) {
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com",
        defaultModels = listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4o", "gpt-4o-mini", "o3", "o4-mini"),
        apiStyle = ApiStyle.OPENAI_CHAT,
    ),
    ANTHROPIC(
        displayName = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com",
        defaultModels = listOf(
            "claude-sonnet-4-5",
            "claude-opus-4-1",
            "claude-haiku-4-5",
        ),
        apiStyle = ApiStyle.ANTHROPIC_MESSAGES,
    ),
    GEMINI(
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        defaultModels = listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash"),
        apiStyle = ApiStyle.OPENAI_CHAT,
        defaultAppendV1 = false,
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModels = emptyList(),
        apiStyle = ApiStyle.OPENAI_CHAT,
        defaultAppendV1 = false,
    ),
    XAI(
        displayName = "xAI (Grok)",
        defaultBaseUrl = "https://api.x.ai/v1",
        defaultModels = listOf("grok-4", "grok-3", "grok-3-mini"),
        apiStyle = ApiStyle.OPENAI_CHAT,
        defaultAppendV1 = false,
    ),
    CUSTOM(
        displayName = "自定义（OpenAI 兼容）",
        defaultBaseUrl = "",
        defaultModels = emptyList(),
        apiStyle = ApiStyle.OPENAI_CHAT,
    );

    companion object {
        fun from(name: String): ProviderType =
            entries.firstOrNull { it.name == name } ?: CUSTOM
    }
}

/**
 * 单个服务商实例。地址规则与 OpenMinis 一致：
 * 自定义地址（或预设地址）末尾按需追加 /v1，再拼接具体接口路径。
 */
data class ProviderInstance(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val type: ProviderType,
    val apiKey: String = "",
    val customBaseUrl: String = "",
    val appendV1Suffix: Boolean = true,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** 生效的基础地址（已处理 /v1 后缀）。 */
    val effectiveBaseUrl: String
        get() {
            val raw = customBaseUrl.trim().ifBlank { type.defaultBaseUrl }.trim()
            if (raw.isEmpty()) return ""
            var base = raw.trimEnd('/')
            if (type == ProviderType.GEMINI) return base
            if (appendV1Suffix && !base.endsWith("/v1")) base += "/v1"
            return base
        }

    val displayName: String
        get() = label.trim().ifBlank { type.displayName }
}

/** 模型思考强度；wireValue 为 OpenAI 系请求使用的 effort。 */
enum class ThinkingLevel(val label: String, val wireValue: String?) {
    OFF("关闭", null),
    LOW("低", "low"),
    MEDIUM("中", "medium"),
    HIGH("高", "high"),
    XHIGH("超高", "xhigh"),
    MAX("最大", "max"),
    ULTRA("极致", "max");

    companion object {
        fun from(name: String): ThinkingLevel =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: OFF
    }
}

/**
 * 某个服务商下的模型条目，参数独立保存（对齐 OpenMinis 的 per-model overrides）。
 *
 * [maxTokens] 为 0 时表示不发送该参数，交由服务商使用默认值。
 */
data class ModelEntry(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val modelId: String,
    val displayName: String = "",
    val maxTokens: Int = 0,
    /** 携带的历史对话轮数，0 表示仅发送当前消息。 */
    val contextTurns: Int = 10,
    val temperature: Float = 0.8f,
    val topP: Float = 1.0f,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.MEDIUM,
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val title: String
        get() = displayName.trim().ifBlank { modelId }
}

/** 模型组路由策略，对齐 OpenMinis：顺序故障转移或随机负载均衡。 */
enum class GroupRoutingStrategy(val label: String, val description: String) {
    FALLBACK("故障转移", "按成员顺序调用，失败时自动切换到下一个模型"),
    LOAD_BALANCE("负载均衡", "每次请求在成员中随机选择一个可用模型"),
}

/** 模型组：把多个模型组合成一个可在对话页直接选择的逻辑模型。 */
data class ModelGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val memberEntryIds: List<String> = emptyList(),
    val strategy: GroupRoutingStrategy = GroupRoutingStrategy.FALLBACK,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 代理设置。 */
data class ProxySettings(
    val enabled: Boolean = false,
    val type: String = PROXY_HTTP,
    val host: String = "",
    val port: Int = 7890,
) {
    val summary: String
        get() = when {
            !enabled -> "未启用"
            host.isBlank() -> "已启用 · 地址未填写"
            else -> "$type · $host:$port"
        }

    companion object {
        const val PROXY_HTTP = "HTTP"
        const val PROXY_SOCKS = "SOCKS5"
    }
}

/** 后台与通知设置。 */
data class NotificationSettings(
    val notifyOnComplete: Boolean = true,
    val notifyOnError: Boolean = true,
    /** 后台保活：开启后常驻前台服务，降低被系统清理的概率。 */
    val keepAlive: Boolean = false,
)

/** 可用工具开关：关闭后不再向模型发送该工具的 schema。 */
data class ToolSettings(
    val readFiles: Boolean = true,
    val writeFiles: Boolean = true,
    val webFetch: Boolean = true,
    val memoryWrite: Boolean = true,
) {
    val enabledCount: Int
        get() = listOf(readFiles, writeFiles, webFetch, memoryWrite).count { it }
}

/** 已挂载的外部文件夹（SAF 目录授权）。 */
data class MountedFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uri: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** 已解析的当前模型：服务商 + 模型条目。 */
data class ActiveModel(
    val provider: ProviderInstance,
    val entry: ModelEntry,
) {
    val title: String
        get() = entry.title
}

/** SKILL.md 技能：文件存放在 /var/minis/skills/<id>/SKILL.md，
 * YAML frontmatter 保存名称、描述与版本，正文为提示词。 */
data class Skill(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "1.0.0",
    val body: String = "",
    val enabled: Boolean = true,
    val updatedAt: Long = 0,
) {
    val displayDescription: String
        get() = description.trim().ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }

    val isBuiltIn: Boolean
        get() = id == BUILT_IN_SKILL_CREATOR_ID

    companion object {
        const val BUILT_IN_SKILL_CREATOR_ID = "skill-creator"
    }
}

/** 技能目录中的附件文件，用于详情页展示 references / assets 等资源。 */
data class SkillFile(
    val path: String,
    val sizeBytes: Long,
    val editable: Boolean,
)

/** 一次请求的 Token 用量。 */
data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
) {
    val totalTokens: Int
        get() = inputTokens + outputTokens

    operator fun plus(other: TokenUsage): TokenUsage = TokenUsage(
        inputTokens = inputTokens + other.inputTokens,
        outputTokens = outputTokens + other.outputTokens,
    )
}

/** 按服务商 + 模型累计的 Token 用量。 */
data class UsageStat(
    val providerId: String,
    val providerName: String,
    val model: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
) {
    val totalTokens: Int
        get() = inputTokens + outputTokens
}
