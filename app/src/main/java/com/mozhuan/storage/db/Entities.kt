package com.mozhuan.storage.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 书稿元数据，仅保存索引信息，不保存正文。 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderUri: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 章节元数据，正文保存在用户目录下的独立 .md 文件中。 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    @ColumnInfo(name = "orderIndex") val orderIndex: Int,
    val title: String,
    val fileUri: String,
    val fileName: String,
    val wordCount: Int,
    val updatedAt: Long,
)

/** 列表页展示用的聚合结果，chapterCount 由子查询得到。 */
data class BookSummaryRow(
    val id: String,
    val title: String,
    val folderUri: String,
    val createdAt: Long,
    val updatedAt: Long,
    val chapterCount: Int,
)
