package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.mozhuan.data.repository.MemoryFileInfo
import com.mozhuan.data.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 记忆管理：全局记忆与按天记录的查看、编辑、删除。 */
class MemoryViewModel(
    private val repository: MemoryRepository,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = repository.enabled

    private val _files = MutableStateFlow(repository.files())
    val files: StateFlow<List<MemoryFileInfo>> = _files.asStateFlow()

    fun setEnabled(value: Boolean) = repository.setEnabled(value)

    fun read(name: String): String = repository.read(name)

    fun save(name: String, content: String) {
        repository.save(name, content)
        refresh()
    }

    fun delete(name: String) {
        repository.delete(name)
        refresh()
    }

    fun ensureTodayFile(): String {
        val name = repository.todayFileName()
        if (_files.value.none { it.name == name }) {
            repository.save(name, "# ${name.removeSuffix(".md")} 记录\n\n")
            refresh()
        }
        return name
    }

    fun refresh() {
        _files.value = repository.files()
    }
}
