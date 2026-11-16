package com.mozhuan.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.system.file.TextFiles

private enum class ImportTarget { SKILL, PERSONA }

private data class TextDocument(
    val title: String,
    val fileName: String,
    val content: String,
)

/** 共享文件夹：技能与人格文本的预览、SAF 导入导出，不涉及挂载或 rootfs。 */
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("共享文件夹") },
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
            Text(
                text = "仅预览、导入和导出文本，不挂载外部目录。",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        importTarget = ImportTarget.SKILL
                        importLauncher.launch(arrayOf("text/plain", "text/markdown", "application/octet-stream"))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("导入到技能目录")
                }
                OutlinedButton(
                    onClick = {
                        importTarget = ImportTarget.PERSONA
                        importLauncher.launch(arrayOf("text/plain", "text/markdown", "application/octet-stream"))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("导入到人格目录")
                }
            }

            SettingsGroup(title = "技能目录（${skills.size}）") {
                if (skills.isEmpty()) {
                    Text(
                        text = "目录为空，可从手机导入 TXT / MD 文本。",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                skills.forEachIndexed { index, skill ->
                    if (index > 0) SettingsDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = "${skill.name}.md",
                        subtitle = if (skill.enabled) "已启用 · 点击预览" else "未启用 · 点击预览",
                        onClick = {
                            preview = TextDocument(
                                title = skill.name,
                                fileName = "${TextFiles.safeFileName(skill.name)}.md",
                                content = skill.prompt,
                            )
                        },
                    )
                }
            }

            SettingsGroup(title = "人格目录") {
                SettingsRow(
                    icon = Icons.Filled.Person,
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
