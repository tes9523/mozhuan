package com.mozhuan.business.util

import com.mozhuan.business.model.ExportFormat
import java.util.Locale

object FileNames {

    private val illegalChars = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    fun sanitize(name: String): String =
        name.replace(illegalChars, "_")
            .trim()
            .trim('.')
            .take(40)
            .ifBlank { "未命名" }

    fun bookTitleFromFile(displayName: String): String {
        val base = displayName.substringBeforeLast('.', displayName).trim()
        return base.ifBlank { "未命名书稿" }
    }

    fun chapterFileName(orderIndex: Int, title: String): String =
        String.format(Locale.US, "%03d_%s.md", orderIndex + 1, sanitize(title))

    fun exportFileName(bookTitle: String, chapterTitle: String?, format: ExportFormat): String {
        val scope = if (chapterTitle.isNullOrBlank()) "全书" else chapterTitle
        return "${sanitize(bookTitle)}_${sanitize(scope)}.${format.extension}"
    }

    fun isSupportedImport(displayName: String): Boolean {
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.US)
        return extension in setOf("txt", "md", "markdown")
    }

    fun isMarkdown(displayName: String): Boolean {
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.US)
        return extension == "md" || extension == "markdown"
    }
}

/** 中文字数按非空白字符统计。 */
fun countWords(text: String): Int = text.count { !it.isWhitespace() }
