package com.mozhuan

import android.app.Application
import android.content.Context
import com.mozhuan.data.repository.BookRepository
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.data.repository.SkillRepository
import com.mozhuan.net.AiClient
import com.mozhuan.storage.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手动依赖容器。
 *
 * 本阶段书稿 / 章节 / 技能数据均为内存仓库；
 * 后续接入持久化或 LLM 底座时在此扩展，不污染 UI 层。
 */
class AppContainer(context: Context) {

    val applicationContext: Context = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val preferences = AppPreferences(applicationContext)

    val settingsRepository = SettingsRepository(preferences)

    val bookRepository = BookRepository()

    val skillRepository = SkillRepository()

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
