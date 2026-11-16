package com.mozhuan.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 可用工具：书稿 / 技能 / 记忆的文件读取，书稿写入，网页读取与记忆写入。
 * 关闭某项后，该工具的 schema 不会发送给模型。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("可用工具") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SettingsGroup(
                title = "工具",
                footer = "已启用 ${settings.enabledCount} / 4。工具通过 OpenAI 兼容的 function calling 调用；" +
                    "Anthropic 协议暂不下发工具。所有文件操作仅限 book / skills / memory 三个目录，写入只允许 book/。",
            ) {
                NotificationSwitchRow(
                    title = "读取文件",
                    subtitle = "查看目录、读取书稿 / 技能 / 记忆中的文本",
                    checked = settings.readFiles,
                    onCheckedChange = viewModel::setReadFiles,
                )
                SettingsDivider()
                NotificationSwitchRow(
                    title = "写入书稿",
                    subtitle = "把正文、大纲、设定写入 book/，会自动创建目录",
                    checked = settings.writeFiles,
                    onCheckedChange = viewModel::setWriteFiles,
                )
                SettingsDivider()
                NotificationSwitchRow(
                    title = "读取网页",
                    subtitle = "抓取 http/https 页面并转为纯文本，用于查资料",
                    checked = settings.webFetch,
                    onCheckedChange = viewModel::setWebFetch,
                )
                SettingsDivider()
                NotificationSwitchRow(
                    title = "写入记忆",
                    subtitle = "让 AI 把偏好、设定写入今天的记忆文件",
                    checked = settings.memoryWrite,
                    onCheckedChange = viewModel::setMemoryWrite,
                )
            }
        }
    }
}
