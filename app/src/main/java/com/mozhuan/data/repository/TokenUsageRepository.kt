package com.mozhuan.data.repository

import com.mozhuan.data.model.ApiSettings
import com.mozhuan.data.model.TokenUsage
import com.mozhuan.data.model.UsageStat
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Token 统计：当前会话仅保存在内存，累计用量按服务商 + 模型持久化。 */
class TokenUsageRepository(
    private val preferences: AppPreferences,
) {

    private val _sessionUsage = MutableStateFlow(TokenUsage())
    val sessionUsage: StateFlow<TokenUsage> = _sessionUsage.asStateFlow()

    val cumulativeUsage: StateFlow<List<UsageStat>> = preferences.usageStats

    fun record(settings: ApiSettings, usage: TokenUsage) {
        if (usage.totalTokens <= 0) return
        _sessionUsage.update { it + usage }

        val current = cumulativeUsage.value
        val index = current.indexOfFirst {
            it.providerId == settings.id && it.model == settings.model
        }
        val updated = if (index >= 0) {
            current.mapIndexed { itemIndex, stat ->
                if (itemIndex == index) {
                    stat.copy(
                        providerName = settings.displayName,
                        inputTokens = stat.inputTokens + usage.inputTokens,
                        outputTokens = stat.outputTokens + usage.outputTokens,
                    )
                } else {
                    stat
                }
            }
        } else {
            current + UsageStat(
                providerId = settings.id,
                providerName = settings.displayName,
                model = settings.model,
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
            )
        }
        preferences.saveUsageStats(updated)
    }

    fun clearSession() {
        _sessionUsage.value = TokenUsage()
    }

    fun clearAll() {
        _sessionUsage.value = TokenUsage()
        preferences.saveUsageStats(emptyList())
    }
}
