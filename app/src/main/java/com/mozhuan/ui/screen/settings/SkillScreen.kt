package com.mozhuan.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.Skill
import com.mozhuan.system.file.TextFiles

/** 技能管理：文件化 SKILL.md，支持开关、编辑、导入与导出。 */
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

    if (creating || editingSkill != null) {
        SkillEditorContent(
            skill = editingSkill,
            viewModel = viewModel,
            onClose = {
                creating = false
                editingSkill = null
            },
        )
        return
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
            text = "每个技能是一个 SKILL.md，存放在 /var/minis/skills；开启后会自动合并进系统提示词。",
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
                        footer = "点击技能可编辑名称、描述与正文；导入的技能默认停用。",
                    ) {
                        skills.forEachIndexed { index, skill ->
                            SkillRow(
                                skill = skill,
                                onOpen = { editingSkill = skill },
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
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = skill.name.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillEditorContent(
    skill: Skill?,
    viewModel: SkillViewModel,
    onClose: () -> Unit,
) {
    var name by remember(skill?.id) { mutableStateOf(skill?.name.orEmpty()) }
    var description by remember(skill?.id) { mutableStateOf(skill?.description.orEmpty()) }
    var body by remember(skill?.id) { mutableStateOf(skill?.body.orEmpty()) }
    var enabled by remember(skill?.id) { mutableStateOf(skill?.enabled ?: true) }
    var showDelete by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        val current = skill ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching { TextFiles.write(context, uri, viewModel.exportSkill(current)) }
                .onSuccess { Toast.makeText(context, "技能已导出", Toast.LENGTH_SHORT).show() }
                .onFailure {
                    Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TopAppBar(
            title = { Text(if (skill == null) "新增技能" else "编辑技能") },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (skill != null) {
                    TextButton(
                        onClick = {
                            exportLauncher.launch("${TextFiles.safeFileName(skill.name)}.md")
                        },
                    ) {
                        Text("导出")
                    }
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("技能名称") },
                singleLine = true,
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                label = { Text("一句话描述") },
                placeholder = { Text("例如：生成更有画面感的场景描写") },
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用技能", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = skill?.let { viewModel.skillPath(it.id) }
                            ?: "/var/minis/skills/<新目录>/SKILL.md",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 10.dp),
                label = { Text("技能正文（Markdown 提示词）") },
                placeholder = { Text("写入希望 AI 遵循的写作规则或流程…") },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                minLines = 8,
            )
            Button(
                onClick = {
                    viewModel.saveSkill(skill?.id, name, description, body, enabled)
                    Toast.makeText(context, "技能已保存", Toast.LENGTH_SHORT).show()
                    onClose()
                },
                enabled = name.isNotBlank() && body.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Text("保存技能")
            }
            if (skill != null) {
                TextButton(
                    onClick = { showDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text("删除技能", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除技能") },
            text = { Text("将删除「${skill?.name.orEmpty()}」所在的 SKILL.md 文件，无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        skill?.let { viewModel.deleteSkill(it.id) }
                        onClose()
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
