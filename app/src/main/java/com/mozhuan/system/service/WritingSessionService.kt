package com.mozhuan.system.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.mozhuan.MozhuanApplication
import com.mozhuan.system.notification.NotificationHelper

/**
 * 编辑期间常驻的前台服务。仅承担保活与状态展示，不参与文件读写。
 */
class WritingSessionService : Service() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP_KEEP_ALIVE) {
            runCatching {
                val container = (application as MozhuanApplication).container
                val current = container.preferences.notificationSettings.value
                container.preferences.setNotificationSettings(current.copy(keepAlive = false))
            }
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.getBooleanExtra(EXTRA_KEEP_ALIVE, false) == true) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                NotificationHelper.buildKeepAlive(this),
            )
            return START_STICKY
        }
        val bookTitle = intent?.getStringExtra(EXTRA_BOOK_TITLE).orEmpty().ifBlank { "墨撰" }
        val chapterTitle = intent?.getStringExtra(EXTRA_CHAPTER_TITLE).orEmpty().ifBlank { "未选择章节" }
        val status = intent?.getStringExtra(EXTRA_STATUS).orEmpty().ifBlank { "自动保存已开启" }
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            NotificationHelper.build(this, bookTitle, chapterTitle, status),
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.mozhuan.action.STOP_WRITING"
        const val ACTION_STOP_KEEP_ALIVE = "com.mozhuan.action.STOP_KEEP_ALIVE"
        const val EXTRA_BOOK_TITLE = "extra_book_title"
        const val EXTRA_CHAPTER_TITLE = "extra_chapter_title"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_KEEP_ALIVE = "extra_keep_alive"

        fun start(
            context: Context,
            bookTitle: String,
            chapterTitle: String,
            status: String,
        ) {
            val intent = Intent(context, WritingSessionService::class.java).apply {
                putExtra(EXTRA_BOOK_TITLE, bookTitle)
                putExtra(EXTRA_CHAPTER_TITLE, chapterTitle)
                putExtra(EXTRA_STATUS, status)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        /** 后台保活：常驻前台服务，降低被系统回收的概率。 */
        fun startKeepAlive(context: Context) {
            val intent = Intent(context, WritingSessionService::class.java).apply {
                putExtra(EXTRA_KEEP_ALIVE, true)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WritingSessionService::class.java)) }
        }
    }
}
