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

/** 多服务商管理：新增、编辑、删除、设默认与独立生成参数。 */
class ProviderConfigViewModel(
    private val settingsRepository: SettingsRepository,
    private val aiClient: AiClient,
) : ViewModel() {

    val providers: StateFlow<List<ApiSettings>> = settingsRepository.providers
    val defaultProviderId: StateFlow<String> = settingsRepository.defaultProviderId

    private val _draft = MutableStateFlow(
        settingsRepository.currentApiSettings().takeIf { settingsRepository.providers.value.isNotEmpty() }
            ?: ApiSettings(name = "默认服务商"),
    )
    val draft: StateFlow<ApiSettings> = _draft.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    fun startNew() {
        _draft.value = ApiSettings(name = "新服务商")
        _testResult.value = null
    }

    fun edit(provider: ApiSettings) {
        _draft.value = provider
        _testResult.value = null
    }

    fun update(transform: (ApiSettings) -> ApiSettings) {
        _draft.value = transform(_draft.value)
        _testResult.value = null
    }

    fun save() {
        val settings = _draft.value
        if (!settings.isConfigured || settings.name.isBlank()) {
            _testResult.value = TestResult(false, "请完整填写名称、端点、API Key 与模型")
            return
        }
        settingsRepository.saveProvider(settings)
    }

    fun delete(providerId: String) {
        settingsRepository.deleteProvider(providerId)
        if (_draft.value.id == providerId) {
            _draft.value = settingsRepository.currentApiSettings()
                .takeIf { settingsRepository.providers.value.isNotEmpty() }
                ?: ApiSettings(name = "默认服务商")
        }
        _testResult.value = null
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
