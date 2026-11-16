package com.mozhuan.ui.screen.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.Skill
import com.mozhuan.ui.component.MozhuanChip

/**
 * 技能管理页：内置技能只读展示，自定义技能可新增 / 编辑 / 删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillManagementScreen(
    viewModel: SkillManagementViewModel,
    onBack: () -> Unit,
) {
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingSkill by remember { mutableStateOf<Skill?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("技能管理") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingSkill = null
                    showEditor = true
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新增技能") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(skills, key = { it.id }) { skill ->
                SkillCard(
                    skill = skill,
                    onEdit = {
                        editingSkill = skill
                        showEditor = true
                    },
                    onDelete = { viewModel.deleteSkill(skill.id) },
                )
            }
        }
    }

    if (showEditor) {
        SkillEditorDialog(
            skill = editingSkill,
            onDismiss = { showEditor = false },
            onConfirm = { name, prompt, tags ->
                val ok = if (editingSkill == null) {
                    viewModel.addSkill(name, prompt, tags)
                } else {
                    viewModel.updateSkill(editingSkill!!.id, name, prompt, tags)
                }
                if (ok) showEditor = false
            },
        )
    }
}

@Composable
private fun SkillCard(skill: Skill, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (skill.builtin) {
                    MozhuanChip(text = "内置", selected = true)
                } else {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }
            }
            Text(
                text = skill.systemPrompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
            if (skill.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    skill.tags.take(3).forEach { tag -> MozhuanChip(text = "#$tag") }
                }
            }
        }
    }
}

@Composable
private fun SkillEditorDialog(
    skill: Skill?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, prompt: String, tags: List<String>) -> Unit,
) {
    var name by remember { mutableStateOf(skill?.name.orEmpty()) }
    var prompt by remember { mutableStateOf(skill?.systemPrompt.orEmpty()) }
    var tagsText by remember { mutableStateOf(skill?.tags?.joinToString(",").orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (skill == null) "新增技能" else "编辑技能") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("技能名称") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("系统 Prompt") },
                    minLines = 4,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("标签（逗号分隔）") },
                    placeholder = { Text("对话, 脑暴") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tags = tagsText.split(',', '，')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onConfirm(name.trim(), prompt.trim(), tags)
                },
                enabled = name.isNotBlank() && prompt.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
