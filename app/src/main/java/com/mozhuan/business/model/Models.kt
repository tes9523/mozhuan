package com.mozhuan.business.model

/** 书稿（项目）元数据。 */
data class Book(
    val id: String,
    val title: String,
    val folderUri: String,
    val createdAt: Long,
    val updatedAt: Long,
    val chapterCount: Int,
)

/** 章节元数据，正文位于独立的 Markdown 文件中。 */
data class Chapter(
    val id: String,
    val bookId: String,
    val orderIndex: Int,
    val title: String,
    val fileUri: String,
    val fileName: String,
    val wordCount: Int,
    val updatedAt: Long,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun from(value: String): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class ExportFormat(val extension: String, val mimeType: String) {
    TXT("txt", "text/plain"),
    MARKDOWN("md", "text/markdown"),
}

enum class ExportScope {
    CHAPTER,
    BOOK,
}

data class ExportPayload(
    val fileName: String,
    val mimeType: String,
    val content: String,
)
