package com.mozhuan.data.repository

import com.mozhuan.data.model.Skill
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.UUID

/** 静态提示词技能仓库：增删改、开关与单条 JSON 导入导出。 */
class SkillRepository(
    private val preferences: AppPreferences,
) {

    val skills: StateFlow<List<Skill>> = preferences.skills

    fun enabledSkills(): List<Skill> = skills.value.filter { it.enabled }

    fun saveSkill(skill: Skill) {
        val current = skills.value
        val updated = if (current.any { it.id == skill.id }) {
            current.map { if (it.id == skill.id) skill else it }
        } else {
            current + skill
        }
        preferences.saveSkills(updated)
    }

    fun deleteSkill(skillId: String) {
        preferences.saveSkills(skills.value.filterNot { it.id == skillId })
    }

    fun setEnabled(skillId: String, enabled: Boolean) {
        preferences.saveSkills(
            skills.value.map { if (it.id == skillId) it.copy(enabled = enabled) else it },
        )
    }

    fun exportSkill(skill: Skill): String = JSONObject()
        .put("type", EXPORT_TYPE)
        .put("name", skill.name)
        .put("prompt", skill.prompt)
        .put("enabled", skill.enabled)
        .toString(2)

    /** 导入为一条新技能，不覆盖已有技能。 */
    fun importSkill(json: String): Result<Skill> = runCatching {
        val item = JSONObject(json)
        val name = item.optString("name").trim()
        val prompt = item.optString("prompt").trim()
        require(name.isNotEmpty()) { "技能名称不能为空" }
        require(prompt.isNotEmpty()) { "技能提示词不能为空" }
        Skill(
            id = UUID.randomUUID().toString(),
            name = name,
            prompt = prompt,
            enabled = item.optBoolean("enabled", true),
        ).also(::saveSkill)
    }

    /** 从共享文件夹导入纯文本，生成一条静态提示词技能。 */
    fun importTextSkill(name: String, prompt: String): Skill {
        val skill = Skill(
            name = name.trim().ifBlank { "导入技能" },
            prompt = prompt,
            enabled = false,
        )
        saveSkill(skill)
        return skill
    }

    private companion object {
        const val EXPORT_TYPE = "mozhuan_skill"
    }
}
