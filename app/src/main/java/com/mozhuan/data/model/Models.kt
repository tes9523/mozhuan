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

/**
 * 某个服务商下的模型条目，参数独立保存（对齐 OpenMinis 的 per-model overrides）。
 */
data class ModelEntry(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val modelId: String,
    val displayName: String = "",
    val maxTokens: Int = 4096,
    /** 携带的历史对话轮数，0 表示仅发送当前消息。 */
    val contextTurns: Int = 10,
    val temperature: Float = 0.8f,
    val topP: Float = 1.0f,
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val title: String
        get() = displayName.trim().ifBlank { modelId }
}

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
}

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
