package com.mozhuan.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 全局路由表：集中维护，页面之间不硬编码路由。
 */
object Routes {
    /** Tab1：AI 对话。 */
    const val CHAT = "chat"

    /** Tab2：书稿（纯占位）。 */
    const val BOOKS = "books"

    /** Tab3：设置。 */
    const val SETTINGS = "settings"

    /** LLM 提供商管理（全屏子页）。 */
    const val PROVIDER = "settings/provider"

    /** Token 用量统计。 */
    const val TOKEN_USAGE = "settings/token_usage"

    /** 对话显示设置。 */
    const val DISPLAY_SETTINGS = "settings/display"

    /** 静态提示词技能管理。 */
    const val SKILLS = "settings/skills"

    /** 技能与人格文本共享文件夹。 */
    const val SHARED_FOLDER = "settings/shared_folder"

    /** AGENT 人格系统（全屏子页）。 */
    const val PERSONA = "settings/persona"

    /** 模型组管理。 */
    const val MODEL_GROUPS = "settings/model_groups"

    /** 挂载外部文件夹。 */
    const val MOUNTED_FOLDERS = "settings/mounted_folders"

    /** 备份与恢复。 */
    const val BACKUP_RESTORE = "settings/backup_restore"

    /** 代理设置。 */
    const val PROXY = "settings/proxy"

    /** 后台与通知。 */
    const val BACKGROUND = "settings/background"

    /** 运行日志。 */
    const val LOGS = "settings/logs"

    /** 通用占位子页。 */
    const val PLACEHOLDER = "settings/placeholder/{title}"

    const val ARG_TITLE = "title"

    fun placeholder(title: String): String = "settings/placeholder/${Uri.encode(title)}"
}

/**
 * 底部导航的三个顶层目的地（永久固定）。
 *
 * @property route 路由表中的模式（用于判断当前选中项）
 * @property navRoute 实际跳转用的具体路由
 */
enum class TopLevelDestination(
    val route: String,
    val navRoute: String,
    val label: String,
    val icon: ImageVector,
) {
    CHAT(Routes.CHAT, Routes.CHAT, "AI对话", Icons.Filled.Email),
    BOOKS(Routes.BOOKS, Routes.BOOKS, "书稿", Icons.AutoMirrored.Filled.List),
    SETTINGS(Routes.SETTINGS, Routes.SETTINGS, "设置", Icons.Filled.Settings),
}
