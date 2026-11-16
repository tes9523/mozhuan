package com.mozhuan.net.model

/** 模型返回的工具调用。 */
data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

/** 发送给 AI 后端的单条消息（OpenAI 兼容格式）。 */
data class AiMessage(
    val role: String,
    val content: String,
    val toolCalls: List<AiToolCall> = emptyList(),
    val toolCallId: String? = null,
)
