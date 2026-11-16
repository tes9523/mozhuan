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
import com.mozhuan.ui.screen.settings.DisplaySettingsScreen
import com.mozhuan.ui.screen.settings.PersonaScreen
import com.mozhuan.ui.screen.settings.PersonaViewModel
import com.mozhuan.ui.screen.settings.PlaceholderScreen
import com.mozhuan.ui.screen.settings.ProviderConfigScreen
import com.mozhuan.ui.screen.settings.ProviderConfigViewModel
import com.mozhuan.ui.screen.settings.SettingsScreen
import com.mozhuan.ui.screen.settings.SettingsViewModel
import com.mozhuan.ui.screen.settings.SharedFolderScreen
import com.mozhuan.ui.screen.settings.SharedFolderViewModel
import com.mozhuan.ui.screen.settings.SkillScreen
import com.mozhuan.ui.screen.settings.SkillViewModel
import com.mozhuan.ui.screen.settings.TokenUsageScreen
import com.mozhuan.ui.screen.settings.TokenUsageViewModel

/** 全局导航图：三个顶层 Tab + 设置功能页与占位页。 */
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
        composable(Routes.CHAT) {
            val chatViewModel: AiChatViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        AiChatViewModel(
                            providerRepository = container.providerRepository,
                            settingsRepository = container.settingsRepository,
                            skillRepository = container.skillRepository,
                            tokenUsageRepository = container.tokenUsageRepository,
                            aiClient = container.aiClient,
                        )
                    }
                },
            )
            AiChatScreen(
                viewModel = chatViewModel,
                onOpenPersona = {
                    navController.navigate(Routes.PERSONA) { launchSingleTop = true }
                },
                onOpenProviders = {
                    navController.navigate(Routes.PROVIDER) { launchSingleTop = true }
                },
            )
        }

        composable(Routes.BOOKS) {
            BookPlaceholderScreen()
        }

        composable(Routes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SettingsViewModel(container.settingsRepository, container.providerRepository) }
                },
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigate = { route -> navController.navigate(route) },
                onOpenPlaceholder = { title -> navController.navigate(Routes.placeholder(title)) },
            )
        }

        composable(Routes.PROVIDER) {
            val providerViewModel: ProviderConfigViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ProviderConfigViewModel(
                            repository = container.providerRepository,
                        )
                    }
                },
            )
            ProviderConfigScreen(
                viewModel = providerViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.TOKEN_USAGE) {
            val tokenViewModel: TokenUsageViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { TokenUsageViewModel(container.tokenUsageRepository) }
                },
            )
            TokenUsageScreen(
                viewModel = tokenViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.DISPLAY_SETTINGS) {
            val displayViewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SettingsViewModel(container.settingsRepository, container.providerRepository) }
                },
            )
            DisplaySettingsScreen(
                viewModel = displayViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SKILLS) {
            val skillViewModel: SkillViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SkillViewModel(container.skillRepository) }
                },
            )
            SkillScreen(
                viewModel = skillViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SHARED_FOLDER) {
            val sharedViewModel: SharedFolderViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SharedFolderViewModel(
                            settingsRepository = container.settingsRepository,
                            skillRepository = container.skillRepository,
                        )
                    }
                },
            )
            SharedFolderScreen(
                viewModel = sharedViewModel,
                onBack = { navController.popBackStack() },
            )
        }

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
