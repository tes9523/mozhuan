package com.mozhuan.ui.screen.chat

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.Book
import com.mozhuan.data.model.ChatMessage
import com.mozhuan.data.model.ChatRole
import com.mozhuan.data.model.Skill
import com.mozhuan.data.repository.BookRepository
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.data.repository.SkillRepository
import com.mozhuan.net.AiClient
import com.mozhuan.net.model.AiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Call

/**
 * AI 对话 ViewModel。
 *
 * - bookId 为空：全局自由对话，可手动选择全局技能。
 * - bookId 非空：绑定书稿的专属对话，自动注入书名、世界观、人物、章节列表，
 *   并优先使用本书绑定的技能。
 */
class AiChatViewModel(
    private val bookId: String?,
    private val bookRepository: BookRepository,
    private val skillRepository: SkillRepository,
    private val settingsRepository: SettingsRepository,
    private val aiClient: AiClient,
) : ViewModel() {

    /** 是否绑定书稿。 */
    val isBound: Boolean = bookId != null

    private val _book = MutableStateFlow(bookId?.let { bookRepository.getBook(it) })
    val book: StateFlow<Book?> = _book.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val skills: StateFlow<List<Skill>> = skillRepository.skills
    val selectedGlobalSkillId: StateFlow<String> = skillRepository.selectedGlobalSkillId

    /** 当前生效技能：本书绑定技能优先，否则用全局选中技能。 */
    val activeSkill: Skill?
        get() {
            val book = _book.value
            if (book != null && book.boundSkillIds.isNotEmpty()) {
                val bound = book.boundSkillIds.firstNotNullOfOrNull { skillRepository.getById(it) }
                if (bound != null) return bound
            }
            return skillRepository.getById(skillRepository.selectedGlobalSkillId.value)
        }

    private var activeCall: Call? = null
    private var cancelledByUser = false

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun selectGlobalSkill(skillId: String) {
        skillRepository.setSelectedGlobalSkill(skillId)
    }

    fun clearError() {
        _error.value = null
    }

    fun clearMessages() {
        if (_isStreaming.value) return
        _messages.value = emptyList()
    }

    /** 发送当前输入，发起 SSE 流式对话。 */
    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty() || _isStreaming.value) return

        val config = settingsRepository.aiConfig()
        if (config.baseUrl.isBlank() || config.apiKey.isBlank()) {
            _error.value = "请先在设置页配置接口地址与 API Key"
            return
        }

        // 刷新书稿数据，保证注入的是最新设定。
        _book.value = bookId?.let { bookRepository.getBook(it) }
        cancelledByUser = false
        _error.value = null
        _input.value = ""
        _messages.update { it + ChatMessage(role = ChatRole.USER, content = text) }
        _messages.update { it + ChatMessage(role = ChatRole.ASSISTANT, content = "") }
        _isStreaming.value = true

        activeCall = aiClient.streamChat(
            config = config,
            messages = buildApiMessages(),
            onDelta = { delta -> appendToLast(delta) },
            onComplete = { _isStreaming.value = false },
            onError = { message ->
                if (!cancelledByUser) {
                    val last = _messages.value.lastOrNull()
                    val suffix = if (last?.content.isNullOrEmpty()) {
                        "⚠️ $message"
                    } else {
                        "\n\n⚠️ $message"
                    }
                    appendToLast(suffix)
                    _error.value = message
                }
                _isStreaming.value = false
            },
        )
    }

    /** 中止生成，保留已生成内容。 */
    fun stop() {
        cancelledByUser = true
        activeCall?.cancel()
        activeCall = null
        _isStreaming.value = false
    }

    override fun onCleared() {
        activeCall?.cancel()
        super.onCleared()
    }

    private fun appendToLast(delta: String) {
        _messages.update { list ->
            if (list.isEmpty()) return@update list
            list.dropLast(1) + list.last().copy(content = list.last().content + delta)
        }
    }

    /** 组装 system 消息 + 多轮历史。 */
    private fun buildApiMessages(): List<AiMessage> = buildList {
        val systemPrompt = buildSystemPrompt()
        if (systemPrompt.isNotBlank()) add(AiMessage(role = "system", content = systemPrompt))
        _messages.value.dropLast(1).forEach { message ->
            add(AiMessage(role = message.role.apiValue, content = message.content))
        }
    }

    /** 技能 Prompt +（绑定书稿时）本书设定。 */
    private fun buildSystemPrompt(): String {
        val builder = StringBuilder()
        activeSkill?.let { builder.append(it.systemPrompt) }
        val book = _book.value ?: return builder.toString()

        builder.append("\n\n【书名】").append(book.title)
        if (book.worldview.isNotBlank()) {
            builder.append("\n【世界观设定】\n").append(book.worldview)
        }
        if (book.characters.isNotBlank()) {
            builder.append("\n【人物设定】\n").append(book.characters)
        }
        if (book.chapters.isNotEmpty()) {
            builder.append("\n【章节列表】\n")
            book.chapters.forEachIndexed { index, chapter ->
                builder.append("${index + 1}. ").append(chapter.title).append('\n')
            }
        }
        return builder.toString()
    }
}
