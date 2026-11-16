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
import com.mozhuan.data.model.ThemeMode
import com.mozhuan.ui.component.MozhuanBottomBar
import com.mozhuan.ui.navigation.MozhuanNavHost
import com.mozhuan.ui.navigation.TopLevelDestination
import com.mozhuan.ui.theme.MozhuanTheme

/**
 * 应用根节点：主题 + 字号 + 全局 Scaffold + 导航图。
 *
 * 底部导航栏只在三个顶层 Tab 显示；设置子页为全屏页面。
 */
@Composable
fun MozhuanRoot(container: AppContainer) {
    val themeModeValue by container.settingsRepository.themeMode.collectAsStateWithLifecycle()
    val fontScale by container.settingsRepository.fontScale.collectAsStateWithLifecycle()
    val darkTheme = when (ThemeMode.from(themeModeValue)) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MozhuanTheme(darkTheme = darkTheme, fontScale = fontScale) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
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
