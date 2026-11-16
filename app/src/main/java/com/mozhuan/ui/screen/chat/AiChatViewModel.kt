package com.mozhuan.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.data.model.ActiveModel
import com.mozhuan.data.model.ChatMessage
import com.mozhuan.data.model.ChatRole
import com.mozhuan.data.model.DisplaySettings
import com.mozhuan.data.model.ModelEntry
import com.mozhuan.data.model.ProviderInstance
import com.mozhuan.data.model.TokenUsage
import com.mozhuan.data.repository.ProviderRepository
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.data.repository.SkillRepository
import com.mozhuan.data.repository.TokenUsageRepository
import com.mozhuan.net.AiClient
import com.mozhuan.net.StreamChunk
import com.mozhuan.net.model.AiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 对话页可选的模型（服务商 + 模型条目）。 */
data class ModelOption(
    val provider: ProviderInstance,
    val entry: ModelEntry,
)

/** 全局 AI 对话：人格 + 已启用技能 + 当前选中模型的上下文与参数。 */
class AiChatViewModel(
    private val providerRepository: ProviderRepository,
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
    val selectedEntryId: StateFlow<String> = providerRepository.selectedEntryId

    val modelOptions: StateFlow<List<ModelOption>> =
        combine(providerRepository.providers, providerRepository.entries) { providers, entries ->
            val byId = providers.associateBy { it.id }
            entries.mapNotNull { entry ->
                byId[entry.providerId]?.takeIf { it.isEnabled }?.let { ModelOption(it, entry) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeModel: StateFlow<ActiveModel?> =
        combine(
            providerRepository.providers,
            providerRepository.entries,
            providerRepository.selectedEntryId,
        ) { providers, entries, selectedId ->
            resolveActive(providers, entries, selectedId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var streamJob: Job? = null
    private var cancelledByUser = false

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun selectModel(entryId: String) {
        providerRepository.selectEntry(entryId)
        _error.value = null
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
        val model = activeModel.value
        if (model == null) {
            _error.value = "请先在「设置 → 提供商」添加服务商并选择一个模型"
            return
        }
        if (model.provider.effectiveBaseUrl.isBlank()) {
            _error.value = "当前服务商缺少请求地址，请到提供商设置中补全"
            return
        }

        _input.value = ""
        _messages.update {
            it + ChatMessage(role = ChatRole.USER, content = text) +
                ChatMessage(role = ChatRole.ASSISTANT, content = "")
        }
        startGeneration(model)
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
        val model = activeModel.value ?: run {
            _error.value = "请先在「设置 → 提供商」添加服务商并选择一个模型"
            return
        }

        _messages.value = current.take(index) + ChatMessage(role = ChatRole.ASSISTANT, content = "")
        startGeneration(model)
    }

    fun stop() {
        cancelledByUser = true
        streamJob?.cancel()
        streamJob = null
        _isStreaming.value = false
        finishEmptyMessage()
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }

    private fun startGeneration(model: ActiveModel) {
        cancelledByUser = false
        _error.value = null
        _isStreaming.value = true
        var reportedUsage = TokenUsage()

        streamJob = viewModelScope.launch {
            try {
                aiClient.streamChat(
                    provider = model.provider,
                    entry = model.entry,
                    messages = buildApiMessages(model.entry.contextTurns),
                    systemPrompt = buildSystemPrompt(),
                ).collect { chunk ->
                    when (chunk) {
                        is StreamChunk.Text -> appendToLast { it.copy(content = it.content + chunk.text) }
                        is StreamChunk.Reasoning -> appendToLast {
                            it.copy(reasoning = it.reasoning + chunk.text)
                        }
                        is StreamChunk.Usage -> {
                            val delta = TokenUsage(
                                inputTokens = (chunk.inputTokens - reportedUsage.inputTokens)
                                    .coerceAtLeast(0),
                                outputTokens = (chunk.outputTokens - reportedUsage.outputTokens)
                                    .coerceAtLeast(0),
                            )
                            tokenUsageRepository.record(model.provider, model.entry, delta)
                            reportedUsage = TokenUsage(
                                inputTokens = maxOf(reportedUsage.inputTokens, chunk.inputTokens),
                                outputTokens = maxOf(reportedUsage.outputTokens, chunk.outputTokens),
                            )
                        }
                        StreamChunk.Completed -> Unit
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!cancelledByUser) {
                    val message = error.message?.takeIf { it.isNotBlank() } ?: "生成失败，请稍后重试"
                    _error.value = message
                    appendToLast {
                        val prefix = if (it.content.isBlank()) "" else "\n\n"
                        it.copy(content = it.content + prefix + "⚠️ " + message)
                    }
                }
            } finally {
                _isStreaming.value = false
                streamJob = null
                if (cancelledByUser) finishEmptyMessage()
            }
        }
    }

    private fun finishEmptyMessage() {
        appendToLast {
            if (it.content.isBlank() && it.reasoning.isBlank()) {
                it.copy(content = "（已停止生成）")
            } else {
                it
            }
        }
    }

    private fun appendToLast(transform: (ChatMessage) -> ChatMessage) {
        _messages.update { list ->
            if (list.isEmpty()) return@update list
            list.dropLast(1) + transform(list.last())
        }
    }

    /** 人格 + 所有启用技能，合并为系统提示词。 */
    private fun buildSystemPrompt(): String = buildString {
        val persona = settingsRepository.currentPersona().trim()
        if (persona.isNotEmpty()) append(persona)
        val skills = skillRepository.enabledSkills()
        if (skills.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append("# 已启用技能\n")
            skills.forEach { skill ->
                append("\n## ").append(skill.name.trim()).append('\n')
                if (skill.description.isNotBlank()) {
                    append(skill.description.trim()).append("\n\n")
                }
                append(skill.body.trim()).append('\n')
            }
        }
    }

    /** 按当前模型的上下文轮数携带历史；0 表示只发送当前消息。思考过程不回传。 */
    private fun buildApiMessages(contextTurns: Int): List<AiMessage> {
        val withoutPlaceholder = _messages.value.dropLast(1)
        val current = withoutPlaceholder.lastOrNull()
        val history = if (contextTurns <= 0) {
            emptyList()
        } else {
            withoutPlaceholder.dropLast(1).takeLast(contextTurns * 2)
        }
        return (history + listOfNotNull(current)).mapNotNull { message ->
            message.content.trim().takeIf { it.isNotEmpty() }?.let {
                AiMessage(role = message.role.apiValue, content = it)
            }
        }
    }

    private fun resolveActive(
        providers: List<ProviderInstance>,
        entries: List<ModelEntry>,
        selectedId: String,
    ): ActiveModel? {
        val selected = entries.firstOrNull { it.id == selectedId }
        if (selected != null) {
            providers.firstOrNull { it.id == selected.providerId && it.isEnabled }?.let {
                return ActiveModel(it, selected)
            }
        }
        providers.filter { it.isEnabled }.forEach { provider ->
            entries.firstOrNull { it.providerId == provider.id }?.let {
                return ActiveModel(provider, it)
            }
        }
        return null
    }
}
