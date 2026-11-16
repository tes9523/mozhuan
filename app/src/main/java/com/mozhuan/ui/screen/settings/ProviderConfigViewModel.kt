package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.data.model.ModelEntry
import com.mozhuan.data.model.ProviderInstance
import com.mozhuan.data.model.ProviderType
import com.mozhuan.data.repository.ProviderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 测试连接结果。 */
data class TestResult(
    val success: Boolean,
    val message: String,
)

/** 提供商管理阶段：列表 → 选择类型 → 服务商配置 → 模型参数。 */
enum class ProviderStage { LIST, TYPE, FORM, MODEL }

/**
 * 服务商管理，流程对齐 OpenMinis：
 * 先选服务商类型，再填名称 / API Key / 地址与 /v1 后缀；
 * 保存后可拉取模型列表、为每个模型单独配置生成参数并选中当前模型。
 */
class ProviderConfigViewModel(
    private val repository: ProviderRepository,
) : ViewModel() {

    val providers: StateFlow<List<ProviderInstance>> = repository.providers
    val entries: StateFlow<List<ModelEntry>> = repository.entries
    val selectedEntryId: StateFlow<String> = repository.selectedEntryId

    private val _stage = MutableStateFlow(ProviderStage.LIST)
    val stage: StateFlow<ProviderStage> = _stage.asStateFlow()

    private val _draftProvider = MutableStateFlow<ProviderInstance?>(null)
    val draftProvider: StateFlow<ProviderInstance?> = _draftProvider.asStateFlow()

    private val _isNew = MutableStateFlow(true)
    val isNew: StateFlow<Boolean> = _isNew.asStateFlow()

    private val _draftEntry = MutableStateFlow<ModelEntry?>(null)
    val draftEntry: StateFlow<ModelEntry?> = _draftEntry.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    private val _loadingModels = MutableStateFlow(false)
    val loadingModels: StateFlow<Boolean> = _loadingModels.asStateFlow()

    private val _modelsError = MutableStateFlow<String?>(null)
    val modelsError: StateFlow<String?> = _modelsError.asStateFlow()

    private val _newModelInput = MutableStateFlow("")
    val newModelInput: StateFlow<String> = _newModelInput.asStateFlow()

    fun startAdd() {
        _draftProvider.value = null
        _isNew.value = true
        _testResult.value = null
        _modelsError.value = null
        _newModelInput.value = ""
        _stage.value = ProviderStage.TYPE
    }

    fun chooseType(type: ProviderType) {
        _draftProvider.value = ProviderInstance(
            label = type.displayName,
            type = type,
            appendV1Suffix = type.defaultAppendV1,
        )
        _isNew.value = true
        _testResult.value = null
        _stage.value = ProviderStage.FORM
    }

    fun editProvider(provider: ProviderInstance) {
        _draftProvider.value = provider
        _isNew.value = false
        _testResult.value = null
        _modelsError.value = null
        _newModelInput.value = ""
        _stage.value = ProviderStage.FORM
    }

    fun updateDraft(transform: (ProviderInstance) -> ProviderInstance) {
        _draftProvider.value = _draftProvider.value?.let(transform)
        _testResult.value = null
    }

    /** 保存基础配置；新服务商保存后停留在表单页以便继续拉取模型。 */
    fun saveProvider() {
        val draft = _draftProvider.value ?: return
        if (draft.label.isBlank()) {
            _testResult.value = TestResult(false, "请填写服务商名称")
            return
        }
        if (draft.effectiveBaseUrl.isBlank()) {
            _testResult.value = TestResult(false, "请填写请求地址")
            return
        }
        val existing = repository.provider(draft.id)
        if (existing == null) {
            val created = repository.createProvider(
                type = draft.type,
                label = draft.label,
                apiKey = draft.apiKey,
                customBaseUrl = draft.customBaseUrl,
                appendV1Suffix = draft.appendV1Suffix,
            )
            _draftProvider.value = created
            _isNew.value = false
        } else {
            repository.updateProvider(draft)
        }
        _testResult.value = TestResult(true, "服务商配置已保存")
    }

    fun deleteProvider() {
        val draft = _draftProvider.value ?: return
        repository.deleteProvider(draft.id)
        _draftProvider.value = null
        _testResult.value = null
        _stage.value = ProviderStage.LIST
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        repository.setProviderEnabled(providerId, enabled)
    }

    fun selectEntry(entryId: String) {
        repository.selectEntry(entryId)
    }

    fun refreshModels() {
        val draft = _draftProvider.value ?: return
        if (repository.provider(draft.id) == null) {
            _modelsError.value = "请先保存服务商，再拉取模型列表"
            return
        }
        _loadingModels.value = true
        _modelsError.value = null
        viewModelScope.launch {
            repository.refreshModels(draft.id)
                .onSuccess { added ->
                    _testResult.value = TestResult(
                        true,
                        if (added > 0) "已添加 $added 个模型" else "模型列表已是最新",
                    )
                }
                .onFailure { error ->
                    _modelsError.value = error.message
                        ?: "获取模型列表失败，可手动填写模型名"
                }
            _loadingModels.value = false
        }
    }

    fun onNewModelInputChange(text: String) {
        _newModelInput.value = text
    }

    fun addModel(modelId: String = _newModelInput.value) {
        val draft = _draftProvider.value ?: return
        if (repository.provider(draft.id) == null) {
            _modelsError.value = "请先保存服务商，再添加模型"
            return
        }
        val entry = repository.addEntry(draft.id, modelId, isCustom = true)
        if (entry == null) {
            _modelsError.value = "请填写模型名称"
            return
        }
        if (repository.selectedEntryId.value.isBlank()) repository.selectEntry(entry.id)
        _newModelInput.value = ""
        _modelsError.value = null
    }

    fun editEntry(entry: ModelEntry) {
        _draftEntry.value = entry
        _stage.value = ProviderStage.MODEL
    }

    fun updateEntryDraft(transform: (ModelEntry) -> ModelEntry) {
        _draftEntry.value = _draftEntry.value?.let(transform)
    }

    fun saveEntry() {
        val entry = _draftEntry.value ?: return
        if (entry.modelId.isBlank()) {
            _modelsError.value = "模型名称不能为空"
            return
        }
        repository.updateEntry(entry)
        _modelsError.value = null
        _draftEntry.value = null
        _stage.value = ProviderStage.FORM
    }

    fun deleteEntry() {
        val entry = _draftEntry.value ?: return
        repository.deleteEntry(entry.id)
        _draftEntry.value = null
        _stage.value = ProviderStage.FORM
    }

    fun testConnection() {
        val draft = _draftProvider.value ?: return
        if (draft.effectiveBaseUrl.isBlank()) {
            _testResult.value = TestResult(false, "请填写请求地址")
            return
        }
        val entry = repository.entries.value.firstOrNull {
            it.id == selectedEntryId.value && it.providerId == draft.id
        } ?: repository.entriesFor(draft.id).firstOrNull()
        if (entry == null && draft.type.defaultModels.isEmpty()) {
            _testResult.value = TestResult(false, "请先添加一个模型后再测试")
            return
        }
        _testing.value = true
        _testResult.value = null
        viewModelScope.launch {
            repository.testProvider(draft, entry)
                .onSuccess { reply ->
                    val modelName = entry?.title ?: draft.type.defaultModels.first()
                    _testResult.value = TestResult(true, "连接成功（$modelName）：${reply.take(60)}")
                }
                .onFailure { error ->
                    _testResult.value = TestResult(false, error.message ?: "连接失败")
                }
            _testing.value = false
        }
    }

    fun back() {
        _testResult.value = null
        _modelsError.value = null
        _stage.value = when (_stage.value) {
            ProviderStage.MODEL -> ProviderStage.FORM
            ProviderStage.FORM -> ProviderStage.LIST
            ProviderStage.TYPE -> ProviderStage.LIST
            ProviderStage.LIST -> ProviderStage.LIST
        }
    }
}
