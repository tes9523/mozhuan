package com.mozhuan.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 全局路由表：集中维护，页面之间不硬编码路由。
 */
object Routes {
    /** Tab1：AI 对话。bookId 为空表示全局对话，非空表示绑定书稿的专属对话。 */
    const val CHAT = "chat?bookId={bookId}"

    /** Tab2：书稿列表。 */
    const val BOOKS = "books"

    /** Tab3：设置。 */
    const val SETTINGS = "settings"

    /** 技能管理（全屏）。 */
    const val SKILLS = "skills"

    /** 书稿详情（全屏）。 */
    const val BOOK_DETAIL = "book/{bookId}"

    /** 章节编辑（全屏）。 */
    const val CHAPTER = "chapter/{bookId}/{chapterId}"

    const val ARG_BOOK_ID = "bookId"
    const val ARG_CHAPTER_ID = "chapterId"

    fun chat(bookId: String? = null): String =
        if (bookId.isNullOrBlank()) "chat" else "chat?bookId=$bookId"

    fun bookDetail(bookId: String): String = "book/$bookId"

    fun chapter(bookId: String, chapterId: String): String = "chapter/$bookId/$chapterId"
}

/**
 * 底部导航的三个顶层目的地。
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
    CHAT(Routes.CHAT, "chat", "AI对话", Icons.Filled.Email),
    BOOKS(Routes.BOOKS, Routes.BOOKS, "书稿", Icons.AutoMirrored.Filled.List),
    SETTINGS(Routes.SETTINGS, Routes.SETTINGS, "设置", Icons.Filled.Settings),
}
