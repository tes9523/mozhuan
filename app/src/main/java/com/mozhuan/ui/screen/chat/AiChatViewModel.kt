package com.mozhuan.ui.screen.chat

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.ApiSettings
import com.mozhuan.data.model.ChatMessage
import com.mozhuan.data.model.ChatRole
import com.mozhuan.data.model.DisplaySettings
import com.mozhuan.data.model.TokenUsage
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.data.repository.SkillRepository
import com.mozhuan.data.repository.TokenUsageRepository
import com.mozhuan.net.AiClient
import com.mozhuan.net.model.AiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Call

/** 全局 AI 对话：人格 + 已启用技能 + 上下文，统一使用默认服务商。 */
class AiChatViewModel(
    private val settingsRepository: SettingsRepository,
    private val skillRepository: SkillRepository,
    private val tokenUsageRepository: TokenUsageRepository,
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

    val displaySettings: StateFlow<DisplaySettings> = settingsRepository.displaySettings

    private var activeCall: Call? = null
    private var cancelledByUser = false

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun clearMessages() {
        if (_isStreaming.value) return
        _messages.value = emptyList()
        _error.value = null
        tokenUsageRepository.clearSession()
    }

    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty() || _isStreaming.value) return
        val settings = currentSettingsOrError() ?: return

        _input.value = ""
        _messages.update {
            it + ChatMessage(role = ChatRole.USER, content = text) +
                ChatMessage(role = ChatRole.ASSISTANT, content = "")
        }
        startGeneration(settings)
    }

    /** 从指定 AI 消息重新生成，丢弃该消息及其后的分支内容。 */
    fun regenerate(messageId: String) {
        if (_isStreaming.value) return
        val index = _messages.value.indexOfFirst { it.id == messageId }
        if (index <= 0) return
        val current = _messages.value
        if (current[index].role != ChatRole.ASSISTANT || current[index - 1].role != ChatRole.USER) {
            return
        }
        val settings = currentSettingsOrError() ?: return

        _messages.value = current.take(index) + ChatMessage(role = ChatRole.ASSISTANT, content = "")
        startGeneration(settings)
    }

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

    private fun currentSettingsOrError(): ApiSettings? {
        val settings = settingsRepository.currentApiSettings()
        if (!settings.isConfigured) {
            _error.value = "请先在「设置 → LLM 提供商 → 管理提供商」完成默认服务商配置"
            return null
        }
        return settings
    }

    private fun startGeneration(settings: ApiSettings) {
        cancelledByUser = false
        _error.value = null
        _isStreaming.value = true
        var lastReportedUsage = TokenUsage()

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
            onUsage = { reported ->
                val delta = TokenUsage(
                    inputTokens = (reported.inputTokens - lastReportedUsage.inputTokens).coerceAtLeast(0),
                    outputTokens = (reported.outputTokens - lastReportedUsage.outputTokens).coerceAtLeast(0),
                )
                tokenUsageRepository.record(settings, delta)
                lastReportedUsage = reported
            },
        )
    }

    private fun appendToLast(delta: String) {
        _messages.update { list ->
            if (list.isEmpty()) return@update list
            list.dropLast(1) + list.last().copy(content = list.last().content + delta)
        }
    }

    /** 合并人格与开启技能，并携带指定轮数的历史上下文。 */
    private fun buildApiMessages(contextTurns: Int): List<AiMessage> = buildList {
        val systemPrompt = buildString {
            val persona = settingsRepository.currentPersona().trim()
            if (persona.isNotEmpty()) append(persona)
            skillRepository.enabledSkills().forEach { skill ->
                if (isNotEmpty()) append("\n\n")
                append("# 技能：").append(skill.name.trim()).append('\n')
                append(skill.prompt.trim())
            }
        }
        if (systemPrompt.isNotBlank()) {
            add(AiMessage(role = "system", content = systemPrompt))
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
