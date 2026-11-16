package com.mozhuan.data.repository

import com.mozhuan.data.model.ActiveModel
import com.mozhuan.data.model.ModelEntry
import com.mozhuan.data.model.ModelGroup
import com.mozhuan.data.model.ProviderInstance
import com.mozhuan.data.model.ProviderType
import com.mozhuan.data.model.ThinkingLevel
import com.mozhuan.net.AiClient
import com.mozhuan.net.StreamChunk
import com.mozhuan.net.model.AiMessage
import kotlinx.coroutines.CancellationException
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * 服务商与模型仓库，行为对齐 OpenMinis：
 * 服务商按类型创建、地址可选自定义并按需追加 /v1、模型列表可从 /models 拉取，
 * 每个模型独立保存生成参数；模型组可把多个模型组合成故障转移 / 负载均衡单元。
 */
class ProviderRepository(
    private val preferences: AppPreferences,
    private val aiClient: AiClient,
) {

    val providers: StateFlow<List<ProviderInstance>> = preferences.providers
    val entries: StateFlow<List<ModelEntry>> = preferences.modelEntries
    val selectedEntryId: StateFlow<String> = preferences.selectedEntryId
    val groups: StateFlow<List<ModelGroup>> = preferences.modelGroups
    val selectedGroupId: StateFlow<String> = preferences.selectedGroupId

    fun entriesFor(providerId: String): List<ModelEntry> =
        entries.value.filter { it.providerId == providerId }

    fun provider(id: String): ProviderInstance? = providers.value.firstOrNull { it.id == id }

    /** 新建服务商，并按其类型写入内置模型。 */
    fun createProvider(
        type: ProviderType,
        label: String,
        apiKey: String,
        customBaseUrl: String,
        appendV1Suffix: Boolean,
    ): ProviderInstance {
        val provider = ProviderInstance(
            label = label.trim().ifBlank { type.displayName },
            type = type,
            apiKey = apiKey.trim(),
            customBaseUrl = customBaseUrl.trim(),
            appendV1Suffix = appendV1Suffix,
        )
        preferences.saveProviders(providers.value + provider)
        type.defaultModels.forEach { modelId ->
            addEntryInternal(provider.id, modelId, isCustom = false)
        }
        return provider
    }

    fun updateProvider(provider: ProviderInstance) {
        preferences.saveProviders(
            providers.value.map { if (it.id == provider.id) provider else it },
        )
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        val current = provider(providerId) ?: return
        updateProvider(current.copy(isEnabled = enabled))
        ensureSelection()
    }

    fun deleteProvider(providerId: String) {
        val removedEntryIds = entries.value
            .filter { it.providerId == providerId }
            .map { it.id }
            .toSet()
        preferences.saveProviders(providers.value.filterNot { it.id == providerId })
        preferences.saveModelEntries(entries.value.filterNot { it.providerId == providerId })
        if (removedEntryIds.isNotEmpty()) {
            preferences.saveModelGroups(
                groups.value.map { group ->
                    group.copy(memberEntryIds = group.memberEntryIds.filterNot { it in removedEntryIds })
                },
            )
        }
        ensureSelection()
    }

    fun addEntry(providerId: String, modelId: String, isCustom: Boolean = true): ModelEntry? {
        val normalized = modelId.trim()
        if (normalized.isEmpty()) return null
        if (entries.value.any { it.providerId == providerId && it.modelId == normalized }) {
            return entries.value.firstOrNull { it.providerId == providerId && it.modelId == normalized }
        }
        return addEntryInternal(providerId, normalized, isCustom)
    }

    private fun addEntryInternal(providerId: String, modelId: String, isCustom: Boolean): ModelEntry {
        val entry = ModelEntry(
            id = UUID.randomUUID().toString(),
            providerId = providerId,
            modelId = modelId,
            isCustom = isCustom,
        )
        preferences.saveModelEntries(entries.value + entry)
        ensureSelection()
        return entry
    }

    fun updateEntry(entry: ModelEntry) {
        preferences.saveModelEntries(
            entries.value.map { if (it.id == entry.id) entry else it },
        )
    }

    fun deleteEntry(entryId: String) {
        preferences.saveModelEntries(entries.value.filterNot { it.id == entryId })
        preferences.saveModelGroups(
            groups.value.map { group ->
                group.copy(memberEntryIds = group.memberEntryIds.filterNot { it == entryId })
            },
        )
        ensureSelection()
    }

    fun selectEntry(entryId: String) {
        preferences.setSelectedEntry(entryId)
    }

    // ── 模型组 ───────────────────────────────────────────────────

    fun createGroup(name: String): ModelGroup {
        val group = ModelGroup(name = name.trim().ifBlank { "模型组" })
        preferences.saveModelGroups(groups.value + group)
        return group
    }

    fun updateGroup(group: ModelGroup) {
        preferences.saveModelGroups(
            groups.value.map { if (it.id == group.id) group else it },
        )
    }

    fun deleteGroup(groupId: String) {
        preferences.saveModelGroups(groups.value.filterNot { it.id == groupId })
        if (preferences.selectedGroupId.value == groupId) preferences.setSelectedGroup("")
        ensureSelection()
    }

    fun addGroupMember(groupId: String, entryId: String) {
        val group = groups.value.firstOrNull { it.id == groupId } ?: return
        if (entryId in group.memberEntryIds) return
        updateGroup(group.copy(memberEntryIds = group.memberEntryIds + entryId))
    }

    fun removeGroupMember(groupId: String, entryId: String) {
        val group = groups.value.firstOrNull { it.id == groupId } ?: return
        updateGroup(group.copy(memberEntryIds = group.memberEntryIds - entryId))
    }

    /** 切换当前使用模型组；空组不生效。 */
    fun selectGroup(groupId: String) {
        val group = groups.value.firstOrNull { it.id == groupId } ?: return
        if (groupMembers(group).isEmpty()) return
        preferences.setSelectedGroup(groupId)
    }

    /** 模型组成员（仅保留启用服务商下的有效模型），按配置顺序。 */
    fun groupMembers(group: ModelGroup): List<ActiveModel> {
        val providersById = providers.value.filter { it.isEnabled }.associateBy { it.id }
        val entriesById = entries.value.associateBy { it.id }
        return group.memberEntryIds.mapNotNull { entryId ->
            val entry = entriesById[entryId] ?: return@mapNotNull null
            providersById[entry.providerId]?.let { ActiveModel(it, entry) }
        }
    }

    fun groupMemberEntries(group: ModelGroup): List<ModelEntry> =
        groupMembers(group).map { it.entry }

    /** 拉取服务商模型列表；已有模型保留，只补充新模型。 */
    suspend fun refreshModels(providerId: String): Result<Int> {
        val provider = provider(providerId) ?: return Result.failure(Exception("服务商不存在"))
        return aiClient.fetchModels(provider).map { models ->
            var added = 0
            models.forEach { modelId ->
                val exists = entries.value.any {
                    it.providerId == providerId && it.modelId == modelId
                }
                if (!exists) {
                    addEntryInternal(providerId, modelId, isCustom = false)
                    added++
                }
            }
            ensureSelection()
            added
        }
    }

    /** 快速测试与正式对话共用流式请求路径，并保留所选模型的完整配置。 */
    suspend fun testProvider(provider: ProviderInstance, entry: ModelEntry? = null): Result<String> {
        val resolved = entry
            ?: entries.value.firstOrNull {
                it.id == preferences.selectedEntryId.value && it.providerId == provider.id
            }
            ?: entriesFor(provider.id).firstOrNull()
            ?: provider.type.defaultModels.firstOrNull()?.let {
                ModelEntry(providerId = provider.id, modelId = it)
            }
            ?: return Result.failure(Exception("请先选择或填写模型名称"))
        val testEntry = resolved.copy(maxTokens = 128, thinkingLevel = ThinkingLevel.OFF)
        return try {
            val reply = StringBuilder()
            aiClient.streamChat(
                provider = provider,
                entry = testEntry,
                messages = listOf(AiMessage(role = "user", content = "请只回复：OK")),
                includeSampling = false,
            ).collect { chunk ->
                if (chunk is StreamChunk.Text) reply.append(chunk.text)
            }
            Result.success(reply.toString().trim().ifBlank { "连接成功" })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun ensureSelection() {
        val allEntries = entries.value
        val allProviders = providers.value
        val allGroups = groups.value

        val group = allGroups.firstOrNull { it.id == preferences.selectedGroupId.value }
        if (group != null && groupMembers(group).isNotEmpty()) return
        if (preferences.selectedGroupId.value.isNotEmpty()) preferences.setSelectedGroup("")

        val current = preferences.selectedEntryId.value
        val valid = allEntries.any { entry ->
            entry.id == current && allProviders.any { it.id == entry.providerId && it.isEnabled }
        }
        if (valid) return
        val fallback = allProviders.firstOrNull { it.isEnabled }
            ?.let { provider -> allEntries.firstOrNull { it.providerId == provider.id } }
            ?: allEntries.firstOrNull()
        preferences.setSelectedEntry(fallback?.id.orEmpty())
    }
}
