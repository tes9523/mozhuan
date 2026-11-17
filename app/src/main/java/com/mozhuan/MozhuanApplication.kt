package com.mozhuan

import android.app.Application
import android.content.Context
import com.mozhuan.data.repository.BackupRepository
import com.mozhuan.data.repository.BooksRepository
import com.mozhuan.data.repository.MemoryRepository
import com.mozhuan.data.repository.ProviderRepository
import com.mozhuan.data.repository.SessionRepository
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.data.repository.SkillRepository
import com.mozhuan.data.repository.TokenUsageRepository
import com.mozhuan.net.AiClient
import com.mozhuan.net.tool.ToolRegistry
import com.mozhuan.storage.preferences.AppPreferences
import com.mozhuan.system.notify.CompletionNotifier
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

    val booksRepository = BooksRepository(applicationContext)

    val tokenUsageRepository = TokenUsageRepository(preferences)

    val sessionRepository = SessionRepository(preferences)

    val memoryRepository = MemoryRepository(applicationContext, preferences)

    val aiClient = AiClient { preferences.proxySettings.value }

    val toolRegistry = ToolRegistry(
        context = applicationContext,
        memoryRepository = memoryRepository,
        proxyProvider = { preferences.proxySettings.value },
    )

    val providerRepository = ProviderRepository(preferences, aiClient)

    val backupRepository = BackupRepository(preferences, skillRepository)

    val completionNotifier = CompletionNotifier(applicationContext)
}

class MozhuanApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
