package com.mozhuan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mozhuan.AppContainer
import com.mozhuan.business.model.ThemeMode
import com.mozhuan.ui.component.MozhuanBottomBar
import com.mozhuan.ui.navigation.MozhuanNavHost
import com.mozhuan.ui.navigation.TopLevelDestination
import com.mozhuan.ui.theme.MozhuanTheme

/**
 * 应用根节点：主题 + 全局 Scaffold + 导航图。
 *
 * 底部导航栏只在顶层 Tab（首页 / 演示页 / 关于页）显示，
 * 编辑器与全局设置为全屏页面，不显示底部导航。
 */
@Composable
fun MozhuanRoot(container: AppContainer) {
    val themeMode by container.settingsRepository.themeMode.collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MozhuanTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        // 首帧路由尚未解析时按首页处理，避免底部导航闪烁。
        val showBottomBar = currentRoute == null ||
            TopLevelDestination.entries.any { it.route == currentRoute }

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    MozhuanBottomBar(navController)
                }
            },
        ) { innerPadding ->
            MozhuanNavHost(
                navController = navController,
                container = container,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
