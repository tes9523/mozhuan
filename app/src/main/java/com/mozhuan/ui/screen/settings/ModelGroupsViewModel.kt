package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.data.model.GroupRoutingStrategy
import com.mozhuan.data.model.ModelEntry
import com.mozhuan.data.model.ModelGroup
import com.mozhuan.data.model.ProviderInstance
import com.mozhuan.data.repository.ProviderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 模型组管理：列表、创建、成员、路由策略与当前选中组。 */
class ModelGroupsViewModel(
    private val repository: ProviderRepository,
) : ViewModel() {

    val groups: StateFlow<List<ModelGroup>> = repository.groups
    val entries: StateFlow<List<ModelEntry>> = repository.entries
    val providers: StateFlow<List<ProviderInstance>> = repository.providers
    val selectedGroupId: StateFlow<String> = repository.selectedGroupId

    private val detailId = MutableStateFlow<String?>(null)

    val detail: StateFlow<ModelGroup?> =
        combine(repository.groups, detailId) { groups, id ->
            groups.firstOrNull { it.id == id }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun open(groupId: String) {
        detailId.value = groupId
    }

    fun back() {
        detailId.value = null
    }

    fun create(name: String) {
        val group = repository.createGroup(name)
        detailId.value = group.id
    }

    fun rename(group: ModelGroup, name: String) {
        repository.updateGroup(group.copy(name = name))
    }

    fun setStrategy(group: ModelGroup, strategy: GroupRoutingStrategy) {
        repository.updateGroup(group.copy(strategy = strategy))
    }

    fun toggleMember(group: ModelGroup, entryId: String) {
        if (entryId in group.memberEntryIds) {
            repository.removeGroupMember(group.id, entryId)
        } else {
            repository.addGroupMember(group.id, entryId)
        }
    }

    fun delete(group: ModelGroup) {
        if (detailId.value == group.id) detailId.value = null
        repository.deleteGroup(group.id)
    }

    fun select(group: ModelGroup) {
        repository.selectGroup(group.id)
    }
}
