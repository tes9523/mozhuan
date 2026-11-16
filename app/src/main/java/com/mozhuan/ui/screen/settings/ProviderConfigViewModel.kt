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

/**
 * LLM 提供商配置 ViewModel：连接信息 + 生成参数 + 测试连接。
 */
class ProviderConfigViewModel(
    private val settingsRepository: SettingsRepository,
    private val aiClient: AiClient,
) : ViewModel() {

    private val _draft = MutableStateFlow(settingsRepository.currentApiSettings())
    val draft: StateFlow<ApiSettings> = _draft.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    fun update(transform: (ApiSettings) -> ApiSettings) {
        _draft.value = transform(_draft.value)
        _testResult.value = null
    }

    /** 保存到本地持久化。 */
    fun save() {
        settingsRepository.saveApiSettings(_draft.value)
    }

    /** 重置生成参数为默认值（保留接口地址 / Key / 模型）。 */
    fun resetParameters() {
        settingsRepository.resetApiParameters()
        _draft.value = settingsRepository.currentApiSettings()
        _testResult.value = null
    }

    /** 使用当前草稿配置发起一次非流式请求，验证连通性。 */
    fun testConnection() {
        if (_testing.value) return
        val settings = _draft.value
        if (!settings.isConfigured) {
            _testResult.value = TestResult(false, "请先填写接口地址、API Key 与模型名称")
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
                    _testResult.value = TestResult(false, "连接失败：${error.message ?: "未知错误"}")
                },
            )
            _testing.value = false
        }
    }
}
