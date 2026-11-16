package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.TokenUsage
import com.mozhuan.data.model.UsageStat
import com.mozhuan.data.repository.TokenUsageRepository
import kotlinx.coroutines.flow.StateFlow

class TokenUsageViewModel(
    private val tokenUsageRepository: TokenUsageRepository,
) : ViewModel() {

    val sessionUsage: StateFlow<TokenUsage> = tokenUsageRepository.sessionUsage
    val cumulativeUsage: StateFlow<List<UsageStat>> = tokenUsageRepository.cumulativeUsage

    fun clearSession() = tokenUsageRepository.clearSession()

    fun clearProvider(providerId: String) = tokenUsageRepository.clearProvider(providerId)

    fun clearAll() = tokenUsageRepository.clearAll()
}
