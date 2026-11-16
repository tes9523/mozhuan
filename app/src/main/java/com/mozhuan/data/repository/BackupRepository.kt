package com.mozhuan.data.repository

import com.mozhuan.storage.preferences.AppPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份与恢复：导出服务商、模型、模型组、人格与技能为单个 JSON 文件；
 * 恢复时整体替换本机配置，技能按备份内容重建。
 */
class BackupRepository(
    private val preferences: AppPreferences,
    private val skillRepository: SkillRepository,
) {

    fun exportJson(): String {
        val skills = JSONArray().apply {
            skillRepository.skills.value.forEach { skill ->
                put(
                    JSONObject()
                        .put("name", skill.name)
                        .put("description", skill.description)
                        .put("version", skill.version)
                        .put("body", skill.body)
                        .put("enabled", skill.enabled),
                )
            }
        }
        return JSONObject()
            .put("app", "mozhuan")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("config", preferences.exportConfig())
            .put("skills", skills)
            .toString(2)
    }

    fun importJson(content: String): Result<String> = runCatching {
        val root = JSONObject(content)
        require(root.optInt("version", 0) >= 1) { "备份文件格式不正确" }
        val config = root.optJSONObject("config") ?: error("备份文件缺少配置内容")

        preferences.restoreConfig(config)

        skillRepository.skills.value.forEach { skillRepository.deleteSkill(it.id) }
        val skills = root.optJSONArray("skills") ?: JSONArray()
        var restored = 0
        for (index in 0 until skills.length()) {
            val item = skills.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val body = item.optString("body").trim()
            if (name.isEmpty() || body.isEmpty()) continue
            skillRepository.saveSkill(
                id = null,
                name = name,
                description = item.optString("description"),
                body = body,
                enabled = item.optBoolean("enabled", true),
            )
            restored++
        }
        "已恢复配置与 $restored 个技能"
    }
}
