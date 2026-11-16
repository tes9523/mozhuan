package com.mozhuan

import android.app.Application
import android.content.Context
import com.mozhuan.data.repository.ProviderRepository
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.data.repository.SkillRepository
import com.mozhuan.data.repository.TokenUsageRepository
import com.mozhuan.net.AiClient
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 手动依赖容器，全应用共用同一套 API 配置与本地数据仓库。 */
class AppContainer(context: Context) {

    val applicationContext: Context = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val preferences = AppPreferences(applicationContext)

    val settingsRepository = SettingsRepository(preferences)

    val skillRepository = SkillRepository(applicationContext, preferences)

    val tokenUsageRepository = TokenUsageRepository(preferences)

    val aiClient = AiClient()

    val providerRepository = ProviderRepository(preferences, aiClient)
}

class MozhuanApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
