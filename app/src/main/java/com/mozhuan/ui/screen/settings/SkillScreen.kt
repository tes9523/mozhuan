package com.mozhuan.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.Skill
import com.mozhuan.system.file.TextFiles

/** 静态提示词技能管理，不包含工具调用、MCP 或子代理。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillScreen(
    viewModel: SkillViewModel,
    onBack: () -> Unit,
) {
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editorVisible by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<Skill?>(null) }
    var pendingExport by remember { mutableStateOf<Skill?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val skill = pendingExport
        if (uri != null && skill != null) {
            runCatching { TextFiles.write(context, uri, viewModel.exportSkill(skill)) }
                .onSuccess { Toast.makeText(context, "技能已导出", Toast.LENGTH_SHORT).show() }
                .onFailure {
                    Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
        pendingExport = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching { TextFiles.read(context, uri) }
                .onFailure {
                    Toast.makeText(context, "读取失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
                .onSuccess { json ->
                    viewModel.importSkill(json)
                        .onSuccess {
                            Toast.makeText(context, "已导入技能「${it.name}」", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                            arrayOf("application/json", "text/plain", "application/octet-stream"),
                        )
                    },
                ) {
                    Text("导入")
                }
                IconButton(
                    onClick = {
                        editingSkill = null
                        editorVisible = true
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "新增技能")
                }
            },
        )

        Text(
            text = "开启的技能提示词会与人格合并发送；仅静态文本，不执行工具。",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (skills.isEmpty()) {
            Text(
                text = "暂无技能，点击右上角 + 新增，或导入单条 JSON 技能文件。",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                skills.forEach { skill ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = skill.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Switch(
                                    checked = skill.enabled,
                                    onCheckedChange = { viewModel.setEnabled(skill.id, it) },
                                )
                            }
                            Text(
                                text = skill.prompt,
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row {
                                TextButton(
                                    onClick = {
                                        pendingExport = skill
                                        exportLauncher.launch(
                                            "${TextFiles.safeFileName(skill.name)}.json",
                                        )
                                    },
                                ) {
                                    Text("导出")
                                }
                                TextButton(
                                    onClick = {
                                        editingSkill = skill
                                        editorVisible = true
                                    },
                                ) {
                                    Text("编辑")
                                }
                                TextButton(onClick = { viewModel.deleteSkill(skill.id) }) {
                                    Text("删除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorVisible) {
        SkillEditorDialog(
            skill = editingSkill,
            onDismiss = { editorVisible = false },
            onSave = { name, prompt, enabled ->
                val original = editingSkill
                viewModel.saveSkill(
                    Skill(
                        id = original?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        prompt = prompt.trim(),
                        enabled = enabled,
                    ),
                )
                editorVisible = false
                Toast.makeText(context, "技能已保存", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun SkillEditorDialog(
    skill: Skill?,
    onDismiss: () -> Unit,
    onSave: (name: String, prompt: String, enabled: Boolean) -> Unit,
) {
    var name by remember(skill?.id) { mutableStateOf(skill?.name.orEmpty()) }
    var prompt by remember(skill?.id) { mutableStateOf(skill?.prompt.orEmpty()) }
    var enabled by remember(skill?.id) { mutableStateOf(skill?.enabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (skill == null) "新增技能" else "编辑技能") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("技能名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    label = { Text("提示词模板") },
                    minLines = 6,
                    maxLines = 12,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "保存后启用",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, prompt, enabled) },
                enabled = name.isNotBlank() && prompt.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
