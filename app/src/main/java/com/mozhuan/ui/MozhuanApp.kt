package com.mozhuan.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
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
        val focusManager = LocalFocusManager.current

        Scaffold(
            modifier = Modifier.pointerInput(Unit) {
                // 点击输入框以外的空白区域时收起输入法（输入框与按钮会自行消费点击）。
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
            // 输入法只由对话页的 imePadding 处理，根布局不能再为键盘预留一次底部空间。
            contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
            bottomBar = {
                if (showBottomBar) {
                    MozhuanBottomBar(navController)
                }
            },
        ) { innerPadding ->
            MozhuanNavHost(
                navController = navController,
                container = container,
                // 已由 Scaffold 消费的内边距不再参与子页面 imePadding 计算，
                // 避免输入法弹出时在输入框与键盘之间多出一段空白。
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            )
        }
    }
}
