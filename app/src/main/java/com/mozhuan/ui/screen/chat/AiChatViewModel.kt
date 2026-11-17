package com.mozhuan.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.data.model.ActiveModel
import com.mozhuan.data.model.ApiStyle
import com.mozhuan.data.model.ChatMessage
import com.mozhuan.data.model.ChatRole
import com.mozhuan.data.model.ChatSession
import com.mozhuan.data.model.DisplaySettings
import com.mozhuan.data.model.GroupRoutingStrategy
import com.mozhuan.data.model.ModelEntry
import com.mozhuan.data.model.ModelGroup
import com.mozhuan.data.model.ProviderInstance
import com.mozhuan.data.model.Skill
import com.mozhuan.data.model.ThinkingLevel
import com.mozhuan.data.model.TokenUsage
import com.mozhuan.data.repository.MemoryRepository
import com.mozhuan.data.repository.ProviderRepository
import com.mozhuan.data.repository.SessionRepository
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.data.repository.SkillRepository
import com.mozhuan.data.repository.TokenUsageRepository
import com.mozhuan.net.AiClient
import com.mozhuan.net.StreamChunk
import com.mozhuan.net.ToolCall
import com.mozhuan.net.model.AiMessage
import com.mozhuan.net.model.AiToolCall
import com.mozhuan.net.tool.ToolRegistry
import com.mozhuan.system.log.AppLog
import com.mozhuan.system.notify.AppForeground
import com.mozhuan.system.notify.CompletionNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 对话页可选的模型（服务商 + 模型条目）。 */
data class ModelOption(
    val provider: ProviderInstance,
    val entry: ModelEntry,
)

/** 对话页可选的模型组及其有效成员。 */
data class GroupOption(
    val group: ModelGroup,
    val members: List<ModelOption>,
)

/** 全局 AI 对话：人格 + 已启用技能 + 当前选中模型/模型组的上下文与参数。 */
class AiChatViewModel(
    private val providerRepository: ProviderRepository,
    private val settingsRepository: SettingsRepository,
    private val skillRepository: SkillRepository,
    private val tokenUsageRepository: TokenUsageRepository,
    private val sessionRepository: SessionRepository,
    private val memoryRepository: MemoryRepository,
    private val toolRegistry: ToolRegistry,
    private val aiClient: AiClient,
    private val completionNotifier: CompletionNotifier,
) : ViewModel() {

    val sessions: StateFlow<List<ChatSession>> = sessionRepository.sessions
    val activeSessionId: StateFlow<String> = sessionRepository.activeSessionId

    private val _messages = MutableStateFlow(
        sessionRepository.sessions.value
            .firstOrNull { it.id == sessionRepository.activeSessionId.value }
            ?.messages
            .orEmpty(),
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 工具调用状态提示，例如「正在调用工具：读取文件」。 */
    private val _toolStatus = MutableStateFlow<String?>(null)
    val toolStatus: StateFlow<String?> = _toolStatus.asStateFlow()

    val displaySettings: StateFlow<DisplaySettings> = settingsRepository.displaySettings
    val selectedEntryId: StateFlow<String> = providerRepository.selectedEntryId
    val selectedGroupId: StateFlow<String> = providerRepository.selectedGroupId

    /** 已安装技能，供输入框的 @ 选择器使用。 */
    val skills: StateFlow<List<Skill>> = skillRepository.skills

    val modelOptions: StateFlow<List<ModelOption>> =
        combine(providerRepository.providers, providerRepository.entries) { providers, entries ->
            val byId = providers.associateBy { it.id }
            entries.mapNotNull { entry ->
                byId[entry.providerId]?.takeIf { it.isEnabled }?.let { ModelOption(it, entry) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 仅包含至少一个可用成员的模型组，供对话页选择。 */
    val groupOptions: StateFlow<List<GroupOption>> =
        combine(
            providerRepository.groups,
            providerRepository.providers,
            providerRepository.entries,
        ) { groups, providers, entries ->
            val byProvider = providers.filter { it.isEnabled }.associateBy { it.id }
            val byEntry = entries.associateBy { it.id }
            groups.mapNotNull { group ->
                val members = group.memberEntryIds.mapNotNull { id ->
                    val entry = byEntry[id] ?: return@mapNotNull null
                    byProvider[entry.providerId]?.let { ModelOption(it, entry) }
                }
                if (members.isEmpty()) null else GroupOption(group, members)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeGroup: StateFlow<ModelGroup?> =
        combine(providerRepository.groups, providerRepository.selectedGroupId) { groups, id ->
            groups.firstOrNull { it.id == id }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeModel: StateFlow<ActiveModel?> =
        combine(
            providerRepository.providers,
            providerRepository.entries,
            providerRepository.selectedEntryId,
            providerRepository.groups,
            providerRepository.selectedGroupId,
        ) { providers, entries, selectedId, groups, groupId ->
            val group = groups.firstOrNull { it.id == groupId }
            if (group != null) {
                val member = resolveGroupMembers(group, providers, entries).firstOrNull()
                if (member != null) return@combine member
            }
            resolveActive(providers, entries, selectedId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 当前会话的思考强度：会话显式设置优先，否则跟随模型默认值（缺省中度）。 */
    val thinkingLevel: StateFlow<ThinkingLevel> =
        combine(
            sessionRepository.sessions,
            sessionRepository.activeSessionId,
            activeModel,
        ) { sessions, sessionId, model ->
            sessions.firstOrNull { it.id == sessionId }?.thinkingLevel
                ?: model?.entry?.thinkingLevel?.takeIf { it != ThinkingLevel.OFF }
                ?: ThinkingLevel.MEDIUM
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ThinkingLevel.MEDIUM)

    private var streamJob: Job? = null

    /** 当前生成所属会话，用于丢弃切换会话后迟到的流式增量。 */
    private var generationSessionId: String? = null
    private var cancelledByUser = false

    fun onInputChange(text: String) {
        _input.value = text
    }

    /** 修改当前会话的思考强度并落盘。 */
    fun setThinkingLevel(level: ThinkingLevel) {
        val sessionId = sessionRepository.activeSessionId.value
        if (sessionId.isNotBlank()) sessionRepository.setThinkingLevel(sessionId, level)
    }

    fun selectModel(entryId: String) {
        providerRepository.selectEntry(entryId)
        _error.value = null
    }

    fun selectGroup(groupId: String) {
        providerRepository.selectGroup(groupId)
        _error.value = null
    }

    fun clearMessages() {
        if (_isStreaming.value) return
        setMessages(emptyList())
        _error.value = null
        tokenUsageRepository.clearSession()
    }

    // ── 会话 ─────────────────────────────────────────────────────

    fun newSession() {
        if (_isStreaming.value) stop()
        sessionRepository.createSession()
        _messages.value = emptyList()
        _error.value = null
        tokenUsageRepository.clearSession()
    }

    fun openSession(sessionId: String) {
        if (sessionId == sessionRepository.activeSessionId.value) return
        if (_isStreaming.value) stop()
        sessionRepository.selectSession(sessionId)
        _messages.value = sessionRepository.sessions.value
            .firstOrNull { it.id == sessionId }
            ?.messages
            .orEmpty()
        _error.value = null
        tokenUsageRepository.clearSession()
    }

    fun deleteSession(sessionId: String) {
        if (_isStreaming.value) stop()
        sessionRepository.deleteSession(sessionId)
        _messages.value = sessionRepository.sessions.value
            .firstOrNull { it.id == sessionRepository.activeSessionId.value }
            ?.messages
            .orEmpty()
        _error.value = null
        tokenUsageRepository.clearSession()
    }

    /** 更新当前会话消息并同步落盘（流式期间由仓库做防抖）。 */
    private fun setMessages(messages: List<ChatMessage>) {
        _messages.value = messages
        val sessionId = sessionRepository.activeSessionId.value
        if (sessionId.isNotBlank()) sessionRepository.updateMessages(sessionId, messages)
    }

    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty() || _isStreaming.value) return
        val candidates = currentCandidates()
        if (candidates.isEmpty()) {
            _error.value = "请先在「设置 → 管理提供商」添加服务商并选择模型，或为模型组添加成员"
            return
        }
        if (candidates.first().provider.effectiveBaseUrl.isBlank()) {
            _error.value = "当前服务商缺少请求地址，请到提供商设置中补全"
            return
        }

        _input.value = ""
        setMessages(
            _messages.value +
                ChatMessage(role = ChatRole.USER, content = text) +
                ChatMessage(role = ChatRole.ASSISTANT, content = ""),
        )
        sessionRepository.ensureTitle(sessionRepository.activeSessionId.value, text)
        startGeneration(candidates)
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
        val candidates = currentCandidates()
        if (candidates.isEmpty()) {
            _error.value = "请先在「设置 → 管理提供商」添加服务商并选择模型"
            return
        }

        setMessages(current.take(index) + ChatMessage(role = ChatRole.ASSISTANT, content = ""))
        startGeneration(candidates)
    }

    /** 重发指定用户消息，保留它之前的上下文，丢弃原回复及后续分支。 */
    fun retryUserMessage(messageId: String) {
        restartFromUser(messageId, editedContent = null)
    }

    /** 修改指定用户消息后重新生成；仅允许编辑最新一条用户消息。 */
    fun editUserMessage(messageId: String, content: String) {
        val latestUser = _messages.value.lastOrNull { it.role == ChatRole.USER }
        if (latestUser?.id != messageId) return
        restartFromUser(messageId, editedContent = content.trim())
    }

    /** 删除指定消息及其后的所有消息。 */
    fun deleteMessagesFrom(messageId: String) {
        if (_isStreaming.value) return
        val index = _messages.value.indexOfFirst { it.id == messageId }
        if (index < 0) return
        setMessages(_messages.value.take(index))
        _error.value = null
    }

    private fun restartFromUser(messageId: String, editedContent: String?) {
        if (_isStreaming.value || editedContent?.isEmpty() == true) return
        val current = _messages.value
        val index = current.indexOfFirst { it.id == messageId && it.role == ChatRole.USER }
        if (index < 0) return
        val candidates = currentCandidates()
        if (candidates.isEmpty()) {
            _error.value = "请先在「设置 → 管理提供商」添加服务商并选择模型"
            return
        }

        val userMessage = if (editedContent == null) {
            current[index]
        } else {
            current[index].copy(content = editedContent)
        }
        setMessages(
            current.take(index) + userMessage +
                ChatMessage(role = ChatRole.ASSISTANT, content = ""),
        )
        sessionRepository.ensureTitle(sessionRepository.activeSessionId.value, userMessage.content)
        startGeneration(candidates)
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
        sessionRepository.flush()
        super.onCleared()
    }

    /** 模型组按策略给出候选顺序：故障转移保持顺序，负载均衡随机。 */
    private fun currentCandidates(): List<ActiveModel> {
        val group = activeGroup.value
        if (group != null) {
            val members = providerRepository.groupMembers(group)
            if (members.isNotEmpty()) {
                return if (group.strategy == GroupRoutingStrategy.LOAD_BALANCE) {
                    members.shuffled()
                } else {
                    members
                }
            }
        }
        return listOfNotNull(activeModel.value)
    }

    private fun startGeneration(candidates: List<ActiveModel>) {
        cancelledByUser = false
        _error.value = null
        _isStreaming.value = true
        generationSessionId = sessionRepository.activeSessionId.value
        val group = activeGroup.value

        streamJob = viewModelScope.launch {
            var lastError: String? = null
            var usedModel: ActiveModel? = null
            try {
                for ((index, model) in candidates.withIndex()) {
                    if (index > 0) resetLastAssistant()
                    try {
                        streamFrom(model)
                        usedModel = model
                        break
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        lastError = error.message?.takeIf { it.isNotBlank() } ?: "生成失败，请稍后重试"
                        if (index == candidates.lastIndex) throw error
                        AppLog.warn(
                            "模型组「${group?.name.orEmpty()}」切换：${model.entry.modelId} → " +
                                candidates[index + 1].entry.modelId,
                        )
                    }
                }

                val model = usedModel
                if (model != null &&
                    !AppForeground.isForeground &&
                    settingsRepository.notificationSettings.value.notifyOnComplete
                ) {
                    completionNotifier.notifyComplete(model.entry.title)
                }
                AppLog.info("生成完成 · ${model?.entry?.title.orEmpty()}")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!cancelledByUser) {
                    val message = lastError
                        ?: error.message?.takeIf { it.isNotBlank() }
                        ?: "生成失败，请稍后重试"
                    _error.value = message
                    appendToLast {
                        val prefix = if (it.content.isBlank()) "" else "\n\n"
                        it.copy(content = it.content + prefix + "⚠️ " + message)
                    }
                    if (!AppForeground.isForeground &&
                        settingsRepository.notificationSettings.value.notifyOnError
                    ) {
                        completionNotifier.notifyError(
                            candidates.lastOrNull()?.entry?.title.orEmpty(),
                            message,
                        )
                    }
                }
            } finally {
                _isStreaming.value = false
                streamJob = null
                _toolStatus.value = null
                if (cancelledByUser) finishEmptyMessage()
                sessionRepository.flush()
                generationSessionId = null
            }
        }
    }

    private suspend fun streamFrom(model: ActiveModel) {
        var reportedUsage = TokenUsage()
        // 会话级思考强度覆盖模型配置，其余参数保持模型设置。
        val entry = model.entry.copy(thinkingLevel = thinkingLevel.value)
        val history = buildApiMessages(entry.contextTurns)
        val systemPrompt = buildSystemPrompt()
        val tools = if (model.provider.type.apiStyle == ApiStyle.OPENAI_CHAT) {
            toolRegistry.specs(settingsRepository.toolSettings.value)
        } else {
            emptyList()
        }
        val transcript = mutableListOf<AiMessage>()
        var round = 0
        while (true) {
            val calls = mutableListOf<ToolCall>()
            val roundText = StringBuilder()
            aiClient.streamChat(
                provider = model.provider,
                entry = entry,
                messages = history + transcript,
                systemPrompt = systemPrompt,
                tools = tools,
            ).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Text -> {
                        roundText.append(chunk.text)
                        appendToLast { it.copy(content = it.content + chunk.text) }
                    }
                    is StreamChunk.Reasoning -> appendToLast {
                        it.copy(reasoning = it.reasoning + chunk.text)
                    }
                    is StreamChunk.ToolCalls -> calls += chunk.calls
                    is StreamChunk.Usage -> {
                        val delta = TokenUsage(
                            inputTokens = (chunk.inputTokens - reportedUsage.inputTokens)
                                .coerceAtLeast(0),
                            outputTokens = (chunk.outputTokens - reportedUsage.outputTokens)
                                .coerceAtLeast(0),
                        )
                        tokenUsageRepository.record(model.provider, entry, delta)
                        reportedUsage = TokenUsage(
                            inputTokens = maxOf(reportedUsage.inputTokens, chunk.inputTokens),
                            outputTokens = maxOf(reportedUsage.outputTokens, chunk.outputTokens),
                        )
                    }
                    StreamChunk.Completed -> Unit
                }
            }
            if (calls.isEmpty()) break
            if (round >= MAX_TOOL_ROUNDS) {
                appendToLast {
                    val prefix = if (it.content.isBlank()) "" else "\n\n"
                    it.copy(content = it.content + prefix + "（已达到工具调用轮数上限）")
                }
                break
            }
            transcript += AiMessage(
                role = "assistant",
                content = roundText.toString(),
                toolCalls = calls.map { AiToolCall(it.id, it.name, it.arguments) },
            )
            calls.forEach { call ->
                _toolStatus.value = "正在调用工具：${toolLabel(call.name)}"
                val result = toolRegistry.execute(call.name, call.arguments)
                AppLog.info("工具 ${call.name} 执行完成")
                transcript += AiMessage(role = "tool", content = result, toolCallId = call.id)
            }
            _toolStatus.value = null
            round++
        }
        _toolStatus.value = null
    }

    private fun toolLabel(name: String): String = when (name) {
        "list_files" -> "查看目录"
        "read_file" -> "读取文件"
        "write_file" -> "写入书稿"
        "fetch_url" -> "读取网页"
        "save_memory" -> "写入记忆"
        else -> name
    }

    /** 故障转移重试前清空上一条失败尝试产生的半截内容。 */
    private fun resetLastAssistant() {
        val list = _messages.value
        if (list.isEmpty()) return
        setMessages(list.dropLast(1) + ChatMessage(role = ChatRole.ASSISTANT, content = ""))
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
        // 生成期间切换 / 删除会话后，丢弃旧会话迟到的增量，避免串到新会话。
        val generatingFor = generationSessionId
        if (generatingFor != null && generatingFor != sessionRepository.activeSessionId.value) {
            return
        }
        val list = _messages.value
        if (list.isEmpty()) return
        setMessages(list.dropLast(1) + transform(list.last()))
    }

    /**
     * 人格 + 技能 + 记忆。
     * 普通技能只注入元数据，模型按需 read_file；
     * 用户在输入框用 @ 明确指定的技能则直接注入正文，立即生效。
     */
    private fun buildSystemPrompt(): String = buildString {
        val persona = settingsRepository.currentPersona().trim()
        if (persona.isNotEmpty()) append(persona)

        val allSkills = skillRepository.enabledSkills().take(MAX_DISCOVERABLE_SKILLS)
        val mentioned = mentionedSkills(allSkills)
        val mentionedIds = mentioned.map { it.id }.toSet()

        if (mentioned.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append("# 用户已通过 @ 指定的技能\n")
            append("用户已明确调用以下技能。严格按其中的流程执行；需要附件时用 read_file 按路径读取，不要预先加载全部附件。\n")
            mentioned.forEach { skill ->
                append("\n## ").append(skill.name.trim()).append('\n')
                append(skill.body.trim()).append('\n')
                val files = skillRepository.listAttachments(skill.id)
                if (files.isNotEmpty()) {
                    append("\n附件：\n")
                    files.forEach { file ->
                        append("- skills/").append(skill.id).append('/').append(file.path).append('\n')
                    }
                }
            }
        }

        val discoverable = allSkills.filterNot { it.id in mentionedIds }
        if (discoverable.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append("# 可用技能\n")
            append("以下仅包含技能元数据。当用户任务与某个技能的描述匹配时，先调用 read_file 读取对应 path 的 SKILL.md，再遵循其中的指令；不要凭空假设技能正文。附件也按需读取，不要预先加载。\n")
            discoverable.forEach { skill ->
                append("\n- name: ").append(skill.name.trim().replace('\n', ' '))
                append("\n  description: ")
                    .append(skill.displayDescription.trim().replace(Regex("\\s+"), " ").take(MAX_SKILL_DESCRIPTION_CHARS))
                append("\n  path: skills/").append(skill.id).append("/SKILL.md\n")
            }
        }
        val memory = memoryRepository.promptFragment()
        if (memory.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append(memory)
        }
        if (settingsRepository.toolSettings.value.writeFiles) {
            if (isNotEmpty()) append("\n\n")
            append("# 书稿目录约定\n")
            append("每本小说位于 book/<书名>/ 下：正文写入「章节内容/」，")
            append("例如 book/书名/章节内容/第01章 标题.md；")
            append("世界观写入 book/书名/小说资料/设定/世界观/，人物写入 book/书名/小说资料/设定/人物库/，")
            append("大纲写入 book/书名/小说资料/大纲/，章节摘要写入 book/书名/小说资料/章节摘要/。")
            append("写入任一文件即可在书稿页看到整本小说，缺失的目录会自动补齐。")
        }
    }

    /** 从当前待发送的用户消息中解析 @技能名 / @技能 id。 */
    private fun mentionedSkills(enabled: List<Skill>): List<Skill> {
        val pending = _messages.value.dropLast(1)
            .lastOrNull()
            ?.takeIf { it.role == ChatRole.USER }
            ?.content
            ?: return emptyList()
        if (!pending.contains('@')) return emptyList()
        return enabled.filter { skill ->
            val name = skill.name.trim()
            name.isNotEmpty() && (pending.contains("@$name") || pending.contains("@${skill.id}"))
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

    private fun resolveGroupMembers(
        group: ModelGroup,
        providers: List<ProviderInstance>,
        entries: List<ModelEntry>,
    ): List<ActiveModel> {
        val byProvider = providers.filter { it.isEnabled }.associateBy { it.id }
        val byEntry = entries.associateBy { it.id }
        return group.memberEntryIds.mapNotNull { id ->
            val entry = byEntry[id] ?: return@mapNotNull null
            byProvider[entry.providerId]?.let { ActiveModel(it, entry) }
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

    private companion object {
        /** 单次回复内最多执行几轮工具调用，避免死循环。 */
        const val MAX_TOOL_ROUNDS = 4
        const val MAX_DISCOVERABLE_SKILLS = 20
        const val MAX_SKILL_DESCRIPTION_CHARS = 200
    }
}
