package com.mozhuan.ui.screen.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.ChatMessage
import com.mozhuan.data.model.ChatRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** AI 对话页：流式气泡、重新生成、复制、停止与清空确认。 */
@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel,
    onOpenPersona: () -> Unit,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val displaySettings by viewModel.displaySettings.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showClearDialog by remember { mutableStateOf(false) }

    val hideKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(
        displaySettings.autoScroll,
        messages.size,
        messages.lastOrNull()?.content?.length,
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
        // 紧凑顶栏：仅保留必要系统栏高度，消息区占满剩余空间。
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "AI 对话",
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
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
                    modifier = Modifier.padding(top = 120.dp),
                ) {
                    Text(
                        text = "开始一段新的对话",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "人格与已启用技能会自动合并发送，模型参数取自默认服务商",
                        modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
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
                        compact = displaySettings.compactMessages,
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
            )
            Spacer(Modifier.width(8.dp))
            if (isStreaming) {
                FilledTonalButton(onClick = viewModel::stop) {
                    Text("停止生成")
                }
            } else {
                Button(
                    onClick = viewModel::send,
                    enabled = input.isNotBlank(),
                ) {
                    Text("发送")
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
    compact: Boolean,
    showTimestamp: Boolean,
    canRegenerate: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val isUser = message.role == ChatRole.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            if (message.content.isEmpty() && !isUser) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = "正在生成…",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(if (compact) 9.dp else 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
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
