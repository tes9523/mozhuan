package com.mozhuan.ui.component

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mozhuan.ui.navigation.TopLevelDestination

/**
 * 全局底部导航栏：首页 / 演示页 / 关于页。
 *
 * 切换 Tab 时使用 saveState + restoreState 保存并恢复各页面状态，
 * 避免重复创建页面或丢失书稿列表滚动位置。
 */
@Composable
fun MozhuanBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    navController.navigate(destination.route) {
                        // 回到导航图起点，避免在返回栈中堆积多个顶层页面。
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // 重复点击同一个 Tab 时不重新创建页面。
                        launchSingleTop = true
                        // 切回之前访问过的 Tab 时恢复其状态。
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
            )
        }
    }
}
