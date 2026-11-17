package com.mozhuan.data.model

/** 书稿：`book/<书名>/` 目录下的一本小说。 */
data class Novel(
    val id: String,
    val title: String,
    val coverIndex: Int,
    val chapterCount: Int,
    val updatedAt: Long,
)

/** 书稿目录里的一个条目（子目录或文本文件）。 */
data class NovelEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val updatedAt: Long,
)
