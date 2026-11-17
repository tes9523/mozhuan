package com.mozhuan.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.Skill
import com.mozhuan.data.model.SkillFile
import com.mozhuan.system.file.TextFiles

/** 技能管理：文件化 SKILL.md，支持开关、查看、编辑、导入与导出。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillScreen(
    viewModel: SkillViewModel,
    onBack: () -> Unit,
) {
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var creating by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<Skill?>(null) }
    var viewingSkill by remember { mutableStateOf<Skill?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val displayName = TextFiles.displayName(context, uri)
            val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
            if (displayName.endsWith(".zip", ignoreCase = true) || mimeType.contains("zip")) {
                val stream = runCatching { context.contentResolver.openInputStream(uri) }
                    .getOrNull()
                if (stream == null) {
                    Toast.makeText(context, "读取失败：无法打开所选文件", Toast.LENGTH_SHORT).show()
                } else {
                    stream.use { input ->
                        viewModel.importArchive(input)
                            .onSuccess { skill ->
                                Toast.makeText(
                                    context,
                                    "已导入技能「${skill.name}」（含附件），默认停用",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    "导入失败：${it.message}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                    }
                }
            } else {
                val fallbackName = TextFiles.withoutExtension(displayName)
                runCatching { TextFiles.read(context, uri) }
                    .onFailure {
                        Toast.makeText(context, "读取失败：${it.message}", Toast.LENGTH_SHORT).show()
                    }
                    .onSuccess { content ->
                        viewModel.importSkill(content, fallbackName)
                            .onSuccess { skill ->
                                Toast.makeText(
                                    context,
                                    "已导入技能「${skill.name}」，默认停用",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .onFailure {
                                Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
            }
        }
    }

    // 先处理内部层级，再交给 NavHost：编辑 → 详情 → 列表。
    val editorOpen = creating || editingSkill != null
    val detail = viewingSkill
    when {
        editorOpen -> {
            SkillEditorContent(
                skill = editingSkill,
                viewModel = viewModel,
                onClose = {
                    creating = false
                    editingSkill = null
                },
            )
        }

        detail != null -> {
            SkillDetailContent(
                skill = detail,
                viewModel = viewModel,
                onBack = { viewingSkill = null },
                onEdit = {
                    viewingSkill = null
                    editingSkill = detail
                },
            )
        }

        else -> Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("技能") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf(
                                    "text/markdown",
                                    "text/plain",
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                    "*/*",
                                ),
                            )
                        },
                    ) {
                        Text("导入")
                    }
                    IconButton(
                        onClick = {
                            editingSkill = null
                            creating = true
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "新增技能")
                    }
                },
            )

            Text(
                text = "每个技能是一个 SKILL.md；开启后仅注入名称、描述与路径，AI 按需读取正文和附件。",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (skills.isEmpty()) {
                    item {
                        Text(
                            text = "暂无技能。点击右上角 + 新建，或导入 SKILL.md 文件。",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item {
                        SettingsGroup(
                            footer = "内置 skill-creator 只可查看、启停和导出；其他技能点进详情后可编辑。",
                        ) {
                            skills.forEachIndexed { index, skill ->
                                SkillRow(
                                    skill = skill,
                                    onOpen = { viewingSkill = skill },
                                    onEnabledChange = { viewModel.setEnabled(skill.id, it) },
                                )
                                if (index != skills.lastIndex) SettingsDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillRow(
    skill: Skill,
    onOpen: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkillAvatar(size = 34.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = skill.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = skill.displayDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = skill.enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun SkillAvatar(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size / 2),
        )
    }
}

// ── 详情（图 1 样式） ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillDetailContent(
    skill: Skill,
    viewModel: SkillViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val markdown = remember(skill.id) { viewModel.readSkillMarkdown(skill.id) }
    val attachments = remember(skill.id) { viewModel.attachments(skill.id) }
    var enabled by remember(skill.id) { mutableStateOf(skill.enabled) }
    var previewPath by remember(skill.id) { mutableStateOf<String?>(null) }
    var showDelete by remember(skill.id) { mutableStateOf(false) }

    val exportMdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        if (uri != null) {
            runCatching { TextFiles.write(context, uri, viewModel.exportSkill(skill)) }
                .onSuccess { Toast.makeText(context, "技能已导出", Toast.LENGTH_SHORT).show() }
                .onFailure {
                    Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = viewModel.exportArchive(skill) ?: error("技能附件已不存在")
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("无法写入所选位置")
            }
                .onSuccess { Toast.makeText(context, "技能与附件已导出", Toast.LENGTH_SHORT).show() }
                .onFailure {
                    Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("查看技能") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (!skill.isBuiltIn) {
                    TextButton(onClick = onEdit) { Text("编辑") }
                }
                TextButton(
                    onClick = {
                        val safe = TextFiles.safeFileName(skill.name)
                        if (attachments.isEmpty()) exportMdLauncher.launch("$safe.md")
                        else exportZipLauncher.launch("$safe.zip")
                    },
                ) { Text("导出") }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (skill.isBuiltIn) {
                Text(
                    text = "skill-creator 由应用内置，内容与文件会在扫描时自动恢复，不可修改或删除。",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    SkillAvatar(size = 48.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(
                            text = skill.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = skill.displayDescription,
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!skill.isBuiltIn) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "编辑技能",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用技能", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = viewModel.skillPath(skill.id),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        viewModel.setEnabled(skill.id, it)
                    },
                )
            }

            Text(
                text = "Skill 内容",
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Text(
                    text = markdown.ifBlank { "（SKILL.md 为空）" },
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (attachments.isNotEmpty()) {
                Text(
                    text = "引用文件（${attachments.size}）" +
                        if (attachments.any { !it.editable }) " · assets 为输出资源" else "",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(attachments, key = { it.path }) { file ->
                        AssistChip(
                            onClick = { previewPath = file.path },
                            label = {
                                Text(
                                    text = file.path,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            if (!skill.isBuiltIn) {
                TextButton(
                    onClick = { showDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                ) {
                    Text("删除技能", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    val preview = previewPath
    if (preview != null) {
        val content = remember(skill.id, preview) {
            viewModel.readAttachment(skill.id, preview)
        }
        AlertDialog(
            onDismissRequest = { previewPath = null },
            title = { Text(preview) },
            text = {
                Text(
                    text = content ?: "无法预览该文件（二进制文件或超过大小限制）",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { previewPath = null }) { Text("关闭") }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除技能") },
            text = { Text("将删除「${skill.name}」所在的 SKILL.md 文件与附件，无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        viewModel.deleteSkill(skill.id)
                        onBack()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            },
        )
    }
}

// ── 编辑（图 4 样式：全屏 + Tab + 引用文件可编辑） ────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillEditorContent(
    skill: Skill?,
    viewModel: SkillViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val initialRaw = remember(skill?.id) {
        skill?.let { viewModel.readSkillMarkdown(it.id) }.orEmpty()
    }
    var raw by remember(skill?.id) { mutableStateOf(initialRaw) }
    var enabled by remember(skill?.id) { mutableStateOf(skill?.enabled ?: true) }
    var tab by remember(skill?.id) { mutableStateOf(0) }
    var selectedPath by remember(skill?.id) { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }

    val attachments = remember(skill?.id) {
        skill?.let { viewModel.attachments(it.id) }.orEmpty()
    }
    val drafts = remember(skill?.id) { mutableStateMapOf<String, String>() }
    val originals = remember(skill?.id) { mutableStateMapOf<String, String>() }

    val currentName = remember(raw) { frontmatterName(raw) }
        .ifBlank { skill?.name ?: "新建技能" }
    val dirty = raw != initialRaw ||
        drafts.any { (path, text) -> originals[path] != text } ||
        (skill != null && enabled != skill.enabled)

    fun openAttachment(file: SkillFile) {
        if (!drafts.containsKey(file.path)) {
            val text = skill?.let { viewModel.readAttachment(it.id, file.path) }.orEmpty()
            originals[file.path] = text
            drafts[file.path] = text
        }
        selectedPath = file.path
    }

    fun save() {
        if (raw.isBlank()) {
            Toast.makeText(context, "SKILL.md 内容不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        val result = runCatching { viewModel.saveDocument(skill?.id, raw, enabled) }
        val saved = result.getOrElse { error ->
            Toast.makeText(context, "保存失败：${error.message}", Toast.LENGTH_SHORT).show()
            return
        }
        drafts.forEach { (path, text) ->
            if (originals[path] != text) {
                viewModel.saveAttachment(saved.id, path, text)
            }
        }
        Toast.makeText(context, "技能已保存", Toast.LENGTH_SHORT).show()
        onClose()
    }

    fun requestClose() {
        if (dirty) showDiscard = true else onClose()
    }

    BackHandler(onBack = ::requestClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkillAvatar(size = 32.dp)
                    Text(
                        text = currentName,
                        modifier = Modifier.padding(start = 10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = ::requestClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                TextButton(
                    onClick = ::save,
                    enabled = raw.isNotBlank(),
                ) { Text("保存") }
                TextButton(onClick = ::requestClose) { Text("取消") }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EditorTab(
                text = "正文 SKILL.md",
                selected = tab == 0,
                modifier = Modifier.weight(1f),
                onClick = { tab = 0 },
            )
            EditorTab(
                text = "引用文件（${attachments.size}）",
                selected = tab == 1,
                modifier = Modifier.weight(1f),
                onClick = { tab = 1 },
            )
        }

        if (tab == 0) {
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                placeholder = {
                    Text(
                        "---\nname: 技能名称\ndescription: 一句话描述\n---\n\n# 技能正文\n…",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
            )
        } else {
            val path = selectedPath
            if (path != null) {
                val file = attachments.firstOrNull { it.path == path }
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { selectedPath = null }) { Text("← 文件列表") }
                        Text(
                            text = path,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedTextField(
                        value = drafts[path].orEmpty(),
                        onValueChange = { drafts[path] = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        readOnly = file?.editable == false,
                        placeholder = { Text("空文件") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            } else if (attachments.isEmpty()) {
                Text(
                    text = "该技能没有引用文件。",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(attachments, key = { it.path }) { file ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openAttachment(file) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = file.path,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!file.editable) {
                                    Text(
                                        text = "只读",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("保存修改？") },
            text = { Text("你有未保存的修改，离开前要先保存吗？") },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; save() }) {
                    Text("保存并退出")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDiscard = false }) {
                        Text("继续编辑")
                    }
                    TextButton(onClick = { showDiscard = false; onClose() }) {
                        Text("不保存")
                    }
                }
            },
        )
    }
}

@Composable
private fun EditorTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** 从 SKILL.md frontmatter 里读取 name，用于编辑时实时显示标题。 */
private fun frontmatterName(raw: String): String {
    val lines = raw.removePrefix("\uFEFF").lines()
    if (lines.firstOrNull()?.trim() != "---") return ""
    val header = lines.drop(1).takeWhile { it.trim() != "---" }
    val line = header.firstOrNull { it.trimStart().startsWith("name:") } ?: return ""
    return line.substringAfter("name:").trim().trim('"', '\'')
}
