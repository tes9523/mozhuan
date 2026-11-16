package com.mozhuan.ui.screen.chat

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.ChatMessage
import com.mozhuan.data.model.ChatRole
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.net.AiClient
import com.mozhuan.net.model.AiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Call

/**
 * AI 对话 ViewModel（全局对话）。
 *
 * 统一读取设置页的接口地址 / 模型 / 参数，并自动注入 SOUL.md 人格 Prompt 与上下文。
 */
class AiChatViewModel(
    private val settingsRepository: SettingsRepository,
    private val aiClient: AiClient,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var activeCall: Call? = null
    private var cancelledByUser = false

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun clearMessages() {
        if (_isStreaming.value) return
        _messages.value = emptyList()
    }

    /** 发送当前输入，发起 SSE 流式对话。 */
    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty() || _isStreaming.value) return

        val settings = settingsRepository.currentApiSettings()
        if (!settings.isConfigured) {
            _error.value = "请先在「设置 → LLM 提供商 → 管理提供商」完成配置"
            return
        }

        cancelledByUser = false
        _error.value = null
        _input.value = ""
        _messages.update { it + ChatMessage(role = ChatRole.USER, content = text) }
        _messages.update { it + ChatMessage(role = ChatRole.ASSISTANT, content = "") }
        _isStreaming.value = true

        activeCall = aiClient.streamChat(
            settings = settings,
            messages = buildApiMessages(settings.contextTurns),
            onDelta = { delta -> appendToLast(delta) },
            onComplete = {
                _isStreaming.value = false
                activeCall = null
            },
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
                activeCall = null
            },
        )
    }

    /** 停止生成，保留已生成内容。 */
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

    /** 人格 Prompt + 指定轮数的历史上下文（不含正在生成的空占位消息）。 */
    private fun buildApiMessages(contextTurns: Int): List<AiMessage> = buildList {
        val persona = settingsRepository.currentPersona().trim()
        if (persona.isNotEmpty()) {
            add(AiMessage(role = "system", content = persona))
        }

        val history = _messages.value.dropLast(1)
        val limited = if (contextTurns <= 0) {
            history.takeLast(1)
        } else {
            history.takeLast(contextTurns * 2)
        }
        limited.forEach { message ->
            if (message.content.isNotBlank()) {
                add(AiMessage(role = message.role.apiValue, content = message.content))
            }
        }
    }
}
