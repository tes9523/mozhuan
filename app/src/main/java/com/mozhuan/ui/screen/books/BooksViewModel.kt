package com.mozhuan.ui.screen.books

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.data.model.Novel
import com.mozhuan.data.model.NovelEntry
import com.mozhuan.data.model.TrashedNovel
import com.mozhuan.data.repository.BookDeleteResult
import com.mozhuan.data.repository.BooksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BooksViewModel(
    private val repository: BooksRepository,
) : ViewModel() {

    val books: StateFlow<List<Novel>> = repository.books
    val trash: StateFlow<List<TrashedNovel>> = repository.trash.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = repository.trash.value,
    )

    fun refresh() {
        repository.reload()
    }

    fun createBook(title: String, coverIndex: Int): Novel {
        val novel = repository.createBook(title, coverIndex)
        repository.autoPurgeExpired()
        return novel
    }

    fun entries(bookId: String, relativePath: String): List<NovelEntry> {
        repository.ensureBookOnEnter(bookId)
        return repository.listEntries(bookId, relativePath)
    }

    fun readFile(bookId: String, relativePath: String): String? = repository.readFile(bookId, relativePath)

    fun writeFile(bookId: String, relativePath: String, content: String): Boolean =
        repository.writeFile(bookId, relativePath, content)

    /** 导入在 IO 线程执行，避免大文件卡住界面。 */
    fun importBook(fileName: String, bytes: ByteArray, onResult: (Result<Novel>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.importBook(fileName, bytes) }
            onResult(result)
        }
    }

    fun deleteBook(bookId: String): BookDeleteResult = repository.deleteBook(bookId)

    fun restoreFromTrash(trashId: String): Boolean = repository.restoreFromTrash(trashId)

    fun purgeTrash(trashId: String): Boolean = repository.purgeTrash(trashId)

    fun purgeAllTrash() {
        repository.purgeAllTrash()
    }

    fun setCoverFromGallery(bookId: String, uri: Uri): Result<String> =
        repository.setCoverFromGallery(bookId, uri)

    fun setBuiltInCover(bookId: String, coverIndex: Int) = repository.setBuiltInCover(bookId, coverIndex)

    fun coverFile(bookId: String) = repository.coverFile(bookId)

    fun ensureStructure(bookId: String) = repository.ensureStructure(bookId)
}
