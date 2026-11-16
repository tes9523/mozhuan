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
import kotlin.math.roundToInt

/**
 * 设置页：OpenMinis 风格卡片分组。
 *
 * 已做实：LLM 提供商配置、外观（主题 / 字号）、AGENT 人格系统。
 * 其余入口保留 UI，页面为占位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenProvider: () -> Unit,
    onOpenPersona: () -> Unit,
    onOpenPlaceholder: (String) -> Unit,
) {
    val themeModeValue by viewModel.themeMode.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val apiSettings by viewModel.apiSettings.collectAsStateWithLifecycle()

    val themeMode = ThemeMode.from(themeModeValue)
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
            // ── LLM 提供商 ────────────────────────────────────────
            SettingsGroup(title = "LLM 提供商") {
                SettingsRow(
                    icon = Icons.Filled.Build,
                    title = "管理提供商",
                    subtitle = if (apiSettings.isConfigured) {
                        "当前模型：${apiSettings.model}"
                    } else {
                        "未配置，填写接口地址 / API Key / 模型"
                    },
                    onClick = onOpenProvider,
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
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("Token 用量") },
                )
            }

            // ── 外观 ──────────────────────────────────────────────
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
            }

            // ── AGENT 运行时 ──────────────────────────────────────
            SettingsGroup(title = "AGENT 运行时") {
                SettingsRow(
                    icon = Icons.Filled.Person,
                    title = "人格系统（SOUL.md）",
                    subtitle = "全局人格 Prompt，所有对话自动携带",
                    onClick = onOpenPersona,
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = "记忆",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("记忆") },
                )
            }

            // ── 存储 ──────────────────────────────────────────────
            SettingsGroup(title = "存储") {
                SettingsRow(
                    icon = Icons.Filled.Share,
                    title = "备份与恢复",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("备份与恢复") },
                )
            }

            // ── 网络 ──────────────────────────────────────────────
            SettingsGroup(title = "网络") {
                SettingsRow(
                    icon = Icons.Filled.Lock,
                    title = "代理设置",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("代理设置") },
                )
            }

            // ── 后台与通知 ────────────────────────────────────────
            SettingsGroup(title = "后台与通知") {
                SettingsRow(
                    icon = Icons.Filled.Notifications,
                    title = "后台运行",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("后台运行") },
                )
            }

            // ── 日志 ──────────────────────────────────────────────
            SettingsGroup(title = "日志") {
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "运行日志",
                    subtitle = "预留入口",
                    onClick = { onOpenPlaceholder("运行日志") },
                )
            }

            // ── 关于 ──────────────────────────────────────────────
            SettingsGroup(title = "关于") {
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = "版本号",
                    subtitle = "0.5.0",
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
