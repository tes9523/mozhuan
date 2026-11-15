package com.mozhuan.ui.screen.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.business.model.Book
import com.mozhuan.business.model.Chapter
import com.mozhuan.business.model.ExportFormat
import com.mozhuan.business.model.ExportPayload
import com.mozhuan.business.model.ExportScope
import com.mozhuan.business.repository.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SaveState {
    data object Idle : SaveState
    data object Pending : SaveState
    data object Saving : SaveState
    data class Saved(val at: Long) : SaveState
    data class Failed(val message: String) : SaveState
}

data class EditorUiState(
    val loading: Boolean = true,
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val selectedChapter: Chapter? = null,
    val content: String = "",
    val saveState: SaveState = SaveState.Idle,
    val exporting: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class EditorViewModel(
    private val bookId: String,
    private val repository: BookRepository,
    private val persistScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _pendingExport = MutableStateFlow<ExportPayload?>(null)
    val pendingExport: StateFlow<ExportPayload?> = _pendingExport.asStateFlow()

    private val saveMutex = Mutex()
    private var saveJob: Job? = null
    private var loadedChapterId: String? = null

    init {
        viewModelScope.launch {
            val book = repository.getBook(bookId)
            if (book == null) {
                _uiState.update { it.copy(loading = false, error = "书稿不存在或已被删除") }
                return@launch
            }
            _uiState.update { it.copy(book = book, loading = false) }
            repository.observeChapters(bookId).collect { chapters ->
                val current = _uiState.value
                val selected = chapters.firstOrNull { it.id == current.selectedChapter?.id }
                    ?: chapters.firstOrNull()
                _uiState.update { it.copy(chapters = chapters, selectedChapter = selected) }
                when {
                    selected == null -> {
                        loadedChapterId = null
                        _uiState.update { it.copy(content = "", saveState = SaveState.Idle) }
                    }

                    selected.id != loadedChapterId -> loadChapter(selected)
                }
            }
        }
    }

    fun onContentChanged(text: String) {
        if (_uiState.value.selectedChapter == null) return
        _uiState.update { it.copy(content = text, saveState = SaveState.Pending) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            persistCurrent()
        }
    }

    fun selectChapter(chapter: Chapter) {
        if (chapter.id == loadedChapterId) return
        viewModelScope.launch {
            saveJob?.cancel()
            val snapshot = _uiState.value
            snapshot.selectedChapter?.let { previous ->
                if (previous.id != chapter.id) persistCurrent(previous, snapshot.content)
            }
            loadedChapterId = chapter.id
            _uiState.update {
                it.copy(selectedChapter = chapter, content = "", saveState = SaveState.Idle)
            }
            loadChapter(chapter)
        }
    }

    fun addChapter() {
        viewModelScope.launch {
            saveJob?.cancel()
            val snapshot = _uiState.value
            snapshot.selectedChapter?.let { persistCurrent(it, snapshot.content) }
            repository.addChapter(bookId)
                .onSuccess { chapter ->
                    loadedChapterId = chapter.id
                    _uiState.update {
                        it.copy(
                            selectedChapter = chapter,
                            chapters = it.chapters + chapter,
                            content = "",
                            saveState = SaveState.Idle,
                        )
                    }
                    loadChapter(chapter)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "新增章节失败") }
                }
        }
    }

    /** 页面离开或进入后台时兜底保存。 */
    fun flush() {
        saveJob?.cancel()
        val snapshot = _uiState.value
        val chapter = snapshot.selectedChapter ?: return
        val currentState = snapshot.saveState
        if (currentState !is SaveState.Pending && currentState !is SaveState.Failed) return
        val content = snapshot.content
        persistScope.launch { persistCurrent(chapter, content) }
    }

    fun prepareExport(scope: ExportScope, format: ExportFormat) {
        val chapterId = if (scope == ExportScope.CHAPTER) {
            _uiState.value.selectedChapter?.id
        } else {
            null
        }
        if (scope == ExportScope.CHAPTER && chapterId == null) {
            _uiState.update { it.copy(message = "当前没有可导出的章节") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true) }
            repository.buildExport(bookId, chapterId, format)
                .onSuccess { payload -> _pendingExport.value = payload }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "导出准备失败") }
                }
            _uiState.update { it.copy(exporting = false) }
        }
    }

    fun onExportTargetPicked(uri: Uri?) {
        val payload = _pendingExport.value ?: return
        if (uri == null) {
            _pendingExport.value = null
            return
        }
        viewModelScope.launch {
            repository.writeExport(uri, payload)
                .onSuccess {
                    _uiState.update { it.copy(message = "已导出：${payload.fileName}") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "写入导出文件失败") }
                }
            _pendingExport.value = null
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        super.onCleared()
        flush()
    }

    private suspend fun loadChapter(chapter: Chapter) {
        loadedChapterId = chapter.id
        _uiState.update { it.copy(content = "", saveState = SaveState.Idle) }
        repository.readChapter(chapter)
            .onSuccess { text ->
                if (loadedChapterId == chapter.id) {
                    _uiState.update { it.copy(content = text, saveState = SaveState.Idle) }
                }
            }
            .onFailure { error ->
                if (loadedChapterId == chapter.id) {
                    _uiState.update {
                        it.copy(message = error.message ?: "章节读取失败，请检查存储目录权限")
                    }
                }
            }
    }

    private suspend fun persistCurrent() {
        val snapshot = _uiState.value
        val chapter = snapshot.selectedChapter ?: return
        persistCurrent(chapter, snapshot.content)
    }

    private suspend fun persistCurrent(chapter: Chapter, content: String) {
        saveMutex.withLock {
            if (loadedChapterId == chapter.id) {
                _uiState.update { it.copy(saveState = SaveState.Saving) }
            }
            repository.saveChapter(chapter, content)
                .onSuccess {
                    if (loadedChapterId == chapter.id) {
                        _uiState.update {
                            it.copy(saveState = SaveState.Saved(System.currentTimeMillis()))
                        }
                    }
                }
                .onFailure { error ->
                    val text = error.message ?: "保存失败，请检查存储目录权限"
                    if (loadedChapterId == chapter.id) {
                        _uiState.update {
                            it.copy(saveState = SaveState.Failed(text), message = text)
                        }
                    }
                }
        }
    }

    companion object {
        private const val AUTO_SAVE_DELAY_MS = 600L
    }
}
