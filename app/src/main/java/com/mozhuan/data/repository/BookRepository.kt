package com.mozhuan.data.repository

import com.mozhuan.data.model.Book
import com.mozhuan.data.model.Chapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 书稿数据仓库（本阶段仅内存存储，退出应用后丢失）。
 *
 * 后续接入本地持久化时，只需要替换本类的内部实现，对外接口保持不变。
 */
class BookRepository {

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    /** 新建书稿。 */
    fun createBook(title: String): Book {
        val book = Book(title = title.trim())
        _books.update { it + book }
        return book
    }

    fun getBook(id: String): Book? = _books.value.firstOrNull { it.id == id }

    /** 新增章节，返回新章节。 */
    fun addChapter(bookId: String, title: String): Chapter {
        val chapter = Chapter(title = title.trim())
        updateBook(bookId) { book -> book.copy(chapters = book.chapters + chapter) }
        return chapter
    }

    /** 更新章节标题 / 正文。 */
    fun updateChapter(bookId: String, chapterId: String, title: String? = null, content: String? = null) {
        updateBook(bookId) { book ->
            book.copy(
                chapters = book.chapters.map { chapter ->
                    if (chapter.id != chapterId) chapter else chapter.copy(
                        title = title ?: chapter.title,
                        content = content ?: chapter.content,
                    )
                },
            )
        }
    }

    fun updateWorldview(bookId: String, text: String) =
        updateBook(bookId) { it.copy(worldview = text) }

    fun updateCharacters(bookId: String, text: String) =
        updateBook(bookId) { it.copy(characters = text) }

    /** 设置本书绑定的技能（覆盖式）。 */
    fun setBoundSkills(bookId: String, skillIds: List<String>) =
        updateBook(bookId) { it.copy(boundSkillIds = skillIds.distinct()) }

    fun deleteBook(bookId: String) {
        _books.update { list -> list.filterNot { it.id == bookId } }
    }

    private inline fun updateBook(bookId: String, transform: (Book) -> Book) {
        _books.update { list ->
            list.map { if (it.id == bookId) transform(it) else it }
        }
    }
}
