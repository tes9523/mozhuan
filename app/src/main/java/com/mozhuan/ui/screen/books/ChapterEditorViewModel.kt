package com.mozhuan.ui.screen.books

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.Book
import com.mozhuan.data.model.Chapter
import com.mozhuan.data.repository.BookRepository
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.data.repository.SkillRepository
import com.mozhuan.net.AiClient
import com.mozhuan.net.model.AiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call

/**
 * 章节编辑 ViewModel：基础文本编辑 + SSE 流式续写。
 *
 * 续写固定使用内置「网文章节续写」技能，并注入全书上下文
 * （书名、世界观、人物、前文章节内容）。
 */
class ChapterEditorViewModel(
    private val bookId: String,
    private val chapterId: String,
    private val bookRepository: BookRepository,
    private val skillRepository: SkillRepository,
    private val settingsRepository: SettingsRepository,
    private val aiClient: AiClient,
) : ViewModel() {

    private val initialBook = bookRepository.getBook(bookId)
    private val initialChapter = initialBook?.chapters?.firstOrNull { it.id == chapterId }

    private val _title = MutableStateFlow(initialChapter?.title.orEmpty())
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow(initialChapter?.content.orEmpty())
    val content: StateFlow<String> = _content.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var activeCall: Call? = null
    private var cancelledByUser = false

    fun onTitleChange(text: String) {
        _title.value = text
        bookRepository.updateChapter(bookId, chapterId, title = text)
    }

    fun onContentChange(text: String) {
        _content.value = text
        bookRepository.updateChapter(bookId, chapterId, content = text)
    }

    fun clearError() {
        _error.value = null
    }

    /** 调用同一个 AI 接口，流式续写本章正文。 */
    fun continueWriting() {
        if (_isGenerating.value) return
        val config = settingsRepository.aiConfig()
        if (config.baseUrl.isBlank() || config.apiKey.isBlank()) {
            _error.value = "请先到设置页配置接口地址与 API Key"
            return
        }
        val book = bookRepository.getBook(bookId) ?: run {
            _error.value = "书稿不存在"
            return
        }
        val chapter = book.chapters.firstOrNull { it.id == chapterId } ?: run {
            _error.value = "章节不存在"
            return
        }

        cancelledByUser = false
        _error.value = null
        _isGenerating.value = true

        // 与已有正文之间留出空行，避免续写内容粘连。
        if (_content.value.isNotBlank() && !_content.value.endsWith("\n")) {
            onContentChange(_content.value + "\n\n")
        }

        val skill = skillRepository.getById(SkillRepository.BUILTIN_CONTINUE_ID)
        val messages = buildList {
            if (skill != null) add(AiMessage(role = "system", content = skill.systemPrompt))
            add(AiMessage(role = "user", content = buildContextPrompt(book, chapter)))
        }

        activeCall = aiClient.streamChat(
            config = config,
            messages = messages,
            onDelta = { delta -> onContentChange(_content.value + delta) },
            onComplete = { _isGenerating.value = false },
            onError = { message ->
                if (!cancelledByUser) _error.value = message
                _isGenerating.value = false
            },
        )
    }

    /** 中止续写，保留已生成正文。 */
    fun stop() {
        cancelledByUser = true
        activeCall?.cancel()
        activeCall = null
        _isGenerating.value = false
    }

    override fun onCleared() {
        activeCall?.cancel()
        super.onCleared()
    }

    /** 组装全书上下文提示词。 */
    private fun buildContextPrompt(book: Book, chapter: Chapter): String = buildString {
        append("【书名】").append(book.title).append("\n\n")
        if (book.worldview.isNotBlank()) {
            append("【世界观设定】\n").append(book.worldview).append("\n\n")
        }
        if (book.characters.isNotBlank()) {
            append("【人物设定】\n").append(book.characters).append("\n\n")
        }
        val index = book.chapters.indexOfFirst { it.id == chapter.id }
        if (index > 0) {
            append("【前文章节】\n")
            book.chapters.take(index).forEach { prev ->
                append("《").append(prev.title).append("》\n")
                append(prev.content.take(1200)).append("\n\n")
            }
        }
        append("【当前章节】").append(chapter.title).append("\n")
        append(chapter.content.ifBlank { "（本章尚无正文）" })
        append("\n\n请承接以上内容继续写作本章，直接输出正文。")
    }
}
