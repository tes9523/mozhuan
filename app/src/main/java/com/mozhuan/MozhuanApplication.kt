package com.mozhuan

import android.app.Application
import com.mozhuan.business.repository.BookRepository
import com.mozhuan.business.repository.SettingsRepository
import com.mozhuan.storage.db.AppDatabase
import com.mozhuan.storage.preferences.AppPreferences
import com.mozhuan.storage.saf.SafDocumentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import android.content.Context

/**
 * 手动依赖容器。后续 llm 模块接入时在此注册独立 provider，不污染本地书稿模块。
 */
class AppContainer(context: Context) {

    val applicationContext: Context = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database = AppDatabase.build(applicationContext)

    val preferences = AppPreferences(applicationContext)

    val safDocumentStore = SafDocumentStore(applicationContext, preferences)

    val bookRepository = BookRepository(
        database = database,
        bookDao = database.bookDao(),
        chapterDao = database.chapterDao(),
        safStore = safDocumentStore,
    )

    val settingsRepository = SettingsRepository(preferences, safDocumentStore)
}

class MozhuanApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
