package com.mozhuan.ui.screen.books

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.data.model.Novel
import com.mozhuan.data.model.NovelEntry
import com.mozhuan.data.model.TrashedNovel
import com.mozhuan.data.repository.BooksRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    fun importBook(fileName: String, bytes: ByteArray): Result<Novel> = repository.importBook(fileName, bytes)

    fun moveToTrash(bookId: String): Boolean = repository.moveToTrash(bookId)

    fun restoreFromTrash(trashId: String): Boolean = repository.restoreFromTrash(trashId)

    fun purgeTrash(trashId: String): Boolean = repository.purgeTrash(trashId)

    fun purgeAllTrash() {
        repository.purgeAllTrash()
    }

    fun setCoverFromGallery(bookId: String, uri: Uri): String? = repository.setCoverFromGallery(bookId, uri)

    fun setBuiltInCover(bookId: String, coverIndex: Int) = repository.setBuiltInCover(bookId, coverIndex)

    fun coverFile(bookId: String) = repository.coverFile(bookId)

    fun ensureStructure(bookId: String) = repository.ensureStructure(bookId)
}
