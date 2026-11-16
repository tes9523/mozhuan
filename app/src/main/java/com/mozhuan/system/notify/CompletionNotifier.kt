package com.mozhuan.system.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** 生成完成 / 失败通知，遵守系统通知权限开关。 */
class CompletionNotifier(private val context: Context) {

    fun notifyComplete(modelTitle: String) {
        notify(
            title = "生成完成",
            text = "「${modelTitle.ifBlank { "当前模型" }}」的回复已生成",
        )
    }

    fun notifyError(modelTitle: String, message: String?) {
        notify(
            title = "生成失败",
            text = message?.takeIf { it.isNotBlank() } ?: "「${modelTitle.ifBlank { "当前模型" }}」请求失败，请打开应用查看",
        )
    }

    fun notifyTest() {
        notify(title = "测试通知", text = "通知已开启，生成完成时会在这里提醒你。")
    }

    private fun notify(title: String, text: String) {
        if (!notificationsAllowed()) return
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NEXT_ID++, notification)
        }
    }

    private fun notificationsAllowed(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "生成通知",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "AI 回复生成完成或失败时提醒"
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "mozhuan_generation"
        var NEXT_ID = 1001
    }
}
