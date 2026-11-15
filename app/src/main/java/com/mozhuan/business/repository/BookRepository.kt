package com.mozhuan.business.repository

import android.net.Uri
import androidx.room.withTransaction
import com.mozhuan.business.exporter.PlainTextConverter
import com.mozhuan.business.importer.ChapterSplitter
import com.mozhuan.business.model.Book
import com.mozhuan.business.model.Chapter
import com.mozhuan.business.model.ExportFormat
import com.mozhuan.business.model.ExportPayload
import com.mozhuan.business.util.FileNames
import com.mozhuan.business.util.countWords
import com.mozhuan.storage.db.AppDatabase
import com.mozhuan.storage.db.BookDao
import com.mozhuan.storage.db.BookEntity
import com.mozhuan.storage.db.BookSummaryRow
import com.mozhuan.storage.db.ChapterDao
import com.mozhuan.storage.db.ChapterEntity
import com.mozhuan.storage.saf.SafDocumentStore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 书稿业务核心：元数据写入 Room，正文写入用户选择的 SAF 目录。
 */
class BookRepository(
    private val database: AppDatabase,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val safStore: SafDocumentStore,
) {

    fun observeBooks(): Flow<List<Book>> =
        bookDao.observeSummaries().map { rows -> rows.map { it.toBook() } }

    fun observeChapters(bookId: String): Flow<List<Chapter>> =
        chapterDao.observeByBook(bookId).map { rows -> rows.map { it.toChapter() } }

    suspend fun getBook(bookId: String): Book? = withContext(Dispatchers.IO) {
        bookDao.getById(bookId)?.toBook()
    }

    suspend fun readChapter(chapter: Chapter): Result<String> =
        safStore.readTextFile(chapter.fileUri)

    /** 导入 TXT / Markdown，按章节拆分并落盘。 */
    suspend fun importBook(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = safStore.displayName(uri) ?: "导入书稿.txt"
            require(FileNames.isSupportedImport(displayName)) { "仅支持导入 TXT 或 Markdown 文件" }

            val rawText = safStore.readImportedText(uri).getOrThrow()
            val title = FileNames.bookTitleFromFile(displayName)
            val isMarkdown = FileNames.isMarkdown(displayName)
            val splitChapters = ChapterSplitter.split(rawText, isMarkdown, fallbackTitle = "正文")
            require(splitChapters.isNotEmpty()) { "文件内容为空，无法导入" }

            val bookId = UUID.randomUUID().toString()
            val folderUri = safStore.createBookFolder(bookId, FileNames.sanitize(title)).getOrThrow()
            try {
                val now = System.currentTimeMillis()
                val chapterEntities = splitChapters.mapIndexed { index, chapter ->
                    val fileName = FileNames.chapterFileName(index, chapter.title)
                    val fileUri = safStore
                        .writeChapterFile(folderUri, fileName, chapter.content)
                        .getOrThrow()
                    ChapterEntity(
                        id = UUID.randomUUID().toString(),
                        bookId = bookId,
                        orderIndex = index,
                        title = chapter.title,
                        fileUri = fileUri,
                        fileName = fileName,
                        wordCount = countWords(chapter.content),
                        updatedAt = now,
                    )
                }
                val bookEntity = BookEntity(
                    id = bookId,
                    title = title,
                    folderUri = folderUri,
                    createdAt = now,
                    updatedAt = now,
                )
                database.withTransaction {
                    bookDao.insert(bookEntity)
                    chapterDao.insertAll(chapterEntities)
                }
                bookEntity.toBook(chapterEntities.size)
            } catch (throwable: Throwable) {
                safStore.deleteDocumentOrFolder(folderUri)
                throw throwable
            }
        }
    }

    /** 新建空白书稿。 */
    suspend fun createBlankBook(title: String): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanTitle = title.trim().ifBlank { "未命名书稿" }
            val bookId = UUID.randomUUID().toString()
            val folderUri = safStore
                .createBookFolder(bookId, FileNames.sanitize(cleanTitle))
                .getOrThrow()
            val now = System.currentTimeMillis()
            val entity = BookEntity(
                id = bookId,
                title = cleanTitle,
                folderUri = folderUri,
                createdAt = now,
                updatedAt = now,
            )
            bookDao.insert(entity)
            entity.toBook(0)
        }
    }

    suspend fun addChapter(bookId: String): Result<Chapter> = withContext(Dispatchers.IO) {
        runCatching {
            val book = bookDao.getById(bookId) ?: error("书稿不存在")
            val orderIndex = chapterDao.maxOrderIndex(bookId) + 1
            val title = "第 ${orderIndex + 1} 章"
            val fileName = FileNames.chapterFileName(orderIndex, title)
            val fileUri = safStore
                .writeChapterFile(book.folderUri, fileName, "")
                .getOrThrow()
            val now = System.currentTimeMillis()
            val entity = ChapterEntity(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                orderIndex = orderIndex,
                title = title,
                fileUri = fileUri,
                fileName = fileName,
                wordCount = 0,
                updatedAt = now,
            )
            chapterDao.upsert(entity)
            bookDao.touch(bookId, now)
            entity.toChapter()
        }
    }

    /** 保存章节正文，并同步更新字数与时间戳。 */
    suspend fun saveChapter(chapter: Chapter, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val overwrite = safStore.overwriteText(chapter.fileUri, content)
                if (overwrite.isFailure) {
                    val book = bookDao.getById(chapter.bookId) ?: error("书稿不存在")
                    val newUri = safStore
                        .writeChapterFile(book.folderUri, chapter.fileName, content)
                        .getOrThrow()
                    chapterDao.updateFileUri(chapter.id, newUri)
                }
                val now = System.currentTimeMillis()
                chapterDao.updateStats(chapter.id, countWords(content), now)
                bookDao.touch(chapter.bookId, now)
            }
        }

    suspend fun deleteBook(book: Book): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            safStore.deleteDocumentOrFolder(book.folderUri).getOrThrow()
            database.withTransaction { bookDao.deleteById(book.id) }
        }
    }

    /** 组装导出内容（单章或全书）。 */
    suspend fun buildExport(
        bookId: String,
        chapterId: String?,
        format: ExportFormat,
    ): Result<ExportPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val book = bookDao.getById(bookId) ?: error("书稿不存在")
            val allChapters = chapterDao.getByBook(bookId)
            val targets = if (chapterId != null) {
                listOf(allChapters.firstOrNull { it.id == chapterId } ?: error("章节不存在"))
            } else {
                allChapters
            }
            require(targets.isNotEmpty()) { "书稿没有可导出的章节" }

            val parts = targets.map { chapter ->
                chapter to safStore.readTextFile(chapter.fileUri).getOrThrow()
            }
            val singleTitle = if (chapterId != null) targets.first().title else null

            val content = buildString {
                when (format) {
                    ExportFormat.MARKDOWN -> {
                        append("# ").append(book.title).append("\n\n")
                        parts.forEach { (chapter, body) ->
                            append("## ").append(chapter.title).append("\n\n")
                            append(body.trim()).append("\n\n")
                        }
                    }

                    ExportFormat.TXT -> {
                        append(book.title).append("\n\n")
                        parts.forEach { (chapter, body) ->
                            append(chapter.title).append("\n\n")
                            append(PlainTextConverter.convert(body)).append("\n\n")
                        }
                    }
                }
            }.trimEnd() + "\n"

            ExportPayload(
                fileName = FileNames.exportFileName(book.title, singleTitle, format),
                mimeType = format.mimeType,
                content = content,
            )
        }
    }

    suspend fun writeExport(target: Uri, payload: ExportPayload): Result<Unit> =
        safStore.writeToUri(target, payload.content)

    private fun BookEntity.toBook(chapterCount: Int = 0): Book = Book(
        id = id,
        title = title,
        folderUri = folderUri,
        createdAt = createdAt,
        updatedAt = updatedAt,
        chapterCount = chapterCount,
    )

    private fun BookSummaryRow.toBook(): Book = Book(
        id = id,
        title = title,
        folderUri = folderUri,
        createdAt = createdAt,
        updatedAt = updatedAt,
        chapterCount = chapterCount,
    )

    private fun ChapterEntity.toChapter(): Chapter = Chapter(
        id = id,
        bookId = bookId,
        orderIndex = orderIndex,
        title = title,
        fileUri = fileUri,
        fileName = fileName,
        wordCount = wordCount,
        updatedAt = updatedAt,
    )
}
