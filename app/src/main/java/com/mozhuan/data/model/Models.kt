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
)

/** 主题模式。 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun from(name: String): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * 统一 LLM 提供商配置（OpenAI 兼容接口）。
 * 对话、续写、人设渲染全部共用这一套配置。
 */
data class ApiSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val maxTokens: Int = 2048,
    val temperature: Float = 0.8f,
    val topP: Float = 1.0f,
    /** 携带的历史对话轮数，0 表示不携带历史。 */
    val contextTurns: Int = 10,
) {
    /** 是否已填写必要的连接信息。 */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    companion object {
        val DEFAULT = ApiSettings()
    }
}
