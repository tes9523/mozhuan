package com.mozhuan.ui.screen.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.ChatMessage
import com.mozhuan.data.model.ChatRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** AI 对话页：模型切换、思考过程、流式气泡、重新生成、复制、停止与清空确认。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel,
    onOpenPersona: () -> Unit,
    onOpenProviders: () -> Unit = {},
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val displaySettings by viewModel.displaySettings.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val activeGroup by viewModel.activeGroup.collectAsStateWithLifecycle()
    val groupOptions by viewModel.groupOptions.collectAsStateWithLifecycle()
    val modelOptions by viewModel.modelOptions.collectAsStateWithLifecycle()
    val selectedEntryId by viewModel.selectedEntryId.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val currentGroup = activeGroup?.let { group ->
        groupOptions.firstOrNull { it.group.id == group.id }?.group
    }

    val listState = rememberLazyListState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    val modelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val hideKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(
        displaySettings.autoScroll,
        messages.size,
        messages.lastOrNull()?.content?.length,
        messages.lastOrNull()?.reasoning?.length,
    ) {
        if (displaySettings.autoScroll && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // 紧凑顶栏：标题下方直接显示当前模型，取消无效留白。
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showModelSheet = true }
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Text(
                        text = "AI 对话",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentGroup?.let { "模型组 · ${it.name}" }
                                ?: (activeModel?.title ?: "未选择模型"),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (activeModel == null && currentGroup == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp),
                        )
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = "切换模型",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (messages.isNotEmpty() && !isStreaming) {
                    TextButton(onClick = { showClearDialog = true }) { Text("清空") }
                }
                IconButton(onClick = onOpenPersona) {
                    Icon(Icons.Filled.Settings, contentDescription = "人格设置")
                }
            }
        }

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { hideKeyboard() })
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 64.dp),
                ) {
                    Text(
                        text = if (activeModel == null && currentGroup == null) {
                            "还没有可用模型"
                        } else {
                            "开始一段新的对话"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = when {
                            activeModel == null && currentGroup == null ->
                                "添加服务商并选择一个模型后即可开始写作"
                            currentGroup != null -> {
                                val option = groupOptions.firstOrNull { it.group.id == currentGroup.id }
                                "模型组「${currentGroup.name}」· ${option?.members?.size ?: 0} 个模型 · " +
                                    currentGroup.strategy.label
                            }
                            else -> "人格与已启用技能会自动合并；当前使用 " +
                                "${activeModel?.provider?.displayName} · ${activeModel?.entry?.title}"
                        },
                        modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (activeModel == null && currentGroup == null) {
                        TextButton(onClick = onOpenProviders) {
                            Text("去添加服务商")
                        }
                    } else if (currentGroup != null) {
                        val option = groupOptions.firstOrNull { it.group.id == currentGroup.id }
                        Text(
                            text = option?.members?.joinToString(" → ") { it.entry.title }.orEmpty(),
                            modifier = Modifier.padding(top = 12.dp, start = 32.dp, end = 32.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        val entry = activeModel?.entry
                        if (entry != null) {
                            Text(
                                text = "temperature ${entry.temperature} · top_p ${entry.topP} · 上下文 ${entry.contextTurns} 轮",
                                modifier = Modifier.padding(top = 12.dp, start = 32.dp, end = 32.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { hideKeyboard() })
                    },
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(
                    if (displaySettings.compactMessages) 6.dp else 12.dp,
                ),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        showTimestamp = displaySettings.showTimestamps,
                        canRegenerate = !isStreaming,
                        onCopy = { copyToClipboard(context, message.content) },
                        onRegenerate = { viewModel.regenerate(message.id) },
                    )
                }
            }
        }

        if (error != null) {
            Text(
                text = error.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("和 AI 聊聊你的故事…") },
                minLines = 1,
                maxLines = 6,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            Spacer(Modifier.width(8.dp))
            if (isStreaming) {
                FilledIconButton(
                    onClick = viewModel::stop,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                MaterialTheme.colorScheme.onPrimaryContainer,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            } else {
                FilledIconButton(
                    onClick = viewModel::send,
                    enabled = input.isNotBlank(),
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "发送")
                }
            }
        }
    }

    if (showModelSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            sheetState = modelSheetState,
        ) {
            Text(
                text = "选择模型",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "模型组与模型参数在「设置」中配置",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (modelOptions.isEmpty() && groupOptions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "还没有可用模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            showModelSheet = false
                            onOpenProviders()
                        },
                    ) {
                        Text("去添加服务商")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (groupOptions.isNotEmpty()) {
                        item(key = "group-header") {
                            Text(
                                text = "模型组",
                                modifier = Modifier.padding(
                                    start = 24.dp,
                                    end = 24.dp,
                                    top = 16.dp,
                                    bottom = 4.dp,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(groupOptions, key = { "group-${it.group.id}" }) { option ->
                            val selected = option.group.id == selectedGroupId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectGroup(option.group.id)
                                        showModelSheet = false
                                    }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.group.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${option.members.size} 个模型 · " +
                                            option.group.strategy.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "当前模型组",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        item(key = "model-header") {
                            Text(
                                text = "全部模型",
                                modifier = Modifier.padding(
                                    start = 24.dp,
                                    end = 24.dp,
                                    top = 16.dp,
                                    bottom = 4.dp,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val grouped = modelOptions.groupBy { it.provider }
                    grouped.forEach { (provider, options) ->
                        item(key = "provider-${provider.id}") {
                            Text(
                                text = provider.displayName,
                                modifier = Modifier.padding(
                                    start = 24.dp,
                                    end = 24.dp,
                                    top = 16.dp,
                                    bottom = 4.dp,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(options, key = { it.entry.id }) { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectModel(option.entry.id)
                                        showModelSheet = false
                                    }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.entry.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "max_tokens " +
                                            (option.entry.maxTokens.takeIf { it > 0 }?.toString()
                                                ?: "服务商默认") +
                                            " · 上下文 ${option.entry.contextTurns} 轮",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (option.entry.id == selectedEntryId &&
                                    selectedGroupId.isBlank()
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "当前模型",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空对话") },
            text = { Text("将删除当前全部消息，并清空本次会话 Token 用量。此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearMessages()
                        showClearDialog = false
                    },
                ) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    showTimestamp: Boolean,
    canRegenerate: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val isUser = message.role == ChatRole.USER
    var reasoningExpanded by remember(message.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (isUser) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            if (message.reasoning.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { reasoningExpanded = !reasoningExpanded },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "思考过程",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                imageVector = if (reasoningExpanded) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (reasoningExpanded) {
                            Text(
                                text = message.reasoning,
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (message.content.isEmpty()) {
                if (message.reasoning.isBlank()) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "正在生成…",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                // 助手消息不加气泡，直接以正文呈现（OpenMinis 结构）
                Text(
                    text = message.content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (showTimestamp && message.content.isNotEmpty()) {
            Text(
                text = formatTime(message.createdAt),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!isUser && message.content.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("复制", style = MaterialTheme.typography.labelSmall)
                }
                if (canRegenerate) {
                    TextButton(
                        onClick = onRegenerate,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("重新生成", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun copyToClipboard(context: Context, text: String) {
    if (text.isEmpty()) return
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("墨撰", text))
}
