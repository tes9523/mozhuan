package com.mozhuan.data.model

/** 书稿：`book/<书名>/` 目录下的一本小说。 */
data class Novel(
    val id: String,
    val title: String,
    val coverIndex: Int,
    /** 上传的封面相对路径（相对 book 根），为 null 表示使用内置渐变。 */
    val customCover: String? = null,
    val chapterCount: Int,
    val updatedAt: Long,
)

/** 书稿目录里的一个条目（子目录或文本文件）。 */
data class NovelEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    /** 文件字数（按字符数统计）。 */
    val wordCount: Int = 0,
    val updatedAt: Long,
)

/** 回收站里的一本小说。 */
data class TrashedNovel(
    val id: String,
    val title: String,
    val originalPath: String,
    val trashedAt: Long,
    val totalBytes: Long,
)
