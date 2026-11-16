package com.mozhuan.data.repository

import android.content.Context
import com.mozhuan.data.model.Skill
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * 技能仓库：每个技能是一个 `/var/minis/skills/<id>/SKILL.md` 文件，
 * 使用 YAML frontmatter 保存 name / description / version，正文为提示词。
 * 目录可被外部增删，进入技能页或调用 [reload] 时重新扫描。
 */
class SkillRepository(
    context: Context,
    private val preferences: AppPreferences,
) {

    private val skillsDir = File(context.applicationContext.filesDir, "skills")
    private val _skills = MutableStateFlow<List<Skill>>(emptyList())
    val skills: StateFlow<List<Skill>> = _skills.asStateFlow()

    init {
        migrateLegacySkills()
        reload()
    }

    fun reload() {
        if (!skillsDir.exists()) skillsDir.mkdirs()
        val disabled = preferences.disabledSkillIds.value
        val scanned = skillsDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { dir -> readSkill(dir, disabled) }
            .sortedByDescending { it.updatedAt }
        _skills.value = scanned
    }

    fun enabledSkills(): List<Skill> = skills.value.filter { it.enabled }

    fun setEnabled(skillId: String, enabled: Boolean) {
        preferences.setSkillEnabled(skillId, enabled)
        reload()
    }

    /** 新建或更新技能；[id] 为 null 时创建新目录。 */
    fun saveSkill(
        id: String?,
        name: String,
        description: String,
        body: String,
        enabled: Boolean,
    ): Skill {
        val skillId = id ?: UUID.randomUUID().toString()
        val dir = File(skillsDir, skillId)
        dir.mkdirs()
        val now = System.currentTimeMillis()
        val skill = Skill(
            id = skillId,
            name = name.trim(),
            description = description.trim(),
            version = "1.0.0",
            body = body.trim(),
            enabled = enabled,
            updatedAt = now,
        )
        File(dir, "SKILL.md").writeText(serialize(skill))
        preferences.setSkillEnabled(skillId, enabled)
        reload()
        return skill
    }

    fun deleteSkill(skillId: String) {
        File(skillsDir, skillId).deleteRecursively()
        preferences.setSkillEnabled(skillId, true)
        reload()
    }

    /**
     * 导入 SKILL.md 内容。没有 frontmatter 时用 [fallbackName]（通常是文件名）
     * 作为技能名称，整段文本作为正文。导入的技能默认停用。
     */
    fun importSkill(content: String, fallbackName: String? = null): Result<Skill> = runCatching {
        val parsed = parseSkillMd(content)
        val name = parsed?.name?.takeIf { it.isNotBlank() }
            ?: fallbackName?.trim()?.takeIf { it.isNotBlank() }
            ?: content.lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trimStart('#', ' ', '\t')
                ?.take(40)
                ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("无法识别技能名称")
        val body = parsed?.body?.takeIf { it.isNotBlank() } ?: content.trim()
        require(body.isNotBlank()) { "技能内容为空" }
        saveSkill(
            id = null,
            name = name,
            description = parsed?.description.orEmpty(),
            body = body,
            enabled = false,
        )
    }

    /** 导出为完整 SKILL.md 文本。 */
    fun exportMarkdown(skill: Skill): String = serialize(skill)

    fun skillMdPath(skillId: String): String = "/var/minis/skills/$skillId/SKILL.md"

    /** 把旧版本的 prefs 技能迁移为 SKILL.md；已有技能文件时只清理旧数据。 */
    private fun migrateLegacySkills() {
        val raw = preferences.legacySkillsJson() ?: return
        if (!skillsDir.exists()) skillsDir.mkdirs()
        val hasFiles = skillsDir.listFiles().orEmpty().any { it.isDirectory }
        if (!hasFiles) {
            runCatching {
                val array = JSONArray(raw)
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val name = item.optString("name").trim()
                    val prompt = item.optString("prompt").trim()
                    if (name.isEmpty() || prompt.isEmpty()) continue
                    saveSkill(
                        id = null,
                        name = name,
                        description = "",
                        body = prompt,
                        enabled = item.optBoolean("enabled", true),
                    )
                }
            }
        }
        preferences.clearLegacySkills()
    }

    // ── 文件读写 ─────────────────────────────────────────────────

    private fun readSkill(dir: File, disabled: Set<String>): Skill? {
        val file = File(dir, "SKILL.md")
        if (!file.isFile) return null
        val content = runCatching { file.readText() }.getOrNull() ?: return null
        val parsed = parseSkillMd(content) ?: return null
        if (parsed.name.isBlank()) return null
        return Skill(
            id = dir.name,
            name = parsed.name,
            description = parsed.description,
            version = parsed.version,
            body = parsed.body,
            enabled = dir.name !in disabled,
            updatedAt = file.lastModified(),
        )
    }

    private fun serialize(skill: Skill): String = buildString {
        append("---\n")
        append("name: ").append(yamlString(skill.name)).append('\n')
        if (skill.description.isNotBlank()) {
            append("description: ").append(yamlString(skill.description)).append('\n')
        }
        append("version: ").append(yamlString(skill.version.ifBlank { "1.0.0" })).append('\n')
        append("---\n\n")
        append(skill.body.trim()).append('\n')
    }

    private fun yamlString(value: String): String {
        val singleLine = value.replace("\r", " ").replace("\n", " ").trim()
        return "\"" + singleLine
            .replace("\\", "\\\\")
            .replace("\"", "\\\"") + "\""
    }

    // ── SKILL.md 解析（兼容 OpenMinis 的 frontmatter 形式） ──────

    private data class ParsedSkill(
        val name: String,
        val description: String,
        val version: String,
        val body: String,
    )

    private fun parseSkillMd(content: String): ParsedSkill? {
        val trimmed = content.removePrefix("\uFEFF").trimStart()
        if (!trimmed.startsWith("---")) return null
        val lines = trimmed.lines()
        var end = -1
        for (index in 1 until lines.size) {
            if (lines[index].trim() == "---") {
                end = index
                break
            }
        }
        if (end < 0) return null

        var name = ""
        var description = ""
        var version = "1.0.0"

        var i = 1
        while (i < end) {
            val line = lines[i]
            val colon = line.indexOf(':')
            if (colon < 0) {
                i++
                continue
            }
            val key = line.substring(0, colon).trim().lowercase()
            val rawValue = line.substring(colon + 1).trim()
            val isBlock = rawValue.startsWith("|") || rawValue.startsWith(">")
            val resolved: String
            if (isBlock && i + 1 < end) {
                val fold = rawValue.startsWith(">")
                val block = mutableListOf<String>()
                var j = i + 1
                while (j < end) {
                    val next = lines[j]
                    if (next.isEmpty() || next.first().isWhitespace()) {
                        block += next.trim()
                        j++
                    } else {
                        break
                    }
                }
                resolved = if (fold) block.joinToString(" ").trim() else block.joinToString("\n").trim('\n')
                i = j
            } else {
                resolved = unquote(rawValue)
                i++
            }
            when (key) {
                "name" -> name = resolved
                "description" -> description = resolved
                "version" -> version = resolved.ifBlank { "1.0.0" }
            }
        }
        val body = if (end + 1 < lines.size) {
            lines.subList(end + 1, lines.size).joinToString("\n").trim('\n')
        } else {
            ""
        }
        return ParsedSkill(name, description, version, body)
    }

    private fun unquote(value: String): String {
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            val inner = value.substring(1, value.length - 1)
            return buildString {
                var escaped = false
                inner.forEach { ch ->
                    if (escaped) {
                        append(ch)
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else {
                        append(ch)
                    }
                }
            }
        }
        if (value.length >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length - 1).replace("''", "'")
        }
        return value
    }
}
