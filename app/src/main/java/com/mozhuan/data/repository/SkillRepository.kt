package com.mozhuan.data.repository

import android.content.Context
import com.mozhuan.data.model.Skill
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

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
        installBundledSkills()
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
        File(dir, "SKILL.md").writeText(serialize(skill))

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

    /** 内置 skill-creator：目录不存在则创建，版本低于内置版本时覆盖正文并保留启用状态。 */
    private fun installBundledSkills() {
        val parsed = parseSkillMd(BUNDLED_SKILL_CREATOR) ?: return
        val dir = File(skillsDir, BUNDLED_SKILL_ID)
        val file = File(dir, "SKILL.md")
        val existingVersion = if (file.isFile) {
            runCatching { parseSkillMd(file.readText())?.version }.getOrNull()
        } else {
            null
        }
        if (existingVersion != null && compareVersions(existingVersion, BUNDLED_SKILL_VERSION) >= 0) {
            return
        }

        dir.mkdirs()
        val enabled = if (file.isFile) {
            BUNDLED_SKILL_ID !in preferences.disabledSkillIds.value
        } else {
            true
        }
        val skill = Skill(
            id = BUNDLED_SKILL_ID,
            name = parsed.name,
            description = parsed.description,
            version = BUNDLED_SKILL_VERSION,
            body = parsed.body,
            enabled = enabled,
            updatedAt = System.currentTimeMillis(),
        )
        file.writeText(serialize(skill))
        if (enabled) preferences.setSkillEnabled(BUNDLED_SKILL_ID, true)
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.trim().toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val diff = (a.getOrElse(index) { 0 }) - (b.getOrElse(index) { 0 })
            if (diff != 0) return diff
        }
        return 0
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
        private const val BUNDLED_SKILL_ID = "skill-creator"
        private const val BUNDLED_SKILL_VERSION = "2.0.0"
        private const val MAX_ARCHIVE_FILE_BYTES = 5L * 1024 * 1024
        private const val MAX_ARCHIVE_TOTAL_BYTES = 30L * 1024 * 1024

        /** 内置技能原文：中文原创指南，说明技能结构、渐进式加载与创建流程。 */
        private val BUNDLED_SKILL_CREATOR = """
---
name: skill-creator
description: 当用户想创建、编写或改进一个技能时使用，指导从需求理解到测试迭代，产出规范可用的 SKILL.md 与可选附件。
version: 2.0.0
---

# 技能创建指南

本指南教你从零编写一个高质量技能。技能是给 AI 的「领域说明书」，让它在你指定的任务上表现得更专业、更稳定。

## 什么是技能

技能是一个自包含的目录包，通过 SKILL.md 提供专用知识、工作流程和工具用法。它像一份针对特定任务的上岗手册：把通用助手变成熟悉该领域的专家。

技能适合承载以下内容：

- 多步骤、有固定顺序的工作流程
- 特定文件格式、API 或工具的调用方式
- 团队内部的业务规则、术语表和数据结构
- 可复用的脚本、模板、参考资料

## 目录结构

```
技能名/
├── SKILL.md            # 必需：frontmatter + 正文指令
├── scripts/            # 可选：可执行脚本或代码片段
├── references/         # 可选：按需加载的详细文档
└── assets/             # 可选：模板、图标、示例素材
```

SKILL.md 由两部分组成：

1. YAML frontmatter：位于文件开头的两行 `---` 之间，至少包含 `name` 和 `description`
2. 正文：触发技能后加载的 Markdown 指令

frontmatter 示例：

```
---
name: weekly-report
description: 当用户需要整理本周工作、生成周报或汇总进度时使用。
version: 1.0.0
---
```

`name` 用简短的英文短横线命名；`description` 用一句话写清「做什么」以及「什么时候触发」。description 是唯一的触发依据，务必覆盖用户可能的说法和场景，不要把触发条件只写在正文里。

## 渐进式加载

技能分三层加载，写作者要顺着这个机制分配内容：

1. 元数据（name / description）：始终在上下文中，保持精简
2. SKILL.md 正文：技能触发后加载，控制在 500 行以内
3. 附件（scripts / references / assets）：真正需要时才读取

因此，正文只保留高频、必需的流程；大段参考资料、长表格、示例清单放进 `references/`，并在正文中写明何时读取哪个文件。

## 创建流程

按顺序执行以下五步：

1. **理解**：向用户确认具体任务、输入输出与成功标准，收集 2~3 个真实例子
2. **规划**：判断是否需要附件，划清正文与附件的边界，列出关键步骤
3. **编写**：先写 frontmatter，再按执行顺序写正文，最后补附件
4. **测试**：用真实任务走一遍技能，检查是否漏步骤、是否误触发
5. **迭代**：根据实际使用暴露的问题精简、补充或调整触发描述

不要跳过测试。只在纸面上推演，很容易写出看似完整、实际无法执行的指令。

## 写作要求

- 简洁优先：默认 AI 已经足够聪明，只补充它不知道的信息
- 命令式表达：直接写「先读取 X，再输出 Y」，少用「你可以考虑」这类模糊说法
- 少废话：不解释显而易见的概念，不重复系统提示词已有的内容
- 用短句和列表，步骤之间保持清晰顺序
- 示例优于长篇说明，一个最小可用示例往往胜过三段解释
- 同一份技能内保持术语一致，避免同义词来回切换

## 不要创建的文件

技能目录只放 AI 执行任务真正需要的东西。不要创建 README.md、CHANGELOG.md、INSTALLATION_GUIDE.md 等说明性文件，也不要堆放与任务无关的示例和截图。多余的上下文会稀释重点，增加误读风险。

## 完成前自检

- frontmatter 是否有 name 和 description，格式是否合法
- description 是否写清触发时机
- 正文是否在 500 行以内，是否按执行顺序组织
- 附件路径是否在正文中被明确引用
- 是否存在可以删除而不影响执行的内容
""".trimIndent()
    }
}
