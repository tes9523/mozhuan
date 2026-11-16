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
import com.mozhuan.ui.screen.books.BookDetailScreen
import com.mozhuan.ui.screen.books.BookDetailViewModel
import com.mozhuan.ui.screen.books.BookListScreen
import com.mozhuan.ui.screen.books.BookListViewModel
import com.mozhuan.ui.screen.books.ChapterEditorScreen
import com.mozhuan.ui.screen.books.ChapterEditorViewModel
import com.mozhuan.ui.screen.chat.AiChatScreen
import com.mozhuan.ui.screen.chat.AiChatViewModel
import com.mozhuan.ui.screen.settings.SettingsScreen
import com.mozhuan.ui.screen.settings.SettingsViewModel
import com.mozhuan.ui.screen.skills.SkillManagementScreen
import com.mozhuan.ui.screen.skills.SkillManagementViewModel

/**
 * 全局导航图：三个顶层 Tab + 书稿详情 / 章节编辑 / 技能管理。
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
        // ── Tab1：AI 对话（全局 / 绑定书稿） ───────────────────────
        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument(Routes.ARG_BOOK_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val bookId = entry.arguments?.getString(Routes.ARG_BOOK_ID)?.takeIf { it.isNotBlank() }
            val chatViewModel: AiChatViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        AiChatViewModel(
                            bookId = bookId,
                            bookRepository = container.bookRepository,
                            skillRepository = container.skillRepository,
                            settingsRepository = container.settingsRepository,
                            aiClient = container.aiClient,
                        )
                    }
                },
            )
            AiChatScreen(
                viewModel = chatViewModel,
                onBack = if (bookId == null) null else ({ navController.popBackStack() }),
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        // ── Tab2：书稿列表 ─────────────────────────────────────────
        composable(Routes.BOOKS) {
            val listViewModel: BookListViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { BookListViewModel(container.bookRepository) }
                },
            )
            BookListScreen(
                viewModel = listViewModel,
                onOpenBook = { bookId -> navController.navigate(Routes.bookDetail(bookId)) },
            )
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
                onOpenSkills = { navController.navigate(Routes.SKILLS) },
            )
        }

        // ── 技能管理 ───────────────────────────────────────────────
        composable(Routes.SKILLS) {
            val skillsViewModel: SkillManagementViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SkillManagementViewModel(container.skillRepository) }
                },
            )
            SkillManagementScreen(
                viewModel = skillsViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        // ── 书稿详情 ───────────────────────────────────────────────
        composable(
            route = Routes.BOOK_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType }),
        ) { entry ->
            val bookId = entry.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty()
            val detailViewModel: BookDetailViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        BookDetailViewModel(
                            bookId = bookId,
                            bookRepository = container.bookRepository,
                            skillRepository = container.skillRepository,
                        )
                    }
                },
            )
            BookDetailScreen(
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() },
                onOpenChat = { navController.navigate(Routes.chat(bookId)) },
                onOpenChapter = { chapterId -> navController.navigate(Routes.chapter(bookId, chapterId)) },
            )
        }

        // ── 章节编辑 ───────────────────────────────────────────────
        composable(
            route = Routes.CHAPTER,
            arguments = listOf(
                navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_CHAPTER_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val bookId = entry.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty()
            val chapterId = entry.arguments?.getString(Routes.ARG_CHAPTER_ID).orEmpty()
            val editorViewModel: ChapterEditorViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ChapterEditorViewModel(
                            bookId = bookId,
                            chapterId = chapterId,
                            bookRepository = container.bookRepository,
                            skillRepository = container.skillRepository,
                            settingsRepository = container.settingsRepository,
                            aiClient = container.aiClient,
                        )
                    }
                },
            )
            ChapterEditorScreen(
                viewModel = editorViewModel,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
    }
}
