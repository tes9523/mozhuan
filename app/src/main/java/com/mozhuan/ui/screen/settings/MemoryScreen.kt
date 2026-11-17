package com.mozhuan.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记忆：GLOBAL.md 长期记忆 + 按天记录。
 * 开启后，全局记忆与最近两天记录会随系统提示词一起发送。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel,
    onBack: () -> Unit,
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    var editingName by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    val editing = editingName
    // 系统返回键先退出记忆编辑，回到记忆列表。
    BackHandler(enabled = editing != null) { editingName = null }
    if (editing != null) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(if (editing == "GLOBAL.md") "全局记忆" else editing) },
                    navigationIcon = {
                        IconButton(onClick = { editingName = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                viewModel.save(editing, draft)
                                editingName = null
                            },
                        ) {
                            Text("保存", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
            },
        ) { padding ->
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("写下值得长期记住的偏好、设定或约定…") },
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("记忆") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val name = viewModel.ensureTodayFile()
                            draft = viewModel.read(name)
                            editingName = name
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "新建今日记录")
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
                title = "记忆",
                footer = "记忆文件存放在应用私有目录 memory/ 下。开启后，GLOBAL.md 与最近两天的记录会随系统提示词发送；" +
                    "也可以让 AI 通过「记忆写入」工具自动记录。",
            ) {
                NotificationSwitchRow(
                    title = "启用记忆",
                    subtitle = "把全局记忆与近期记录注入对话上下文",
                    checked = enabled,
                    onCheckedChange = viewModel::setEnabled,
                )
            }

            SettingsGroup(title = "记忆文件") {
                files.forEachIndexed { index, file ->
                    if (index > 0) SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                draft = viewModel.read(file.name)
                                editingName = file.name
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (file.isGlobal) "全局记忆（GLOBAL.md）" else file.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (file.isGlobal) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = buildString {
                                    append(formatTime(file.modifiedAt))
                                    append(" · ")
                                    append(formatSize(file.size))
                                    if (file.preview.isNotBlank()) {
                                        append(" · ")
                                        append(file.preview)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!file.isGlobal) {
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { pendingDelete = file.name },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (files.isEmpty()) {
                    Text(
                        text = "还没有记忆记录。点击右上角「+」写下今天的记录，或让 AI 通过工具写入。",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    val deleting = pendingDelete
    if (deleting != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除记忆") },
            text = { Text("将删除 $deleting，此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(deleting)
                        pendingDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatSize(size: Long): String = when {
    size >= 1024 -> "${size / 1024}KB"
    else -> "${size}B"
}
