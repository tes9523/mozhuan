package com.mozhuan.system.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
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
        const val EXTRA_BOOK_TITLE = "extra_book_title"
        const val EXTRA_CHAPTER_TITLE = "extra_chapter_title"
        const val EXTRA_STATUS = "extra_status"

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

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WritingSessionService::class.java)) }
        }
    }
}
