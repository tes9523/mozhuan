package com.mozhuan.data.model

import java.util.UUID

/** 书稿：书名、本书设定、章节列表、绑定技能。 */
data class Book(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    /** 世界观设定。 */
    val worldview: String = "",
    /** 人物设定。 */
    val characters: String = "",
    /** 章节列表（按顺序）。 */
    val chapters: List<Chapter> = emptyList(),
    /** 本书绑定的技能 ID，优先级高于全局技能。 */
    val boundSkillIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

/** 章节：标题、正文。 */
data class Chapter(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String = "",
)

/** 技能：名称、系统 Prompt、标签。内置技能不可删除。 */
data class Skill(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val systemPrompt: String,
    val tags: List<String> = emptyList(),
    val builtin: Boolean = false,
)

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

/** 统一 API 配置：对话与续写共用同一套后端。 */
data class AiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)
