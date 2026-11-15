package com.mozhuan.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 全局路由表：所有路由字符串集中在这里维护，页面之间不允许硬编码路由。
 */
object Routes {
    /** 首页：复用阶段0 的书稿列表。 */
    const val HOME = "home"

    /** 演示页：阶段1 的基础交互演示。 */
    const val DEMO = "demo"

    /** 关于页：应用信息与设置入口。 */
    const val ABOUT = "about"

    /** 编辑器：全屏页面，不显示底部导航。 */
    const val EDITOR = "editor/{bookId}"

    /** 全局设置：全屏页面，不显示底部导航。 */
    const val SETTINGS = "settings"

    const val ARG_BOOK_ID = "bookId"

    /** 构造带参数的书稿编辑器路由。 */
    fun editor(bookId: String): String = "editor/$bookId"
}

/**
 * 底部导航栏的顶层目的地（3 个 Tab）。
 * 只有这些路由会显示底部导航栏。
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, "首页", Icons.Filled.Home),
    DEMO(Routes.DEMO, "演示页", Icons.Filled.PlayArrow),
    ABOUT(Routes.ABOUT, "关于页", Icons.Filled.Info),
}
