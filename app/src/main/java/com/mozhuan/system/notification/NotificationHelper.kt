package com.mozhuan.system.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.mozhuan.MainActivity
import com.mozhuan.R
import com.mozhuan.system.service.WritingSessionService

object NotificationHelper {

    const val CHANNEL_ID = "writing_session"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "写作状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "编辑书稿时显示当前书稿与章节"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        bookTitle: String,
        chapterTitle: String,
        status: String,
    ): Notification {
        val text = "《$bookTitle》 · $chapterTitle · $status"
        return base(context)
            .setContentTitle("墨撰 · 写作中")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .addAction(0, "结束写作", stopPendingIntent(context))
            .build()
    }

    /** 后台保活常驻通知。 */
    fun buildKeepAlive(context: Context): Notification =
        base(context)
            .setContentTitle("墨撰 · 后台运行中")
            .setContentText("已开启后台保活，退出应用后仍保持连接")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("已开启后台保活，退出应用后仍保持连接。可在「设置 → 后台与通知」中关闭。"),
            )
            .addAction(0, "停止保活", keepAliveStopPendingIntent(context))
            .build()

    private fun keepAliveStopPendingIntent(context: Context): PendingIntent {
        val stopIntent = Intent(context, WritingSessionService::class.java)
            .setAction(WritingSessionService.ACTION_STOP_KEEP_ALIVE)
        return PendingIntent.getService(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun base(context: Context): NotificationCompat.Builder {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_write)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    private fun stopPendingIntent(context: Context): PendingIntent {
        val stopIntent = Intent(context, WritingSessionService::class.java)
            .setAction(WritingSessionService.ACTION_STOP)
        return PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
