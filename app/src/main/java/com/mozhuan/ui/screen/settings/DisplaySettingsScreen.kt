package com.mozhuan.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 对话显示设置，全部立即生效并持久化。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.displaySettings.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("显示设置") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )

        SettingsGroup(title = "对话") {
            SettingsRow(
                icon = Icons.Filled.Info,
                title = "显示消息时间",
                subtitle = "在消息气泡下方显示发送时间",
                onClick = {
                    viewModel.setDisplaySettings(settings.copy(showTimestamps = !settings.showTimestamps))
                },
                trailing = {
                    Switch(
                        checked = settings.showTimestamps,
                        onCheckedChange = {
                            viewModel.setDisplaySettings(settings.copy(showTimestamps = it))
                        },
                    )
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Create,
                title = "紧凑消息气泡",
                subtitle = "减小气泡内边距与消息间距",
                onClick = {
                    viewModel.setDisplaySettings(settings.copy(compactMessages = !settings.compactMessages))
                },
                trailing = {
                    Switch(
                        checked = settings.compactMessages,
                        onCheckedChange = {
                            viewModel.setDisplaySettings(settings.copy(compactMessages = it))
                        },
                    )
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.List,
                title = "自动滚动到底部",
                subtitle = "新消息与流式输出时跟随最新内容",
                onClick = {
                    viewModel.setDisplaySettings(settings.copy(autoScroll = !settings.autoScroll))
                },
                trailing = {
                    Switch(
                        checked = settings.autoScroll,
                        onCheckedChange = {
                            viewModel.setDisplaySettings(settings.copy(autoScroll = it))
                        },
                    )
                },
            )
        }

        Text(
            text = "修改后立即应用到对话页，并保存在本地。",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
