package com.mozhuan.ui.screen.books

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.Novel
import com.mozhuan.data.model.NovelEntry
import com.mozhuan.data.repository.BooksRepository
import kotlinx.coroutines.flow.StateFlow

/** 书稿管理：列表、创建、导入与目录浏览。 */
class BooksViewModel(
    private val repository: BooksRepository,
) : ViewModel() {

    val books: StateFlow<List<Novel>> = repository.books

    fun refresh() = repository.reload()

    fun createBook(title: String, coverIndex: Int): Novel =
        repository.createBook(title, coverIndex)

    fun deleteBook(bookId: String) = repository.deleteBook(bookId)

    fun entries(bookId: String, path: String): List<NovelEntry> =
        repository.listEntries(bookId, path)

    fun readFile(bookId: String, path: String): String? = repository.readFile(bookId, path)

    fun writeFile(bookId: String, path: String, content: String): Boolean =
        repository.writeFile(bookId, path, content)

    fun importBook(fileName: String, bytes: ByteArray): Result<Novel> =
        repository.importBook(fileName, bytes)
}
