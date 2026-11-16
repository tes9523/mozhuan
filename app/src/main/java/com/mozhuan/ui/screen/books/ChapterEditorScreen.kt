package com.mozhuan.ui.screen.books

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.system.service.WritingSessionService

/**
 * 章节编辑页：基础文本编辑器 + 「续写本章」按钮（SSE 流式生成正文）。
 * 编辑期间启动前台服务，保持通知栏写作状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterEditorScreen(
    viewModel: ChapterEditorViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // 进入编辑页保持后台存活；离开时结束。
    DisposableEffect(Unit) {
        WritingSessionService.start(context, "章节编辑", title.ifBlank { "未命名章节" }, "自动保存已开启")
        onDispose { WritingSessionService.stop(context) }
    }

    // 生成状态同步到通知栏。
    LaunchedEffect(isGenerating) {
        WritingSessionService.start(
            context,
            "章节编辑",
            title.ifBlank { "未命名章节" },
            if (isGenerating) "AI 续写中…" else "自动保存已开启",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            title = { Text("章节编辑") },
        )

        OutlinedTextField(
            value = title,
            onValueChange = viewModel::onTitleChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            label = { Text("章节标题") },
            singleLine = true,
        )

        OutlinedTextField(
            value = content,
            onValueChange = viewModel::onContentChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("在这里输入章节正文，或点击「续写本章」让 AI 接着写…") },
        )

        if (error != null) {
            Text(
                text = error.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${countWords(content)} 字",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (isGenerating) {
                FilledTonalButton(onClick = viewModel::stop) { Text("停止生成") }
            } else {
                Button(onClick = viewModel::continueWriting) { Text("续写本章") }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onOpenSettings) { Text("API") }
        }
    }
}

private fun countWords(text: String): Int = text.count { !it.isWhitespace() }
