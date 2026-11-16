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

/**
 * 单个 LLM 服务商配置（OpenAI 兼容 chat/completions 接口）。
 * 每个服务商独立保存连接信息与生成参数。
 */
data class ApiSettings(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val maxTokens: Int = 2048,
    val temperature: Float = 0.8f,
    val topP: Float = 1.0f,
    /** 携带的历史对话轮数，0 表示仅发送当前消息。 */
    val contextTurns: Int = 10,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    val displayName: String
        get() = name.trim().ifBlank { "未命名服务商" }

    companion object {
        val DEFAULT = ApiSettings(id = "default", name = "默认服务商")
    }
}

/** 静态提示词技能，不包含工具调用或子代理。 */
data class Skill(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val prompt: String,
    val enabled: Boolean = true,
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
