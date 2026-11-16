package com.mozhuan.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.ThemeMode
import com.mozhuan.ui.navigation.Routes
import kotlin.math.roundToInt

/** OpenMinis 风格设置主页；实功能与占位入口分别跳转到对应页面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigate: (String) -> Unit,
    onOpenPlaceholder: (String) -> Unit,
) {
    val themeModeValue by viewModel.themeMode.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val defaultProviderId by viewModel.defaultProviderId.collectAsStateWithLifecycle()

    val themeMode = ThemeMode.from(themeModeValue)
    val defaultProvider = providers.firstOrNull { it.id == defaultProviderId } ?: providers.firstOrNull()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("设置") })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SettingsGroup(title = "LLM 提供商") {
                SettingsRow(
                    icon = Icons.Filled.Build,
                    title = "管理提供商",
                    subtitle = if (defaultProvider?.isConfigured == true) {
                        "默认：${defaultProvider.displayName} · ${defaultProvider.model}"
                    } else {
                        "新增、编辑、删除服务商并设置默认项"
                    },
                    onClick = { onNavigate(Routes.PROVIDER) },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "模型组",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("模型组") },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Star,
                    title = "Token 用量",
                    subtitle = "当前会话与按服务商、模型累计统计",
                    onClick = { onNavigate(Routes.TOKEN_USAGE) },
                )
            }

            SettingsGroup(title = "外观") {
                SettingsRow(
                    icon = Icons.Filled.Settings,
                    title = "主题",
                    subtitle = themeMode.label,
                    onClick = { showThemeDialog = true },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Create,
                    title = "字体大小",
                    subtitle = "当前 ${(fontScale * 100).roundToInt()}%",
                    onClick = { showFontDialog = true },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "显示设置",
                    subtitle = "消息时间、紧凑气泡、自动滚动",
                    onClick = { onNavigate(Routes.DISPLAY_SETTINGS) },
                )
            }

            SettingsGroup(title = "AGENT 运行时") {
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "技能",
                    subtitle = "静态提示词模板、开关与单条导入导出",
                    onClick = { onNavigate(Routes.SKILLS) },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Person,
                    title = "人格系统（SOUL.md）",
                    subtitle = "全局人格 Prompt，所有对话自动携带",
                    onClick = { onNavigate(Routes.PERSONA) },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Build,
                    title = "可用工具",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("可用工具") },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = "记忆",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("记忆") },
                )
            }

            SettingsGroup(title = "存储") {
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "共享文件夹",
                    subtitle = "预览、导入、导出技能与人格文本",
                    onClick = { onNavigate(Routes.SHARED_FOLDER) },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Share,
                    title = "挂载外部文件夹",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("挂载外部文件夹") },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Share,
                    title = "备份与恢复",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("备份与恢复") },
                )
            }

            SettingsGroup(title = "其他") {
                SettingsRow(
                    icon = Icons.Filled.Lock,
                    title = "代理设置",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("代理设置") },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Notifications,
                    title = "后台与通知",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("后台与通知") },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "运行日志",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("运行日志") },
                )
            }

            SettingsGroup(title = "关于") {
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = "版本号",
                    subtitle = "0.8.0",
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Email,
                    title = "反馈",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("反馈") },
                )
            }
        }
    }

    if (showThemeDialog) {
        ThemeDialog(
            current = themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showFontDialog) {
        FontScaleDialog(
            scale = fontScale,
            onScaleChange = viewModel::setFontScale,
            onDismiss = { showFontDialog = false },
        )
    }
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
    }

@Composable
private fun ThemeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主题") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = { onSelect(mode) })
                        Text(
                            text = mode.label,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun FontScaleDialog(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("字体大小") },
        text = {
            Column {
                Text(
                    text = "${(scale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = scale,
                    onValueChange = onScaleChange,
                    valueRange = 0.85f..1.35f,
                    steps = 9,
                )
                Text(
                    text = "仅调整文字大小，不改变卡片与间距",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = { onScaleChange(1f) }) { Text("恢复默认") }
        },
    )
}
