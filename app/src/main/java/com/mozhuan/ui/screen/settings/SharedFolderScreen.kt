package com.mozhuan.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.system.file.TextFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class ImportTarget { SKILL, PERSONA }

private data class TextDocument(
    val title: String,
    val fileName: String,
    val content: String,
)

private val ReadWriteGreen = Color(0xFF34C759)
private val ReadOnlyOrange = Color(0xFFFF9500)

/**
 * 共享文件夹：书稿 / 技能 / 人格三个目录，卡片式列表，
 * 顶部说明卡片 + 行内读写徽标，风格与 OpenMinis 一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedFolderScreen(
    viewModel: SharedFolderViewModel,
    onBack: () -> Unit,
) {
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val persona by viewModel.persona.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var importTarget by remember { mutableStateOf(ImportTarget.SKILL) }
    var preview by remember { mutableStateOf<TextDocument?>(null) }
    var pendingExport by remember { mutableStateOf<TextDocument?>(null) }
    var showBooks by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshSkills() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val name = TextFiles.withoutExtension(TextFiles.displayName(context, uri))
                val content = TextFiles.read(context, uri)
                when (importTarget) {
                    ImportTarget.SKILL -> viewModel.importSkill(name, content).name
                    ImportTarget.PERSONA -> {
                        viewModel.importPersona(content)
                        "SOUL.md"
                    }
                }
            }.onSuccess { name ->
                val message = if (importTarget == ImportTarget.SKILL) {
                    "已导入技能「$name」（默认停用）"
                } else {
                    "已导入并替换 SOUL.md"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val document = pendingExport
        if (uri != null && document != null) {
            runCatching { TextFiles.write(context, uri, document.content) }
                .onSuccess { Toast.makeText(context, "文件已导出", Toast.LENGTH_SHORT).show() }
                .onFailure {
                    Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
        pendingExport = null
    }

    val openDocumentPicker = {
        importLauncher.launch(arrayOf("text/plain", "text/markdown", "application/octet-stream"))
    }

    if (showBooks) {
        BookBrowser(
            root = File(context.filesDir, "book"),
            onExit = { showBooks = false },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("共享文件夹", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "这些目录用于存放书稿、技能、人格文件，支持导入导出，可自行管理。",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FolderCard(
                icon = Icons.Filled.Create,
                iconColor = MaterialTheme.colorScheme.primary,
                title = "书稿",
                writable = true,
                path = "/var/minis/book",
                onClick = { showBooks = true },
            )

            FolderCard(
                icon = Icons.Filled.Star,
                iconColor = Color(0xFFAF52DE),
                title = "技能",
                writable = false,
                path = "/var/minis/skills",
            ) {
                if (skills.isEmpty()) {
                    Text(
                        text = "目录为空，可从手机导入 TXT / MD 文本。",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                skills.forEachIndexed { index, skill ->
                    if (index > 0) SettingsDivider()
                    FileRow(
                        title = "${skill.name}.md",
                        subtitle = if (skill.enabled) "已启用 · 点击预览" else "未启用 · 点击预览",
                        onClick = {
                            preview = TextDocument(
                                title = skill.name,
                                fileName = "${TextFiles.safeFileName(skill.name)}.md",
                                content = viewModel.exportSkill(skill),
                            )
                        },
                    )
                }
                if (skills.isNotEmpty()) SettingsDivider()
                ActionRow(
                    title = "从手机导入技能",
                    onClick = {
                        importTarget = ImportTarget.SKILL
                        openDocumentPicker()
                    },
                )
            }

            FolderCard(
                icon = Icons.Filled.Person,
                iconColor = Color(0xFFFF2D55),
                title = "人格",
                writable = false,
                path = "/var/minis/soul",
            ) {
                FileRow(
                    title = "SOUL.md",
                    subtitle = "全局人格 Prompt · 点击预览",
                    onClick = {
                        preview = TextDocument(
                            title = "SOUL.md",
                            fileName = "SOUL.md",
                            content = persona,
                        )
                    },
                )
                SettingsDivider()
                ActionRow(
                    title = "从手机导入人格",
                    onClick = {
                        importTarget = ImportTarget.PERSONA
                        openDocumentPicker()
                    },
                )
            }
        }
    }

    preview?.let { document ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(document.title) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = document.content,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingExport = document
                        exportLauncher.launch(document.fileName)
                        preview = null
                    },
                ) {
                    Text("导出到手机")
                }
            },
            dismissButton = {
                TextButton(onClick = { preview = null }) { Text("关闭") }
            },
        )
    }
}

/** 单个目录卡片：图标 + 名称 + 读写徽标 + 路径，可选展开内容。 */
@Composable
private fun FolderCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    writable: Boolean,
    path: String,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    AccessBadge(writable = writable)
                }
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        content()
    }
}

/** 卡内普通文本行。 */
@Composable
private fun FileRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 卡内操作行（导入等）。 */
@Composable
private fun ActionRow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 读写 / 只读徽标。 */
@Composable
private fun AccessBadge(writable: Boolean) {
    val color = if (writable) ReadWriteGreen else ReadOnlyOrange
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Text(
            text = if (writable) "读写" else "只读",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private val BOOK_TEXT_EXTENSIONS = setOf("txt", "md", "markdown")

/**
 * 书稿目录浏览器：浏览内置 book 目录，进入子文件夹、预览/编辑文本、从手机导入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookBrowser(
    root: File,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf<List<String>>(emptyList()) }
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    var editing by remember { mutableStateOf<File?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    val dir = remember(path) { path.fold(root) { acc, name -> File(acc, name) } }

    LaunchedEffect(dir, reloadKey) {
        entries = withContext(Dispatchers.IO) {
            (dir.listFiles() ?: emptyArray())
                .filter { !it.name.startsWith(".") }
                .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val rawName = TextFiles.displayName(context, uri).ifBlank { "导入.txt" }
                    val name = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?.let { bytes ->
                            val utf8 = String(bytes, Charsets.UTF_8)
                            if ('\uFFFD' !in utf8) utf8
                            else runCatching { String(bytes, charset("GBK")) }.getOrDefault(utf8)
                        } ?: error("无法读取所选文件")
                    var target = File(dir, name)
                    var i = 2
                    while (target.exists()) {
                        val base = name.substringBeforeLast('.', name)
                        val ext = name.substringAfterLast('.', "").let { if (it == name) "" else ".$it" }
                        target = File(dir, "$base ($i)$ext")
                        i++
                    }
                    target.writeText(text)
                    target.name
                }
            }
            result
                .onSuccess {
                    reloadKey++
                    Toast.makeText(context, "已导入 $it", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    val goBack = {
        if (path.isNotEmpty()) path = path.dropLast(1) else onExit()
    }
    BackHandler { goBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = path.lastOrNull() ?: "书稿",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "/var/minis/book" + if (path.isEmpty()) "" else "/" + path.joinToString("/"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                TextButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("text/plain", "text/markdown", "application/octet-stream", "*/*"),
                        )
                    },
                ) { Text("导入") }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = "此目录为空，可从手机导入 TXT / MD 文本。",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(entries) { file ->
                val isText = file.isFile && file.extension.lowercase() in BOOK_TEXT_EXTENSIONS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = file.isDirectory || isText) {
                            if (file.isDirectory) path = path + file.name
                            else if (isText) editing = file
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (file.isDirectory) "文件夹" else formatFileSize(file.length()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (file.isDirectory || isText) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                SettingsDivider()
            }
        }
    }

    editing?.let { file ->
        BookTextEditorDialog(
            file = file,
            onDismiss = { editing = null },
            onSaved = { reloadKey++ },
        )
    }
}

@Composable
private fun BookTextEditorDialog(
    file: File,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    var content by remember(file) {
        mutableStateOf(runCatching { file.readText() }.getOrDefault(""))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name) },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching { file.writeText(content) }
                        .onSuccess {
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            onSaved()
                            onDismiss()
                        }
                        .onFailure {
                            Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
                        }
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024f / 1024f)
}
