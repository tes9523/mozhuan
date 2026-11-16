package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.data.model.ApiSettings
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.net.AiClient
import com.mozhuan.net.model.AiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 测试连接结果。 */
data class TestResult(
    val success: Boolean,
    val message: String,
)

/** 提供商配置页三阶段：列表 → 基础信息 → 参数配置。 */
enum class ProviderStage { LIST, BASIC, PARAMS }

/** 多服务商管理：两页式流程（基础信息 → 参数），独立参数与连接测试。 */
class ProviderConfigViewModel(
    private val settingsRepository: SettingsRepository,
    private val aiClient: AiClient,
) : ViewModel() {

    val providers: StateFlow<List<ApiSettings>> = settingsRepository.providers
    val defaultProviderId: StateFlow<String> = settingsRepository.defaultProviderId

    private val _stage = MutableStateFlow(ProviderStage.LIST)
    val stage: StateFlow<ProviderStage> = _stage.asStateFlow()

    private val _draft = MutableStateFlow(ApiSettings(name = "新服务商"))
    val draft: StateFlow<ApiSettings> = _draft.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    private val _loadingModels = MutableStateFlow(false)
    val loadingModels: StateFlow<Boolean> = _loadingModels.asStateFlow()

    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    private val _modelsError = MutableStateFlow<String?>(null)
    val modelsError: StateFlow<String?> = _modelsError.asStateFlow()

    fun startNew() {
        _draft.value = ApiSettings(name = "新服务商")
        _testResult.value = null
        _models.value = emptyList()
        _modelsError.value = null
        _stage.value = ProviderStage.BASIC
    }

    fun edit(provider: ApiSettings) {
        _draft.value = provider
        _testResult.value = null
        _stage.value = ProviderStage.BASIC
    }

    fun back() {
        _testResult.value = null
        _stage.value = when (_stage.value) {
            ProviderStage.PARAMS -> ProviderStage.BASIC
            ProviderStage.BASIC -> ProviderStage.LIST
            ProviderStage.LIST -> ProviderStage.LIST
        }
    }

    fun update(transform: (ApiSettings) -> ApiSettings) {
        _draft.value = transform(_draft.value)
        _testResult.value = null
    }

    /** 第一步：保存基础信息（名称 + 端点 + API Key）并跳到参数页。 */
    fun saveBasic(onSaved: () -> Unit = {}) {
        val s = _draft.value
        if (s.name.isBlank()) {
            _testResult.value = TestResult(false, "请填写服务商名称")
            return
        }
        if (s.baseUrl.isBlank()) {
            _testResult.value = TestResult(false, "请填写请求地址")
            return
        }
        if (s.apiKey.isBlank()) {
            _testResult.value = TestResult(false, "请填写 API Key")
            return
        }
        settingsRepository.saveProvider(s)
        _testResult.value = TestResult(true, "基础信息已保存")
        _stage.value = ProviderStage.PARAMS
        onSaved()
        loadModels()
    }

    /** 第二步：保存当前服务商完整配置（参数页）。 */
    fun save() {
        val s = _draft.value
        if (s.model.isBlank()) {
            _testResult.value = TestResult(false, "请填写模型名称")
            return
        }
        settingsRepository.saveProvider(s)
        _testResult.value = TestResult(true, "服务商配置已保存")
    }

    fun delete(providerId: String) {
        settingsRepository.deleteProvider(providerId)
        if (_draft.value.id == providerId) {
            _draft.value = settingsRepository.currentApiSettings()
                .takeIf { settingsRepository.providers.value.isNotEmpty() }
                ?: ApiSettings(name = "默认服务商")
        }
        _testResult.value = null
        _stage.value = ProviderStage.LIST
    }

    fun setDefault(providerId: String) = settingsRepository.setDefaultProvider(providerId)

    /** 仅重置当前编辑中的生成参数，连接信息不变。 */
    fun resetParameters() {
        _draft.value = _draft.value.copy(
            maxTokens = ApiSettings.DEFAULT.maxTokens,
            temperature = ApiSettings.DEFAULT.temperature,
            topP = ApiSettings.DEFAULT.topP,
            contextTurns = ApiSettings.DEFAULT.contextTurns,
        )
        _testResult.value = null
    }

    /** 拉取当前 draft 服务商的模型列表填充下拉。 */
    fun loadModels() {
        if (_draft.value.baseUrl.isBlank() || _draft.value.apiKey.isBlank()) return
        if (_loadingModels.value) return
        viewModelScope.launch {
            _loadingModels.value = true
            _modelsError.value = null
            aiClient.fetchModels(_draft.value.baseUrl, _draft.value.apiKey).fold(
                onSuccess = { names ->
                    _models.value = names
                    if (names.isEmpty()) {
                        _modelsError.value = "未返回可用模型，可手动填写模型名"
                    }
                },
                onFailure = { err ->
                    _models.value = emptyList()
                    _modelsError.value = "获取模型列表失败，可手动填写模型名"
                },
            )
            _loadingModels.value = false
        }
    }

    fun testConnection() {
        if (_testing.value) return
        val settings = _draft.value
        if (!settings.isConfigured) {
            _testResult.value = TestResult(false, "请先填写端点、API Key 与模型名称")
            return
        }

        viewModelScope.launch {
            _testing.value = true
            _testResult.value = null
            aiClient.chat(
                settings = settings,
                messages = listOf(AiMessage(role = "user", content = "你好")),
            ).fold(
                onSuccess = { reply ->
                    val preview = reply.trim().take(40).ifBlank { "（空回复）" }
                    _testResult.value = TestResult(true, "连接成功：$preview")
                },
                onFailure = { error ->
                    _testResult.value = TestResult(false, error.message ?: "连接失败：未知错误")
                },
            )
            _testing.value = false
        }
    }
}
