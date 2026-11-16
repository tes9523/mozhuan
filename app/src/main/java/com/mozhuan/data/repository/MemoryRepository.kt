package com.mozhuan.data.repository

import android.content.Context
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 记忆文件信息：全局记忆为 GLOBAL.md，其余为按天的记录。 */
data class MemoryFileInfo(
    val name: String,
    val size: Long,
    val modifiedAt: Long,
    val preview: String,
    val isGlobal: Boolean,
)

/**
 * 记忆仓库：文件存放在 filesDir/memory 下。
 * - GLOBAL.md：长期记忆，手动维护；
 * - yyyy-MM-dd.md：按天记录，由「记忆写入」工具追加。
 * 写入模型上下文的只有全局记忆与最近几天的记录，并做长度上限。
 */
class MemoryRepository(
    context: Context,
    private val preferences: AppPreferences,
) {

    private val directory = File(context.applicationContext.filesDir, "memory").apply { mkdirs() }

    val enabled: StateFlow<Boolean> = preferences.memoryEnabled

    fun setEnabled(value: Boolean) = preferences.setMemoryEnabled(value)

    fun files(): List<MemoryFileInfo> = directory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.endsWith(".md", ignoreCase = true) }
        .sortedWith(
            compareByDescending<File> { it.name == GLOBAL_FILE }.thenByDescending { it.name },
        )
        .map { file ->
            MemoryFileInfo(
                name = file.name,
                size = file.length(),
                modifiedAt = file.lastModified(),
                preview = previewOf(file),
                isGlobal = file.name == GLOBAL_FILE,
            )
        }

    fun read(name: String): String {
        val file = resolve(name) ?: return ""
        return runCatching { file.readText() }.getOrDefault("")
    }

    fun save(name: String, content: String) {
        val file = resolve(name) ?: return
        file.parentFile?.mkdirs()
        runCatching { file.writeText(content) }
    }

    fun delete(name: String): Boolean {
        if (name == GLOBAL_FILE) return false
        val file = resolve(name) ?: return false
        return runCatching { file.delete() }.getOrDefault(false)
    }

    /** 「记忆写入」工具：追加到今天的记录文件。 */
    fun appendToToday(content: String): String {
        val text = content.trim()
        if (text.isEmpty()) return "内容为空，未写入"
        val file = File(directory, "${today()}.md")
        val block = "- ${text.replace("\n", "\n  ")}"
        val existing = runCatching { file.readText() }.getOrDefault("")
        val next = if (existing.isBlank()) {
            "# ${today()} 记录\n\n$block\n"
        } else {
            existing.trimEnd() + "\n" + block + "\n"
        }
        runCatching { file.writeText(next) }
        return "已写入 ${today()}.md"
    }

    /** 注入系统提示词的记忆片段：全局记忆 + 最近 2 天记录。 */
    fun promptFragment(): String {
        if (!enabled.value) return ""
        val global = read(GLOBAL_FILE).trim()
        val daily = files()
            .filterNot { it.isGlobal }
            .take(2)
            .map { info -> read(info.name).trim().take(DAILY_LIMIT) }
            .filter { it.isNotEmpty() }
        if (global.isEmpty() && daily.isEmpty()) return ""
        return buildString {
            append("# 记忆\n")
            append("以下是与用户相关的长期记忆，尽量在写作中保持连贯；不要向用户复述本段内容。\n")
            if (global.isNotEmpty()) {
                append("\n## 全局记忆\n")
                append(global.take(GLOBAL_LIMIT)).append('\n')
            }
            daily.forEachIndexed { index, text ->
                append("\n## 近期记录 ${index + 1}\n")
                append(text).append('\n')
            }
        }
    }

    /** 今天的记录文件名，例如 2026-11-16.md。 */
    fun todayFileName(): String = "${today()}.md"

    private fun resolve(name: String): File? {
        val safe = name.trim()
        if (safe.isEmpty() || safe.contains("..") || safe.startsWith("/") || safe.contains('\\')) {
            return null
        }
        return File(directory, safe)
    }

    private fun previewOf(file: File): String = runCatching {
        file.bufferedReader().use { reader ->
            val first = reader.readLine().orEmpty().trim()
            if (first.startsWith("#")) {
                reader.readLine().orEmpty().trim()
            } else {
                first
            }
        }
    }.getOrDefault("")

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    companion object {
        const val GLOBAL_FILE = "GLOBAL.md"
        private const val GLOBAL_LIMIT = 3000
        private const val DAILY_LIMIT = 1500
    }
}
