package com.mozhuan.data.repository

import com.mozhuan.data.model.Skill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 技能仓库（OpenMinis 风格，本阶段仅内存存储）。
 *
 * 内置两个不可删除的基础技能，另有用户自定义的全局技能。
 * 书稿可以绑定专属技能，绑定后优先级高于全局技能。
 */
class SkillRepository {

    private val _skills = MutableStateFlow(BUILTIN_SKILLS)
    val skills: StateFlow<List<Skill>> = _skills.asStateFlow()

    /** 当前全局对话选中的技能。 */
    private val _selectedGlobalSkillId = MutableStateFlow(BUILTIN_CHAT_ID)
    val selectedGlobalSkillId: StateFlow<String> = _selectedGlobalSkillId.asStateFlow()

    fun getById(id: String): Skill? = _skills.value.firstOrNull { it.id == id }

    fun setSelectedGlobalSkill(id: String) {
        _selectedGlobalSkillId.value = id
    }

    /** 新增自定义全局技能。 */
    fun addSkill(name: String, systemPrompt: String, tags: List<String>): Skill {
        val skill = Skill(name = name.trim(), systemPrompt = systemPrompt, tags = tags)
        _skills.update { it + skill }
        return skill
    }

    /** 编辑自定义技能；内置技能不允许修改。 */
    fun updateSkill(id: String, name: String, systemPrompt: String, tags: List<String>) {
        _skills.update { list ->
            list.map { skill ->
                if (skill.id != id || skill.builtin) skill else skill.copy(
                    name = name.trim(),
                    systemPrompt = systemPrompt,
                    tags = tags,
                )
            }
        }
    }

    /** 删除自定义技能；内置技能不可删除。返回是否删除成功。 */
    fun deleteSkill(id: String): Boolean {
        val target = getById(id) ?: return false
        if (target.builtin) return false
        _skills.update { list -> list.filterNot { it.id == id } }
        if (_selectedGlobalSkillId.value == id) {
            _selectedGlobalSkillId.value = BUILTIN_CHAT_ID
        }
        return true
    }

    companion object {
        const val BUILTIN_CHAT_ID = "builtin_chat_polish"
        const val BUILTIN_CONTINUE_ID = "builtin_chapter_continue"

        /** 内置技能：剧情对话打磨。 */
        private val CHAT_POLISH = Skill(
            id = BUILTIN_CHAT_ID,
            name = "剧情对话打磨",
            systemPrompt = "你是一位经验丰富的网文编辑与故事策划，擅长脑暴大纲、打磨人设、讨论剧情走向。" +
                "请结合作者提供的设定给出具体、可执行的建议，指出逻辑漏洞与爽点机会，" +
                "语言简洁专业，避免空话套话。",
            tags = listOf("对话", "脑暴", "人设"),
            builtin = true,
        )

        /** 内置技能：网文章节续写。 */
        private val CHAPTER_CONTINUE = Skill(
            id = BUILTIN_CONTINUE_ID,
            name = "网文章节续写",
            systemPrompt = "你是一位网文写作助手。请严格依据作者提供的书稿设定与已有正文，承接当前章节继续创作。" +
                "要求：保持人称、文风、节奏一致；直接输出续写正文；不要输出解释、标题或旁白；不要重复已有内容。",
            tags = listOf("续写", "正文"),
            builtin = true,
        )

        private val BUILTIN_SKILLS = listOf(CHAT_POLISH, CHAPTER_CONTINUE)
    }
}
