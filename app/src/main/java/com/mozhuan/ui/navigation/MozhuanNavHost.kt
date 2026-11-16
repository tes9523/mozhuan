package com.mozhuan.ui.navigation

import android.net.Uri
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
import com.mozhuan.ui.screen.books.BookPlaceholderScreen
import com.mozhuan.ui.screen.chat.AiChatScreen
import com.mozhuan.ui.screen.chat.AiChatViewModel
import com.mozhuan.ui.screen.settings.PersonaScreen
import com.mozhuan.ui.screen.settings.PersonaViewModel
import com.mozhuan.ui.screen.settings.PlaceholderScreen
import com.mozhuan.ui.screen.settings.ProviderConfigScreen
import com.mozhuan.ui.screen.settings.ProviderConfigViewModel
import com.mozhuan.ui.screen.settings.SettingsScreen
import com.mozhuan.ui.screen.settings.SettingsViewModel

/**
 * 全局导航图：三个顶层 Tab + 设置子页（提供商 / 人格 / 占位）。
 */
@Composable
fun MozhuanNavHost(
    navController: NavHostController,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CHAT,
        modifier = modifier,
    ) {
        // ── Tab1：AI 对话 ──────────────────────────────────────────
        composable(Routes.CHAT) {
            val chatViewModel: AiChatViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        AiChatViewModel(
                            settingsRepository = container.settingsRepository,
                            aiClient = container.aiClient,
                        )
                    }
                },
            )
            AiChatScreen(
                viewModel = chatViewModel,
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                },
            )
        }

        // ── Tab2：书稿（占位） ─────────────────────────────────────
        composable(Routes.BOOKS) {
            BookPlaceholderScreen()
        }

        // ── Tab3：设置 ─────────────────────────────────────────────
        composable(Routes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SettingsViewModel(container.settingsRepository) }
                },
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onOpenProvider = { navController.navigate(Routes.PROVIDER) },
                onOpenPersona = { navController.navigate(Routes.PERSONA) },
                onOpenPlaceholder = { title -> navController.navigate(Routes.placeholder(title)) },
            )
        }

        // ── 设置 · 管理提供商 ─────────────────────────────────────
        composable(Routes.PROVIDER) {
            val providerViewModel: ProviderConfigViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ProviderConfigViewModel(
                            settingsRepository = container.settingsRepository,
                            aiClient = container.aiClient,
                        )
                    }
                },
            )
            ProviderConfigScreen(
                viewModel = providerViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        // ── 设置 · 人格系统 ───────────────────────────────────────
        composable(Routes.PERSONA) {
            val personaViewModel: PersonaViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { PersonaViewModel(container.settingsRepository) }
                },
            )
            PersonaScreen(
                viewModel = personaViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        // ── 设置 · 通用占位子页 ───────────────────────────────────
        composable(
            route = Routes.PLACEHOLDER,
            arguments = listOf(navArgument(Routes.ARG_TITLE) { type = NavType.StringType }),
        ) { entry ->
            val title = entry.arguments?.getString(Routes.ARG_TITLE).orEmpty().let(Uri::decode)
            PlaceholderScreen(
                title = title,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
