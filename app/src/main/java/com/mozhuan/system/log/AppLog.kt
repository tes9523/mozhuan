package com.mozhuan.system.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 一条运行日志。 */
data class LogEntry(
    val time: Long = System.currentTimeMillis(),
    val level: String,
    val message: String,
)

/**
 * 轻量内存日志：只保留最近 [MAX_ENTRIES] 条，用于「运行日志」页面排查请求问题。
 * 不做持久化，应用重启后清空。
 */
object AppLog {

    private const val MAX_ENTRIES = 300

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun info(message: String) = append("INFO", message)

    fun warn(message: String) = append("WARN", message)

    fun error(message: String) = append("ERROR", message)

    fun clear() {
        _entries.value = emptyList()
    }

    private fun append(level: String, message: String) {
        val text = message.trim()
        if (text.isEmpty()) return
        _entries.update { current ->
            (current + LogEntry(level = level, message = text)).takeLast(MAX_ENTRIES)
        }
    }
}
