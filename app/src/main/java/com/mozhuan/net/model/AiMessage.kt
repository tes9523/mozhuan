package com.mozhuan.net.model

/** 发送给 AI 后端的单条消息（OpenAI 兼容格式）。 */
data class AiMessage(
    val role: String,
    val content: String,
)
