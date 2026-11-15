package com.mozhuan.ui.screen.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.business.model.ExportFormat
import com.mozhuan.business.model.ExportScope
import com.mozhuan.system.service.WritingSessionService
import com.mozhuan.ui.component.MarkdownPreview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingExport by viewModel.pendingExport.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var previewMode by rememberSaveable { mutableStateOf(false) }
    var showChapterSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val createMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> viewModel.onExportTargetPicked(uri) }
    val createTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> viewModel.onExportTargetPicked(uri) }

    LaunchedEffect(pendingExport) {
        val payload = pendingExport ?: return@LaunchedEffect
        if (payload.mimeType.startsWith("text/markdown")) {
            createMarkdownLauncher.launch(payload.fileName)
        } else {
            createTextLauncher.launch(payload.fileName)
        }
    }

    LaunchedEffect(state.message) {
        val text = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }

    val bookTitle = state.book?.title.orEmpty()
    val chapterTitle = state.selectedChapter?.title.orEmpty()
    val statusText = when (val saveState = state.saveState) {
        SaveState.Saving -> "正在保存"
        is SaveState.Saved -> "已自动保存"
        is SaveState.Failed -> "保存失败"
        else -> "自动保存已开启"
    }

    LaunchedEffect(bookTitle, chapterTitle, statusText) {
        if (bookTitle.isNotEmpty()) {
            WritingSessionService.start(
                context = context,
                bookTitle = bookTitle,
                chapterTitle = chapterTitle.ifBlank { "未选择章节" },
                status = statusText,
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { WritingSessionService.stop(context) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flush()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = bookTitle.ifBlank { "墨撰" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (chapterTitle.isNotBlank()) {
                            Text(
                                text = chapterTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = { previewMode = !previewMode },
                        enabled = state.selectedChapter != null,
                    ) {
                        Text(if (previewMode) "编辑" else "预览")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("新增章节") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.addChapter()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("导出书稿") },
                                onClick = {
                                    menuExpanded = false
                                    showExportDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.selectedChapter != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${state.content.count { !it.isWhitespace() }} 字",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = saveLabel(state.saveState),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.saveState is SaveState.Failed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            if (state.exporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.error != null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) { Text("返回书库") }
                    }
                }

                state.chapters.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "本书暂无章节",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = viewModel::addChapter) { Text("新增章节") }
                    }
                }

                else -> {
                    ChapterSwitcher(
                        state = state,
                        onClick = { showChapterSheet = true },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        if (previewMode) {
                            MarkdownPreview(markdown = state.content)
                        } else {
                            EditorField(
                                value = state.content,
                                onValueChange = viewModel::onContentChanged,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showChapterSheet) {
        ModalBottomSheet(onDismissRequest = { showChapterSheet = false }) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .padding(bottom = 24.dp),
            ) {
                itemsIndexed(state.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                    ListItem(
                        headlineContent = { Text(chapter.title) },
                        supportingContent = { Text("第 ${index + 1} 章 · ${chapter.wordCount} 字") },
                        trailingContent = {
                            if (chapter.id == state.selectedChapter?.id) {
                                Text(
                                    text = "当前",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            showChapterSheet = false
                            viewModel.selectChapter(chapter)
                        },
                    )
                }
                item {
                    TextButton(
                        onClick = {
                            showChapterSheet = false
                            viewModel.addChapter()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("新增章节")
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        ExportDialog(
            chapterAvailable = state.selectedChapter != null,
            onDismiss = { showExportDialog = false },
            onConfirm = { scope, format ->
                showExportDialog = false
                viewModel.prepareExport(scope, format)
            },
        )
    }
}

@Composable
private fun ChapterSwitcher(state: EditorUiState, onClick: () -> Unit) {
    val chapter = state.selectedChapter ?: return
    val index = state.chapters.indexOfFirst { it.id == chapter.id }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "第 ${index + 1} / ${state.chapters.size} 章",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "章节列表",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EditorField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 28.sp,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = "开始写作…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun ExportDialog(
    chapterAvailable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ExportScope, ExportFormat) -> Unit,
) {
    var scope by remember { mutableStateOf(if (chapterAvailable) ExportScope.CHAPTER else ExportScope.BOOK) }
    var format by remember { mutableStateOf(ExportFormat.MARKDOWN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出书稿") },
        text = {
            Column {
                Text("导出范围", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    label = "当前章节",
                    selected = scope == ExportScope.CHAPTER,
                    enabled = chapterAvailable,
                    onSelect = { scope = ExportScope.CHAPTER },
                )
                ChoiceRow(
                    label = "全书",
                    selected = scope == ExportScope.BOOK,
                    enabled = true,
                    onSelect = { scope = ExportScope.BOOK },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("文件格式", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    label = "Markdown (.md)",
                    selected = format == ExportFormat.MARKDOWN,
                    enabled = true,
                    onSelect = { format = ExportFormat.MARKDOWN },
                )
                ChoiceRow(
                    label = "纯文本 (.txt)",
                    selected = format == ExportFormat.TXT,
                    enabled = true,
                    onSelect = { format = ExportFormat.TXT },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(scope, format) }) { Text("导出") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onSelect() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

private fun saveLabel(saveState: SaveState): String = when (saveState) {
    SaveState.Idle -> "已加载"
    SaveState.Pending -> "未保存"
    SaveState.Saving -> "保存中…"
    is SaveState.Saved -> {
        val time = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(saveState.at))
        "已保存 $time"
    }

    is SaveState.Failed -> "保存失败"
}
