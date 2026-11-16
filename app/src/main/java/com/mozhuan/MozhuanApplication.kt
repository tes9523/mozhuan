package com.mozhuan

import android.app.Application
import android.content.Context
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.net.AiClient
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手动依赖容器。
 *
 * 全应用共用同一个 AI 后端：对话、续写、人设渲染都读取同一套 [AppPreferences] 配置，
 * 书稿功能当前仅为占位入口，不在此注册任何业务仓库。
 */
class AppContainer(context: Context) {

    val applicationContext: Context = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val preferences = AppPreferences(applicationContext)

    val settingsRepository = SettingsRepository(preferences)

    val aiClient = AiClient()
}

class MozhuanApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
