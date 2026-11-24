package com.mozhuan.ui.screen.books

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.R
import com.mozhuan.data.model.Novel
import com.mozhuan.data.model.NovelEntry
import com.mozhuan.data.model.TrashedNovel
import com.mozhuan.data.repository.BookDeleteResult
import com.mozhuan.system.file.TextFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 书稿：小说列表 → 作品目录（小说资料 / 章节内容）→ 子目录 → 正文编辑。
 * 删除先进回收站，回收站二次删除才彻底清除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    viewModel: BooksViewModel,
    onBack: () -> Unit,
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var openBookId by remember { mutableStateOf<String?>(null) }
    var path by remember { mutableStateOf<List<String>>(emptyList()) }
    var openFilePath by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var inTrashView by remember { mutableStateOf(false) }
    var pendingDeleteBookId by remember { mutableStateOf<String?>(null) }
    var pendingPurgeTrashId by remember { mutableStateOf<String?>(null) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }
    var coverDialogForBook by remember { mutableStateOf<String?>(null) }

    val openBook = books.firstOrNull { it.id == openBookId }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(openBookId, books) {
        if (openBookId != null && openBook == null) {
            openBookId = null
            path = emptyList()
            openFilePath = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = TextFiles.displayName(context, uri)
        Toast.makeText(context, "正在导入…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) {
                Toast.makeText(context, "读取失败：无法打开所选文件", Toast.LENGTH_SHORT).show()
                return@launch
            }
            viewModel.importBook(fileName, bytes) { result ->
                result.onSuccess { novel ->
                    Toast.makeText(
                        context,
                        "已导入《${novel.title}》，共 ${novel.chapterCount} 章",
                        Toast.LENGTH_SHORT,
                    ).show()
                    openBookId = novel.id
                    path = emptyList()
                }.onFailure {
                    Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val coverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val bookId = coverDialogForBook
        coverDialogForBook = null
        if (uri == null || bookId == null) return@rememberLauncherForActivityResult
        scope.launch {
            // 解码、压缩与落盘放到 IO 线程，避免大图在主线程 OOM / 卡死。
            val result = withContext(Dispatchers.IO) { viewModel.setCoverFromGallery(bookId, uri) }
            result
                .onSuccess { Toast.makeText(context, "封面已更新", Toast.LENGTH_SHORT).show() }
                .onFailure {
                    Toast.makeText(context, "封面更新失败：${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    // 内部逐级返回。
    BackHandler(enabled = openFilePath == null && (path.isNotEmpty() || openBookId != null || inTrashView)) {
        when {
            openFilePath != null -> {} // 由编辑器自己处理
            path.isNotEmpty() -> path = path.dropLast(1)
            openBookId != null -> { openBookId = null; path = emptyList() }
            inTrashView -> inTrashView = false
        }
    }

    val filePath = openFilePath
    when {
        openBook != null && filePath != null -> BookFileEditor(
            book = openBook,
            relativePath = filePath,
            viewModel = viewModel,
            onClose = { openFilePath = null },
        )
        openBook != null -> BookFolderContent(
            book = openBook,
            path = path,
            viewModel = viewModel,
            onChangeCover = { coverDialogForBook = openBook.id },
            onBack = {
                when {
                    path.isNotEmpty() -> path = path.dropLast(1)
                    else -> { openBookId = null; path = emptyList() }
                }
            },
            onOpenFolder = { path = path + it },
            onOpenFile = { openFilePath = it },
        )
        inTrashView -> TrashContent(
            trash = trash,
            onRestore = { viewModel.restoreFromTrash(it) },
            onDelete = { pendingPurgeTrashId = it },
            onEmptyAll = { showEmptyTrashConfirm = true },
            onBack = { inTrashView = false },
        )
        else -> BookListContent(
            books = books,
            trashCount = trash.size,
            onOpen = { openBookId = it.id; path = emptyList() },
            onCreate = { showCreate = true },
            onImport = {
                importLauncher.launch(
                    arrayOf("text/plain", "text/markdown", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream", "*/*"),
                )
            },
            onChangeCover = { coverDialogForBook = it.id },
            onDelete = { pendingDeleteBookId = it.id },
            onOpenTrash = { inTrashView = true },
            onBack = onBack,
        )
    }

    if (showCreate) CreateBookDialog(
        onDismiss = { showCreate = false },
        onCreate = { title, cover ->
            val novel = viewModel.createBook(title, cover)
            showCreate = false
            openBookId = novel.id; path = emptyList()
            Toast.makeText(context, "已创建《${novel.title}》", Toast.LENGTH_SHORT).show()
        },
    )

    coverDialogForBook?.let { bookId ->
        val book = books.firstOrNull { it.id == bookId } ?: run { coverDialogForBook = null; return@let }
        ChangeCoverDialog(
            book = book,
            viewModel = viewModel,
            onPickFromGallery = { coverLauncher.launch("image/*") },
            onPickBuiltIn = { index -> viewModel.setBuiltInCover(bookId, index); coverDialogForBook = null },
            onDismiss = { coverDialogForBook = null },
        )
    }

    pendingDeleteBookId?.let { id ->
        val book = books.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { pendingDeleteBookId = null },
            title = { Text("删除《${book?.title.orEmpty()}》？") },
            text = {
                Text(
                    "仅导入正文、尚未生成「小说资料」的作品会直接删除；" +
                        "已经生成小说资料（AI 分析过）的作品会移入回收站，30 天内可恢复。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteBookId = null
                        val message = when (viewModel.deleteBook(id)) {
                            BookDeleteResult.DELETED -> "已删除"
                            BookDeleteResult.TRASHED -> "已移入回收站"
                            BookDeleteResult.FAILED -> "删除失败"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteBookId = null }) { Text("取消") } },
        )
    }

    pendingPurgeTrashId?.let { id ->
        val item = trash.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { pendingPurgeTrashId = null },
            title = { Text("彻底删除《${item?.title.orEmpty()}》？") },
            text = { Text("该作品文件将被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = { viewModel.purgeTrash(id); pendingPurgeTrashId = null }) { Text("彻底删除") }
            },
            dismissButton = { TextButton(onClick = { pendingPurgeTrashId = null }) { Text("取消") } },
        )
    }

    if (showEmptyTrashConfirm) AlertDialog(
        onDismissRequest = { showEmptyTrashConfirm = false },
        title = { Text("清空回收站？") },
        text = { Text("回收站中所有作品将被彻底删除，无法恢复。") },
        confirmButton = {
            TextButton(onClick = { viewModel.purgeAllTrash(); showEmptyTrashConfirm = false }) { Text("全部彻底删除") }
        },
        dismissButton = { TextButton(onClick = { showEmptyTrashConfirm = false }) { Text("取消") } },
    )
}

// ── 小说列表（图 2） ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookListContent(
    books: List<Novel>,
    trashCount: Int,
    onOpen: (Novel) -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onChangeCover: (Novel) -> Unit,
    onDelete: (Novel) -> Unit,
    onOpenTrash: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("我的小说", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "共 ${books.size} 本作品${if (trashCount > 0) " · 回收站 $trashCount" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (trashCount > 0) IconButton(onClick = onOpenTrash) {
                    Icon(Icons.Filled.Delete, contentDescription = "回收站")
                }
                TextButton(onClick = onImport) { Text("导入") }
                IconButton(onClick = onCreate) {
                    Icon(Icons.Filled.Add, contentDescription = "创建小说")
                }
            },
        )

        if (books.isEmpty()) {
            EmptyBooks(contentModifier = Modifier.weight(1f), onCreate = onCreate)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(books, key = { it.id }) { novel ->
                    BookRow(
                        novel = novel,
                        onClick = { onOpen(novel) },
                        onChangeCover = { onChangeCover(novel) },
                        onDelete = { onDelete(novel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBooks(
    contentModifier: Modifier = Modifier,
    onCreate: () -> Unit,
) {
    Box(modifier = contentModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_book),
                contentDescription = null,
                modifier = Modifier.size(76.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "还没有小说项目",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点右上角「+」创建小说，或点「导入」导入 TXT / DOCX / MD",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(22.dp))
            Button(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("创建小说")
            }
        }
    }
}

@Composable
private fun BookRow(
    novel: Novel,
    onClick: () -> Unit,
    onChangeCover: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                coverIndex = novel.coverIndex,
                customCoverPath = novel.customCover,
                title = novel.title,
                modifier = Modifier.width(52.dp).height(72.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${novel.chapterCount} 章 · ${formatTime(novel.updatedAt)}",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onChangeCover) {
                Icon(
                    painter = painterResource(R.drawable.ic_file),
                    contentDescription = "更换封面",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── 作品目录 ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookFolderContent(
    book: Novel,
    path: List<String>,
    viewModel: BooksViewModel,
    onChangeCover: () -> Unit,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val relativePath = path.joinToString("/")
    var entries by remember(book.id, relativePath) { mutableStateOf<List<NovelEntry>>(emptyList()) }
    var loading by remember(book.id, relativePath) { mutableStateOf(true) }
    val title = path.lastOrNull() ?: book.title

    LaunchedEffect(book.id, relativePath) {
        loading = true
        entries = withContext(Dispatchers.IO) { viewModel.entries(book.id, relativePath) }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (path.isNotEmpty()) Text(
                        text = path.joinToString(" / "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (path.isEmpty()) TextButton(onClick = onChangeCover) { Text("更换封面") }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (path.isEmpty()) item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookCover(
                        coverIndex = book.coverIndex,
                        customCoverPath = book.customCover,
                        title = book.title,
                        modifier = Modifier.width(64.dp).height(88.dp),
                    )
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "章节内容放正文；小说资料放设定、大纲与章节摘要。",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            when {
                loading -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                entries.isEmpty() -> item {
                    Text(
                        text = "此目录为空。",
                        modifier = Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> items(entries, key = { it.path }) { entry ->
                    EntryRow(entry = entry) {
                        if (entry.isDirectory) onOpenFolder(entry.name) else onOpenFile(entry.path)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: NovelEntry, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(if (entry.isDirectory) R.drawable.ic_folder else R.drawable.ic_file),
                contentDescription = null,
                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!entry.isDirectory) Text(
                    text = buildString {
                        append(formatSize(entry.size))
                        if (entry.wordCount > 0) append(" · ${formatWordCount(entry.wordCount)}")
                        append(" · ").append(formatTime(entry.updatedAt))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── 正文编辑 ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookFileEditor(
    book: Novel,
    relativePath: String,
    viewModel: BooksViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var loaded by remember(book.id, relativePath) { mutableStateOf(false) }
    var original by remember(book.id, relativePath) { mutableStateOf<String?>(null) }
    var content by remember(book.id, relativePath) { mutableStateOf("") }
    var editing by remember(book.id, relativePath) { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, relativePath) {
        val text = withContext(Dispatchers.IO) { viewModel.readFile(book.id, relativePath) }
        original = text
        content = text.orEmpty()
        loaded = true
    }

    // 只有编辑模式下的改动才算未保存。
    val dirty = editing && content != original.orEmpty()

    fun save(exitAfter: Boolean) {
        if (viewModel.writeFile(book.id, relativePath, content)) {
            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            original = content
            editing = false
            if (exitAfter) onClose()
        } else {
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
        }
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
                Text(
                    text = relativePath.substringAfterLast('/'),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = ::requestClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                when {
                    !loaded -> Unit
                    original == null -> Text("无法读取", color = MaterialTheme.colorScheme.error)
                    editing -> TextButton(
                        onClick = { save(exitAfter = false) },
                        enabled = dirty,
                    ) { Text("保存") }
                    else -> TextButton(onClick = { editing = true }) { Text("编辑") }
                }
            },
        )
        when {
            !loaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            original == null -> Text(
                text = "文件过大或编码无法识别，暂不支持在此编辑。",
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            editing -> OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            else -> SelectionContainer {
                Text(
                    text = original.orEmpty(),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("保存修改？") },
            text = { Text("这个文件有未保存的修改，离开前要先保存吗？") },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; save(exitAfter = true) }) { Text("保存并退出") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDiscard = false }) { Text("继续编辑") }
                    TextButton(onClick = { showDiscard = false; onClose() }) { Text("不保存") }
                }
            },
        )
    }
}

// ── 新建作品弹窗（图 3） ────────────────────────────────────────

@Composable
private fun CreateBookDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, coverIndex: Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var coverIndex by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_file),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "新建作品",
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BookCover(
                        coverIndex = coverIndex,
                        title = title.ifBlank { "未命名作品" },
                        modifier = Modifier
                            .width(108.dp)
                            .height(148.dp),
                    )
                }
                TextButton(
                    onClick = { coverIndex = (coverIndex + 1) % COVER_GRADIENTS.size },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("点击更换封面")
                }

                Text(
                    text = "作品名称",
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 60) title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("给这本书起个名字") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = "${title.length}/60",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                )
                Text(
                    text = "创建后会在作品目录里生成章节内容 / 小说资料文件夹",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "作品封皮",
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(COVER_GRADIENTS.indices.toList()) { index ->
                        Surface(
                            modifier = Modifier
                                .width(54.dp)
                                .height(74.dp)
                                .clickable { coverIndex = index },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                if (index == coverIndex) 2.dp else 1.dp,
                                if (index == coverIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                            color = Color.Transparent,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.linearGradient(COVER_GRADIENTS[index])),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "书",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = { onCreate(title.trim(), coverIndex) },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("创建") }
                }
            }
        }
    }
}

// ── 共用 ───────────────────────────────────────────────────────

@Composable
private fun BookCover(
    coverIndex: Int,
    customCoverPath: String? = null,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = remember(customCoverPath) {
        customCoverPath?.let { rel ->
            runCatching {
                val file = java.io.File(context.filesDir, "book/$rel")
                if (!file.isFile) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                // 历史版本可能存过超大原图，按最长边约 1024px 采样，避免解码 OOM 闪退。
                var sample = 1
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                while (longest > 0 && longest / (sample * 2) >= 1024) sample *= 2
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }.getOrNull()
        }
    }
    when {
        bitmap != null -> Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        else -> {
            val colors = COVER_GRADIENTS[coverIndex.coerceIn(0, COVER_GRADIENTS.lastIndex)]
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(colors))
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashContent(
    trash: List<TrashedNovel>,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEmptyAll: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("回收站") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
            actions = {
                if (trash.isNotEmpty()) TextButton(onClick = onEmptyAll) { Text("清空") }
            },
        )
        if (trash.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("回收站为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(trash, key = { it.id }) { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = buildString {
                                    append(formatSize(item.totalBytes))
                                    append(" · 删除于 ").append(formatTime(item.trashedAt))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onRestore(item.id) }) { Text("恢复") }
                        IconButton(onClick = { onDelete(item.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "彻底删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeCoverDialog(
    book: Novel,
    viewModel: BooksViewModel,
    onPickFromGallery: () -> Unit,
    onPickBuiltIn: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(book.coverIndex) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(20.dp),
            ) {
                Text(
                    text = "更换封面",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "相册图片",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onPickFromGallery,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从相册选择图片") }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "内置封面",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    COVER_GRADIENTS.indices.forEach { index ->
                        Surface(
                            modifier = Modifier
                                .width(60.dp)
                                .height(82.dp)
                                .clickable { selected = index },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                if (index == selected) 2.dp else 1.dp,
                                if (index == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Brush.linearGradient(COVER_GRADIENTS[index])),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("书", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = { onPickBuiltIn(selected) }) { Text("应用内置封面") }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024f / 1024f)
}

private fun formatWordCount(count: Int): String = when {
    count >= 10_000 -> String.format(Locale.getDefault(), "%.1f 字", count / 10_000f)
    else -> "$count 字"
}

private val COVER_GRADIENTS = listOf(
    listOf(Color(0xFF3B1F6E), Color(0xFF8E6BC8)),
    listOf(Color(0xFF0F3D3E), Color(0xFF2D8A80)),
    listOf(Color(0xFF6B1D0F), Color(0xFFE07A5F)),
    listOf(Color(0xFF16305E), Color(0xFF7EA8D8)),
    listOf(Color(0xFF0B1526), Color(0xFF2456A6)),
    listOf(Color(0xFF4A2A09), Color(0xFFD9A441)),
)
