package com.mozhuan.data.repository

import com.mozhuan.data.model.ModelEntry
import com.mozhuan.data.model.ProviderInstance
import com.mozhuan.data.model.ProviderType
import com.mozhuan.net.AiClient
import com.mozhuan.net.model.AiMessage
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * 服务商与模型仓库，行为对齐 OpenMinis：
 * 服务商按类型创建、地址可选自定义并按需追加 /v1、模型列表可从 /models 拉取，
 * 每个模型独立保存生成参数，对话使用当前选中的模型。
 */
class ProviderRepository(
    private val preferences: AppPreferences,
    private val aiClient: AiClient,
) {

    val providers: StateFlow<List<ProviderInstance>> = preferences.providers
    val entries: StateFlow<List<ModelEntry>> = preferences.modelEntries
    val selectedEntryId: StateFlow<String> = preferences.selectedEntryId

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
        preferences.saveProviders(providers.value.filterNot { it.id == providerId })
        preferences.saveModelEntries(entries.value.filterNot { it.providerId == providerId })
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
        ensureSelection()
    }

    fun selectEntry(entryId: String) {
        preferences.setSelectedEntry(entryId)
    }

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

    /** 测试服务商连接，使用给定模型或该服务商第一个模型。 */
    suspend fun testProvider(provider: ProviderInstance, modelId: String? = null): Result<String> {
        val resolvedModel = modelId?.trim()?.takeIf { it.isNotEmpty() }
            ?: entriesFor(provider.id).firstOrNull()?.modelId
            ?: provider.type.defaultModels.firstOrNull()
            ?: ""
        if (resolvedModel.isEmpty()) {
            return Result.failure(Exception("请先选择或填写模型名称"))
        }
        val entry = ModelEntry(providerId = provider.id, modelId = resolvedModel)
        return aiClient.complete(
            provider = provider,
            entry = entry,
            messages = listOf(AiMessage(role = "user", content = "请只回复：OK")),
        ).map { it.ifBlank { "连接成功" } }
    }

    private fun ensureSelection() {
        val current = preferences.selectedEntryId.value
        val allEntries = entries.value
        val allProviders = providers.value
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
