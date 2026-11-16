package com.mozhuan.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.MountedFolder

/** 挂载外部文件夹：添加 SAF 目录授权，可浏览一级或多级目录并预览文本文件。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MountedFoldersScreen(
    viewModel: MountedFoldersViewModel,
    onBack: () -> Unit,
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var browseRoot by remember { mutableStateOf<DocumentFile?>(null) }
    var path by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var preview by remember { mutableStateOf<Pair<String, String>?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.add(uri)
            Toast.makeText(context, "已挂载文件夹", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("挂载外部文件夹") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { picker.launch(null) }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加文件夹")
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
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "挂载手机目录后可在这里浏览并读取其中的文本文件。目录授权由系统持久保存，卸载应用前一直有效。",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsGroup(
                title = "已挂载目录",
                footer = "点击目录可浏览内容；支持 TXT / MD 等文本文件预览。",
            ) {
                if (folders.isEmpty()) {
                    Text(
                        text = "还没有挂载目录，点击右上角 + 选择手机中的文件夹。",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    folders.forEachIndexed { index, folder ->
                        if (index > 0) SettingsDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val root = DocumentFile.fromTreeUri(
                                        context,
                                        android.net.Uri.parse(folder.uri),
                                    )
                                    if (root != null) {
                                        browseRoot = root
                                        path = listOf(root)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "目录授权已失效，请重新挂载",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF34C759)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White,
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 14.dp),
                            ) {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = folder.uri,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { viewModel.remove(folder) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "移除挂载",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    browseRoot?.let { root ->
        val current = path.lastOrNull() ?: root
        val children = remember(current.uri) { viewModel.childrenOf(current) }
        AlertDialog(
            onDismissRequest = {
                browseRoot = null
                path = emptyList()
            },
            title = { Text(current.name ?: "外部文件夹") },
            text = {
                Column {
                    if (path.size > 1) {
                        TextButton(
                            onClick = { path = path.dropLast(1) },
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) {
                            Text("返回上级")
                        }
                    }
                    if (children.isEmpty()) {
                        Text(
                            text = "目录为空",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(children, key = { it.uri.toString() }) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (file.isDirectory) {
                                                path = path + file
                                            } else {
                                                viewModel.readText(file)
                                                    .onSuccess { content ->
                                                        preview = file.name.orEmpty() to content
                                                    }
                                                    .onFailure {
                                                        Toast.makeText(
                                                            context,
                                                            "无法读取：${it.message}",
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name.orEmpty(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (file.isDirectory) {
                                                "文件夹"
                                            } else {
                                                formatSize(file.length())
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        browseRoot = null
                        path = emptyList()
                    },
                ) {
                    Text("关闭")
                }
            },
        )
    }

    preview?.let { (name, content) ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(name) },
            text = {
                Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall
                            .copy(fontFamily = FontFamily.Monospace),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { preview = null }) { Text("关闭") }
            },
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}
