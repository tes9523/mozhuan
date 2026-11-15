package com.mozhuan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mozhuan.AppContainer
import com.mozhuan.business.model.ThemeMode
import com.mozhuan.ui.screen.editor.EditorScreen
import com.mozhuan.ui.screen.editor.EditorViewModel
import com.mozhuan.ui.screen.library.LibraryScreen
import com.mozhuan.ui.screen.library.LibraryViewModel
import com.mozhuan.ui.screen.settings.SettingsScreen
import com.mozhuan.ui.screen.settings.SettingsViewModel
import com.mozhuan.ui.theme.MozhuanTheme

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_EDITOR = "editor/{bookId}"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun MozhuanRoot(container: AppContainer) {
    val themeMode by container.settingsRepository.themeMode.collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MozhuanTheme(darkTheme = darkTheme) {
        MozhuanNavHost(container)
    }
}

@Composable
private fun MozhuanNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_LIBRARY) {
        composable(ROUTE_LIBRARY) {
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
                onOpenBook = { bookId -> navController.navigate("editor/$bookId") },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }

        composable(
            route = ROUTE_EDITOR,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { entry ->
            val bookId = entry.arguments?.getString("bookId").orEmpty()
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

        composable(ROUTE_SETTINGS) {
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
