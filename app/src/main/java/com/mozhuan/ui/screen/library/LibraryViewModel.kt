package com.mozhuan.ui.screen.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.business.model.Book
import com.mozhuan.business.repository.BookRepository
import com.mozhuan.business.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val storageSelected: Boolean = false,
    val storageReady: Boolean = false,
    val importing: Boolean = false,
)

class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val importing = MutableStateFlow(false)
    private val storageReady = MutableStateFlow(false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val books = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<LibraryUiState> = combine(
        books,
        importing,
        storageReady,
        settingsRepository.rootUri,
    ) { books, importing, ready, rootUri ->
        LibraryUiState(
            books = books,
            storageSelected = rootUri != null,
            storageReady = ready && rootUri != null,
            importing = importing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        refreshStorageAccess()
    }

    fun refreshStorageAccess() {
        storageReady.value = settingsRepository.hasStorageAccess()
    }

    fun selectStorage(uri: Uri) {
        viewModelScope.launch {
            settingsRepository.selectStorage(uri)
                .onSuccess {
                    refreshStorageAccess()
                    _message.value = "存储目录已就绪"
                }
                .onFailure { error ->
                    _message.value = error.message ?: "存储目录授权失败"
                }
        }
    }

    fun importBook(uri: Uri, onImported: (Book) -> Unit) {
        viewModelScope.launch {
            importing.value = true
            bookRepository.importBook(uri)
                .onSuccess(onImported)
                .onFailure { error ->
                    _message.value = error.message ?: "导入书稿失败"
                }
            importing.value = false
        }
    }

    fun createBook(title: String, onCreated: (Book) -> Unit) {
        viewModelScope.launch {
            bookRepository.createBlankBook(title)
                .onSuccess(onCreated)
                .onFailure { error ->
                    _message.value = error.message ?: "新建书稿失败"
                }
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            bookRepository.deleteBook(book)
                .onSuccess { _message.value = "已删除《${book.title}》" }
                .onFailure { error ->
                    _message.value = error.message ?: "删除书稿失败，请检查存储目录权限"
                }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
