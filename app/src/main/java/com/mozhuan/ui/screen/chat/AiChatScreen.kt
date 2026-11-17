package com.mozhuan.ui.screen.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.TypedValue
import android.widget.TextView
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.ChatMessage
import com.mozhuan.data.model.ChatRole
import com.mozhuan.data.model.Skill
import com.mozhuan.data.model.ThinkingLevel
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
    val lastUserMessageId = remember(messages) { messages.lastOrNull { it.role == ChatRole.USER }?.id }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val displaySettings by viewModel.displaySettings.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val activeGroup by viewModel.activeGroup.collectAsStateWithLifecycle()
    val groupOptions by viewModel.groupOptions.collectAsStateWithLifecycle()
    val modelOptions by viewModel.modelOptions.collectAsStateWithLifecycle()
    val selectedEntryId by viewModel.selectedEntryId.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val toolStatus by viewModel.toolStatus.collectAsStateWithLifecycle()
    val thinkingLevel by viewModel.thinkingLevel.collectAsStateWithLifecycle()
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val currentGroup = activeGroup?.let { group ->
        groupOptions.firstOrNull { it.group.id == group.id }?.group
    }

    val listState = rememberLazyListState()
    val atTop by remember { derivedStateOf { !listState.canScrollBackward } }
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showThinkingSheet by remember { mutableStateOf(false) }
    var pendingDeleteSessionId by remember { mutableStateOf<String?>(null) }
    var messageActions by remember { mutableStateOf<ChatMessage?>(null) }
    var messageActionAnchor by remember { mutableStateOf(IntOffset.Zero) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var pendingDeleteMessageId by remember { mutableStateOf<String?>(null) }
    val modelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val thinkingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val hideKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    // 输入框末尾的 “@关键词” 触发技能选择，选择后把 @名称 插入正文。
    val mentionMatch = remember(input) {
        Regex("(^|\\s)@([\\p{L}\\p{N}_-]*)$").find(input)
    }
    val mentionQuery = mentionMatch?.groupValues?.getOrNull(2).orEmpty().lowercase()
    val mentionCandidates = remember(mentionMatch?.value, skills) {
        if (mentionMatch == null) {
            emptyList()
        } else {
            skills.filter { it.enabled }
                .filter {
                    mentionQuery.isEmpty() ||
                        it.name.lowercase().contains(mentionQuery) ||
                        it.id.lowercase().contains(mentionQuery)
                }
                .take(8)
        }
    }
    val applyMention: (Skill) -> Unit = { skill ->
        val match = mentionMatch
        if (match != null) {
            val head = input.substring(0, match.range.first)
            val tail = input.substring(match.range.first)
                .replaceFirst(Regex("@([\\p{L}\\p{N}_-]*)$"), "@${skill.name} ")
            viewModel.onInputChange(head + tail)
        }
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

    // 输入法弹出 / 输入框增高导致列表可视高度变化时，保持贴底，让内容跟随输入框上移。
    LaunchedEffect(displaySettings.autoScroll, messages.size) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .collect { height ->
                if (height > 0 && displaySettings.autoScroll && messages.isNotEmpty()) {
                    listState.scrollToItem(messages.lastIndex)
                }
            }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                sessions = sessions,
                activeSessionId = activeSessionId,
                onNewSession = {
                    hideKeyboard()
                    viewModel.newSession()
                    scope.launch { drawerState.close() }
                },
                onSelect = { sessionId ->
                    hideKeyboard()
                    viewModel.openSession(sessionId)
                    scope.launch { drawerState.close() }
                },
                onDelete = { sessionId -> pendingDeleteSessionId = sessionId },
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
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
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        hideKeyboard()
                        scope.launch { drawerState.open() }
                    },
                ) {
                    Icon(Icons.Filled.Menu, contentDescription = "所有会话")
                }
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
                Surface(
                    onClick = { showThinkingSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(end = 2.dp),
                ) {
                    Text(
                        text = "思考·${thinkingLevel.label}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
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
                            onAddToInput = {
                                viewModel.onInputChange((input.ifBlank { "" } + "\n" + message.content).trim())
                            },
                            onShowActions = { anchor ->
                                hideKeyboard()
                                messageActions = message
                                messageActionAnchor = anchor
                            },
                        )
                    }
                }

                // 一键到顶 / 到底：在顶部只显示到底，在底部只显示到顶。
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!atTop) {
                        ScrollToButton(up = true) {
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    }
                    if (!atBottom) {
                        ScrollToButton(up = false) {
                            scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                        }
                    }
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

        if (toolStatus != null) {
            Text(
                text = toolStatus.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            if (mentionCandidates.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        mentionCandidates.forEach { skill ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { applyMention(skill) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = skill.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "skills/${skill.id}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        text = "skills",
                                        modifier = Modifier.padding(
                                            horizontal = 6.dp,
                                            vertical = 2.dp,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val editMessage = editingMessage
            val isEditingInline = editMessage != null
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
                placeholder = { Text(if (isEditingInline) "修改中…发送以覆盖原消息" else "和 AI 聊聊你的故事…") },
                minLines = 1,
                maxLines = 6,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = if (isEditingInline) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (isEditingInline) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            Spacer(Modifier.width(8.dp))
            if (isEditingInline) {
                FilledIconButton(
                    onClick = {
                        editingMessage = null
                        viewModel.onInputChange("")
                    },
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "取消修改")
                }
                Spacer(Modifier.width(8.dp))
            }
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
                    onClick = {
                        if (isEditingInline) {
                            viewModel.editUserMessage(editMessage!!.id, input)
                            editingMessage = null
                            viewModel.onInputChange("")
                        } else {
                            viewModel.send()
                        }
                    },
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

    if (showThinkingSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThinkingSheet = false },
            sheetState = thinkingSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "思考强度",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "仅对当前会话生效；初始跟随模型默认值，未配置时使用中度。",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThinkingLevel.entries.forEach { level ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setThinkingLevel(level)
                                showThinkingSheet = false
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = level.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (level == thinkingLevel) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "当前强度",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
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

    val deletingSessionId = pendingDeleteSessionId
    if (deletingSessionId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteSessionId = null },
            title = { Text("删除会话") },
            text = { Text("该会话的全部消息将被删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(deletingSessionId)
                        pendingDeleteSessionId = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSessionId = null }) { Text("取消") }
            },
        )
    }

    val actionMessage = messageActions
    Box(modifier = Modifier.fillMaxSize()) {
        if (actionMessage != null) {
            val isUserAction = actionMessage.role == ChatRole.USER
            val anchor = messageActionAnchor
            Popup(
                // 自定义定位：弹层紧贴气泡/手指位置，而不是贴在屏幕角落。
                popupPositionProvider = remember(anchor, isUserAction) {
                    BubblePopupPositionProvider(anchor, alignEnd = isUserAction)
                },
                onDismissRequest = { messageActions = null },
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.widthIn(min = 160.dp, max = 260.dp),
                ) {
                    Column {
                        MessageActionPopupItem(
                            text = "复制",
                            onClick = {
                                copyToClipboard(context, actionMessage.content)
                                messageActions = null
                            },
                        )
                        if (!isUserAction) {
                            MessageActionPopupItem(
                                text = "添加到输入框",
                                onClick = {
                                    viewModel.onInputChange((input.ifBlank { "" } + "\n" + actionMessage.content).trim())
                                    messageActions = null
                                },
                            )
                        }
                        if (!isStreaming) {
                            if (isUserAction) {
                                if (actionMessage.id == lastUserMessageId) {
                                    MessageActionPopupItem(
                                        text = "编辑",
                                        onClick = {
                                            editingMessage = actionMessage
                                            editDraft = actionMessage.content
                                            messageActions = null
                                            viewModel.onInputChange(actionMessage.content)
                                        },
                                    )
                                }
                                MessageActionPopupItem(
                                    text = "删除",
                                    destructive = true,
                                    onClick = {
                                        pendingDeleteMessageId = actionMessage.id
                                        messageActions = null
                                    },
                                )
                            } else {
                                MessageActionPopupItem(
                                    text = "重新生成",
                                    onClick = {
                                        viewModel.regenerate(actionMessage.id)
                                        messageActions = null
                                    },
                                )
                                MessageActionPopupItem(
                                    text = "从此处删除",
                                    destructive = true,
                                    onClick = {
                                        pendingDeleteMessageId = actionMessage.id
                                        messageActions = null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }


    val deleteMessageId = pendingDeleteMessageId
    if (deleteMessageId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteMessageId = null },
            title = { Text("删除这段分支") },
            text = { Text("将删除这条消息及其后的所有消息，此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMessagesFrom(deleteMessageId)
                        pendingDeleteMessageId = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMessageId = null }) { Text("取消") }
            },
        )
    }
}

/** 会话抽屉：新建、切换、删除；每个会话消息独立保存。 */
@Composable
private fun SessionDrawer(
    sessions: List<com.mozhuan.data.model.ChatSession>,
    activeSessionId: String,
    onNewSession: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val ordered = sessions.sortedByDescending { it.updatedAt }
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                text = "所有会话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${sessions.size} 个会话 · 内容相互独立",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            Surface(
                onClick = onNewSession,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "新建会话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(ordered, key = { it.id }) { session ->
                    val active = session.id == activeSessionId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(session.id) }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp),
                        ) {
                            Text(
                                text = session.displayTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${formatSessionTime(session.updatedAt)} · ${session.messages.size} 条消息",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        IconButton(
                            onClick = { onDelete(session.id) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除会话",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            TextButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("收起")
            }
        }
    }
}

private fun formatSessionTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        .format(Date(now)) == SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        .format(Date(timestamp))
    val pattern = if (sameDay) "HH:mm" else "MM-dd HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScrollToButton(
    up: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (up) Icons.Filled.KeyboardArrowUp
                else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (up) "回到顶部" else "回到底部",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 让长按菜单贴着气泡弹出：默认在锚点下方，空间不足时翻到上方并夹在窗口内。 */
private class BubblePopupPositionProvider(
    private val anchor: IntOffset,
    private val alignEnd: Boolean,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = 8
        var x = if (alignEnd) anchor.x - popupContentSize.width else anchor.x
        var y = anchor.y + 14
        if (y + popupContentSize.height > windowSize.height - margin) {
            y = anchor.y - popupContentSize.height - 14
        }
        y = y.coerceAtLeast(margin)
        x = x.coerceIn(
            margin,
            (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin),
        )
        return IntOffset(x, y)
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    showTimestamp: Boolean,
    canRegenerate: Boolean,
    onCopy: () -> Unit,
    onAddToInput: () -> Unit,
    onRegenerate: () -> Unit,
    onShowActions: (IntOffset) -> Unit,
) {
    val isUser = message.role == ChatRole.USER
    var reasoningExpanded by remember(message.id) { mutableStateOf(false) }
    // 气泡在窗口中的位置，用于把长按菜单贴着气泡弹出。
    var bubbleTopLeft by remember(message.id) { mutableStateOf(IntOffset.Zero) }
    var moreAnchor by remember(message.id) { mutableStateOf(IntOffset.Zero) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (isUser) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInWindow()
                        bubbleTopLeft = IntOffset(position.x.toInt(), position.y.toInt())
                    }
                    .pointerInput(message.id) {
                        detectTapGestures(onLongPress = { offset ->
                            onShowActions(
                                IntOffset(
                                    bubbleTopLeft.x + offset.x.toInt(),
                                    bubbleTopLeft.y + offset.y.toInt(),
                                ),
                            )
                        })
                    },
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
                // 用原生 TextView 的可选文本模式：长按即可选中、复制（带系统选择工具条）。
                MarkdownText(
                    markdown = message.content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
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
                TextButton(
                    onClick = onAddToInput,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("添加到输入框", style = MaterialTheme.typography.labelSmall)
                }
                if (canRegenerate) {
                    TextButton(
                        onClick = onRegenerate,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("重新生成", style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(
                    onClick = { onShowActions(moreAnchor) },
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInWindow()
                        moreAnchor = IntOffset(
                            position.x.toInt(),
                            (position.y + coordinates.size.height).toInt(),
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("···更多", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val markwon = remember(context) { Markwon.builder(context).build() }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val fontSize = MaterialTheme.typography.bodyMedium.fontSize.value
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextIsSelectable(true)
                includeFontPadding = false
                setLineSpacing(0f, 1.12f)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
            markwon.setMarkdown(textView, markdown)
        },
    )
}

@Composable
private fun MessageActionPopupItem(
    text: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.bodyMedium,
            color = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun copyToClipboard(context: Context, text: String) {
    if (text.isEmpty()) return
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("墨撰", text))
}
