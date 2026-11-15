package com.mozhuan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mozhuan.AppContainer
import com.mozhuan.ui.screen.about.AboutScreen
import com.mozhuan.ui.screen.demo.DemoScreen
import com.mozhuan.ui.screen.editor.EditorScreen
import com.mozhuan.ui.screen.editor.EditorViewModel
import com.mozhuan.ui.screen.library.LibraryScreen
import com.mozhuan.ui.screen.library.LibraryViewModel
import com.mozhuan.ui.screen.settings.SettingsScreen
import com.mozhuan.ui.screen.settings.SettingsViewModel

/**
 * 全局导航图。
 *
 * 顶层页面（首页 / 演示页 / 关于页）与全屏页面（编辑器 / 设置）放在同一个 NavHost 中，
 * 底部导航栏由外层根据当前路由决定是否显示。
 */
@Composable
fun MozhuanNavHost(
    navController: NavHostController,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        // 首页：阶段0 的书稿列表，功能保持不变。
        composable(Routes.HOME) {
            val libraryViewModel: LibraryViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        LibraryViewModel(
                            bookRepository = container.bookRepository,
                            settingsRepository = container.settingsRepository,
                        )
                    }
                },
            )
            LibraryScreen(
                viewModel = libraryViewModel,
                onOpenBook = { bookId -> navController.navigate(Routes.editor(bookId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        // 演示页：仅包含基础点击反馈，无业务逻辑。
        composable(Routes.DEMO) {
            DemoScreen()
        }

        // 关于页：静态应用信息 + 全局设置入口。
        composable(Routes.ABOUT) {
            AboutScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }

        // 书稿编辑器（全屏）。
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType }),
        ) { entry ->
            val bookId = entry.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty()
            val editorViewModel: EditorViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        EditorViewModel(
                            bookId = bookId,
                            repository = container.bookRepository,
                            persistScope = container.applicationScope,
                        )
                    }
                },
            )
            EditorScreen(
                viewModel = editorViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        // 全局设置（全屏）。
        composable(Routes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(container.settingsRepository)
                    }
                },
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
