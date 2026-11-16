package com.mozhuan.data.repository

import com.mozhuan.data.model.ChatMessage
import com.mozhuan.data.model.ChatSession
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 会话仓库：多个独立会话，消息互不影响。
 * 流式输出期间消息会高频变化，因此内存态立即更新、落盘做 400ms 防抖，
 * 生成结束或切走会话时调用 [flush] 立即持久化。
 */
class SessionRepository(
    private val preferences: AppPreferences,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null

    private val _sessions = MutableStateFlow(preferences.sessions.value)
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow(preferences.activeSessionId.value)
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    val activeSession: StateFlow<ChatSession?> =
        combine(_sessions, _activeSessionId) { sessions, id ->
            sessions.firstOrNull { it.id == id }
        }.stateIn(scope, SharingStarted.Eagerly, _sessions.value.firstOrNull())

    init {
        ensureSession()
    }

    fun createSession(): ChatSession {
        val session = ChatSession()
        _sessions.value = listOf(session) + _sessions.value
        selectSession(session.id)
        persistNow()
        return session
    }

    fun selectSession(sessionId: String) {
        if (_sessions.value.none { it.id == sessionId }) return
        _activeSessionId.value = sessionId
        preferences.setActiveSession(sessionId)
    }

    /** 删除会话；若删的是当前会话，自动切到下一个或新建一个空会话。 */
    fun deleteSession(sessionId: String) {
        val remaining = _sessions.value.filterNot { it.id == sessionId }
        if (remaining.isEmpty()) {
            val fresh = ChatSession()
            _sessions.value = listOf(fresh)
            _activeSessionId.value = fresh.id
            preferences.setActiveSession(fresh.id)
        } else {
            _sessions.value = remaining
            if (_activeSessionId.value == sessionId) {
                val next = remaining.first()
                _activeSessionId.value = next.id
                preferences.setActiveSession(next.id)
            }
        }
        persistNow()
    }

    fun renameSession(sessionId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        _sessions.value = _sessions.value.map {
            if (it.id == sessionId) it.copy(title = trimmed) else it
        }
        schedulePersist()
    }

    /** 更新某个会话的消息（流式期间高频调用，落盘防抖）。 */
    fun updateMessages(sessionId: String, messages: List<ChatMessage>) {
        val now = System.currentTimeMillis()
        _sessions.value = _sessions.value.map {
            if (it.id == sessionId) it.copy(messages = messages, updatedAt = now) else it
        }
        schedulePersist()
    }

    /** 首条用户消息写入后自动生成会话标题。 */
    fun ensureTitle(sessionId: String, text: String) {
        val session = _sessions.value.firstOrNull { it.id == sessionId } ?: return
        if (session.title.isNotBlank() && session.title != "新会话") return
        val title = text.trim().replace("\n", " ").take(20).ifBlank { return }
        renameSession(sessionId, title)
    }

    fun flush() = persistNow()

    private fun ensureSession() {
        if (_sessions.value.isEmpty()) {
            val session = ChatSession()
            _sessions.value = listOf(session)
            _activeSessionId.value = session.id
            preferences.setActiveSession(session.id)
            persistNow()
            return
        }
        if (_sessions.value.none { it.id == _activeSessionId.value }) {
            val first = _sessions.value.first()
            _activeSessionId.value = first.id
            preferences.setActiveSession(first.id)
        }
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(400)
            preferences.saveSessions(_sessions.value)
        }
    }

    private fun persistNow() {
        persistJob?.cancel()
        preferences.saveSessions(_sessions.value)
    }
}
