package com.mozhuan.data.repository

import android.content.Context
import com.mozhuan.data.model.Skill
import com.mozhuan.data.model.SkillFile
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
        installBundledSkills()
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
        require(skillId != Skill.BUILT_IN_SKILL_CREATOR_ID) { "内置 skill-creator 不可修改" }
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
        require(skillId != Skill.BUILT_IN_SKILL_CREATOR_ID) { "内置 skill-creator 不可删除" }
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
        val skillId = UUID.randomUUID().toString()
        val dir = File(skillsDir, skillId)
        dir.mkdirs()
        val skill = Skill(
            id = skillId,
            name = name.trim(),
            description = parsed?.description.orEmpty(),
            version = parsed?.version?.ifBlank { "1.0.0" } ?: "1.0.0",
            body = body,
            enabled = false,
            updatedAt = System.currentTimeMillis(),
        )
        // 完整保留原始 SKILL.md（含 frontmatter）；没有 frontmatter 时才补规范头。
        File(dir, "SKILL.md").writeText(
            if (parsed != null) content.trimEnd() + "\n" else serialize(skill),
        )
        preferences.setSkillEnabled(skillId, false)
        reload()
        skill
    }

    /** 导出为完整 SKILL.md 文本（优先导出目录里的原始文件）。 */
    fun exportMarkdown(skill: Skill): String =
        readSkillMarkdown(skill.id).ifBlank { serialize(skill) }

    /** 读取技能目录里真实的 SKILL.md（含 frontmatter 原文）。 */
    fun readSkillMarkdown(skillId: String): String {
        val file = File(File(skillsDir, skillId), "SKILL.md")
        return if (file.isFile) runCatching { file.readText() }.getOrDefault("") else ""
    }

    /**
     * 以整份 SKILL.md 文本保存技能：名称、描述、版本都从 frontmatter 解析，
     * 正文原样写回，保留未知字段与排版。
     */
    fun saveDocument(id: String?, rawContent: String, enabled: Boolean = true): Skill {
        val skillId = id ?: UUID.randomUUID().toString()
        require(skillId != Skill.BUILT_IN_SKILL_CREATOR_ID) { "内置 skill-creator 不可修改" }
        val parsed = parseSkillMd(rawContent)
        val name = parsed?.name?.trim().orEmpty().ifBlank { "未命名技能" }
        val body = parsed?.body?.takeIf { it.isNotBlank() } ?: rawContent.trim()
        require(body.isNotBlank()) { "SKILL.md 内容为空" }
        val dir = File(skillsDir, skillId)
        dir.mkdirs()
        val skill = Skill(
            id = skillId,
            name = name,
            description = parsed?.description?.trim().orEmpty(),
            version = parsed?.version?.ifBlank { "1.0.0" } ?: "1.0.0",
            body = body,
            enabled = enabled,
            updatedAt = System.currentTimeMillis(),
        )
        val text = if (parsed != null) rawContent.trimEnd() + "\n" else serialize(skill)
        File(dir, "SKILL.md").writeText(text)
        preferences.setSkillEnabled(skillId, enabled)
        reload()
        return skill
    }

    /** 保存引用文件（references / scripts 等文本附件）。 */
    fun saveAttachment(skillId: String, relativePath: String, content: String): Boolean {
        require(skillId != Skill.BUILT_IN_SKILL_CREATOR_ID) { "内置 skill-creator 不可修改" }
        if (relativePath == "SKILL.md" || relativePath.startsWith("assets/")) return false
        if (!isSafePath(relativePath)) return false
        val file = File(File(skillsDir, skillId), relativePath)
        if (!file.isFile) return false
        return runCatching {
            file.writeText(content)
            true
        }.getOrDefault(false)
    }

    /**
     * 从 zip 压缩包导入技能：支持根目录 `SKILL.md` 或一层子目录 `xxx/SKILL.md`，
     * 同级附件（scripts / references / assets 等）按相对路径解压到技能目录。
     * 导入的技能默认停用。
     */
    fun importArchive(input: InputStream): Result<Skill> = runCatching {
        val entries = readArchiveEntries(input)
        val skillEntry = entries.firstOrNull { it.name == "SKILL.md" }
            ?: entries.firstOrNull { entry ->
                val segments = entry.name.split('/')
                segments.size == 2 && segments[1] == "SKILL.md"
            }
            ?: throw IllegalArgumentException("压缩包中未找到 SKILL.md")

        val prefix = if (skillEntry.name == "SKILL.md") {
            ""
        } else {
            skillEntry.name.substringBeforeLast('/').trimEnd('/') + "/"
        }
        val content = String(skillEntry.bytes, Charsets.UTF_8)
        val parsed = parseSkillMd(content)
        val fallbackName = prefix.trimEnd('/').substringAfterLast('/')
            .takeIf { it.isNotBlank() }
        val name = parsed?.name?.takeIf { it.isNotBlank() }
            ?: fallbackName
            ?: content.lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trimStart('#', ' ', '\t')
                ?.take(40)
                ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("无法识别技能名称")
        val body = parsed?.body?.takeIf { it.isNotBlank() } ?: content.trim()
        require(body.isNotBlank()) { "SKILL.md 内容为空" }

        val skillId = UUID.randomUUID().toString()
        val dir = File(skillsDir, skillId)
        dir.mkdirs()
        val skill = Skill(
            id = skillId,
            name = name.trim(),
            description = parsed?.description.orEmpty(),
            version = parsed?.version?.ifBlank { "1.0.0" } ?: "1.0.0",
            body = body.trim(),
            enabled = false,
            updatedAt = System.currentTimeMillis(),
        )
        // 完整保留压缩包里的原始 SKILL.md，name / description 不会被重写。
        File(dir, "SKILL.md").writeText(
            if (parsed != null) content.trimEnd() + "\n" else serialize(skill),
        )

        entries.forEach { entry ->
            if (entry.name == skillEntry.name) return@forEach
            var relative = entry.name
            if (prefix.isNotEmpty()) {
                if (!relative.startsWith(prefix)) return@forEach
                relative = relative.removePrefix(prefix)
            }
            if (!isSafePath(relative)) return@forEach
            val target = File(dir, relative)
            target.parentFile?.mkdirs()
            target.writeBytes(entry.bytes)
        }

        preferences.setSkillEnabled(skillId, false)
        reload()
        skill
    }

    /** 内置技能每次扫描都清空目录并重写 canonical 文件，旧内容与外部改动不会残留。 */
    private fun installBundledSkills() {
        val parsed = parseSkillMd(BundledSkills.SKILL_CREATOR_MD) ?: return
        val dir = File(skillsDir, Skill.BUILT_IN_SKILL_CREATOR_ID)
        // 先清空目录，避免旧版本正文、附件或外部改动留下痕迹。
        dir.listFiles()?.forEach { it.deleteRecursively() }
        dir.mkdirs()
        val skill = Skill(
            id = Skill.BUILT_IN_SKILL_CREATOR_ID,
            name = parsed.name,
            description = parsed.description,
            version = BundledSkills.SKILL_CREATOR_VERSION,
            body = parsed.body,
            enabled = Skill.BUILT_IN_SKILL_CREATOR_ID !in preferences.disabledSkillIds.value,
            updatedAt = System.currentTimeMillis(),
        )
        File(dir, "SKILL.md").writeText(serialize(skill))
        val references = File(dir, "references").apply { mkdirs() }
        File(references, "templates.md").writeText(BundledSkills.SKILL_CREATOR_TEMPLATES_MD)
    }

    // ── zip 解压 ─────────────────────────────────────────────────

    private data class ArchiveEntry(
        val name: String,
        val bytes: ByteArray,
    )

    /** 把压缩包内所有文件读入内存，同时做路径与体积校验。 */
    private fun readArchiveEntries(input: InputStream): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        var totalBytes = 0L
        val zip = ZipInputStream(input)
        try {
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.orEmpty().replace('\\', '/')
                if (entry.isDirectory || name.endsWith("/")) {
                    zip.closeEntry()
                    continue
                }
                if (!isSafePath(name)) {
                    zip.closeEntry()
                    continue
                }
                val bytes = readEntryBytes(zip, name)
                totalBytes += bytes.size
                if (totalBytes > MAX_ARCHIVE_TOTAL_BYTES) {
                    throw IllegalArgumentException("压缩包过大")
                }
                entries += ArchiveEntry(name, bytes)
                zip.closeEntry()
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("压缩包损坏或格式不正确")
        } finally {
            runCatching { zip.close() }
        }
        if (entries.isEmpty()) throw IllegalArgumentException("压缩包内没有可用文件")
        return entries
    }

    private fun readEntryBytes(zip: ZipInputStream, name: String): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var size = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            size += read
            if (size > MAX_ARCHIVE_FILE_BYTES) {
                throw IllegalArgumentException("压缩包内文件过大：$name")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    /** 拒绝绝对路径、含 `..`、空段与隐藏文件。 */
    private fun isSafePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith("/")) return false
        if (path.contains("..")) return false
        return path.split('/').none { it.isEmpty() || it == "." || it.startsWith(".") }
    }

    fun skillMdPath(skillId: String): String = "/var/minis/skills/$skillId/SKILL.md"

    /** 技能目录中的附件文件（不含 SKILL.md）；assets/ 属输出资源，不提供编辑。 */
    fun listAttachments(skillId: String): List<SkillFile> {
        val dir = File(skillsDir, skillId)
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.name != "SKILL.md" }
            .map { file ->
                val relative = file.relativeTo(dir).path.replace('\\', '/')
                SkillFile(
                    path = relative,
                    sizeBytes = file.length(),
                    editable = !relative.startsWith("assets/"),
                )
            }
            .sortedBy { it.path }
            .toList()
    }

    /** 读取技能附件文本；路径越界或文件不存在时返回 null。 */
    fun readAttachment(skillId: String, relativePath: String): String? {
        if (!isSafePath(relativePath)) return null
        val file = File(File(skillsDir, skillId), relativePath)
        if (!file.isFile || file.length() > MAX_ARCHIVE_FILE_BYTES) return null
        return runCatching { file.readText() }.getOrNull()
    }

    /** 技能含附件时导出为 zip（SKILL.md + 全部附件）；没有附件时返回 null。 */
    fun exportArchive(skill: Skill): ByteArray? {
        val dir = File(skillsDir, skill.id)
        val attachments = dir.walkTopDown()
            .filter { it.isFile && it.name != "SKILL.md" }
            .toList()
        if (attachments.isEmpty()) return null
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("SKILL.md"))
            zip.write(exportMarkdown(skill).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            attachments.sortedBy { it.path }.forEach { file ->
                val relative = file.relativeTo(dir).path.replace('\\', '/')
                zip.putNextEntry(ZipEntry(relative))
                zip.write(file.readBytes())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

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

    companion object {
        private const val MAX_ARCHIVE_FILE_BYTES = 5L * 1024 * 1024
        private const val MAX_ARCHIVE_TOTAL_BYTES = 30L * 1024 * 1024
    }
}
