package com.mozhuan.ui.screen.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.GroupRoutingStrategy
import com.mozhuan.data.model.ModelGroup

/** 模型组：列表与详情两级结构，对齐 OpenMinis 的模型组设置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelGroupsScreen(
    viewModel: ModelGroupsViewModel,
    onBack: () -> Unit,
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val group = detail
    if (group == null) {
        ModelGroupList(viewModel = viewModel, onBack = onBack)
    } else {
        ModelGroupDetail(viewModel = viewModel, group = group)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelGroupList(
    viewModel: ModelGroupsViewModel,
    onBack: () -> Unit,
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("模型组") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { newName = ""; showAdd = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "新增模型组")
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
            SettingsGroup(
                footer = "模型组可以把多个模型组合成一个入口：故障转移按顺序调用，失败时自动换下一个；负载均衡每次随机选择。",
            ) {
                if (groups.isEmpty()) {
                    Text(
                        text = "还没有模型组，点击右上角 + 创建。",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    groups.forEachIndexed { index, group ->
                        if (index > 0) SettingsDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.open(group.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary),
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
                                    text = group.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${group.memberEntryIds.size} 个模型 · " +
                                        group.strategy.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (group.id == selectedId) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "当前使用",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.size(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("新增模型组") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("模型组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.create(newName)
                        showAdd = false
                    },
                    enabled = newName.isNotBlank(),
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelGroupDetail(
    viewModel: ModelGroupsViewModel,
    group: ModelGroup,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    var showMembers by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var name by remember(group.id) { mutableStateOf(group.name) }

    val providerNames = providers.associate { it.id to it.displayName }
    val entriesById = entries.associateBy { it.id }
    val members = group.memberEntryIds.mapNotNull { entriesById[it] }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(group.name.ifBlank { "模型组" }) },
                navigationIcon = {
                    IconButton(onClick = viewModel::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            SettingsGroup(title = "名称") {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        viewModel.rename(group, it)
                    },
                    label = { Text("模型组名称") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            SettingsGroup(title = "路由策略") {
                GroupRoutingStrategy.entries.forEachIndexed { index, strategy ->
                    if (index > 0) SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setStrategy(group, strategy) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = group.strategy == strategy,
                            onClick = { viewModel.setStrategy(group, strategy) },
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(strategy.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = strategy.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SettingsGroup(
                title = "成员",
                footer = if (members.isEmpty()) {
                    "成员为空时无法在对话页选择该模型组。"
                } else {
                    "按列表顺序参与故障转移；停用服务商下的模型会自动跳过。"
                },
            ) {
                members.forEachIndexed { index, entry ->
                    if (index > 0) SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = providerNames[entry.providerId] ?: "未知服务商",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.toggleMember(group, entry.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "移除成员",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (members.isNotEmpty()) SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMembers = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "添加模型",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Button(
                onClick = { viewModel.select(group) },
                enabled = members.isNotEmpty() && group.id != selectedId,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Text(if (group.id == selectedId) "当前正在使用" else "使用此模型组")
            }

            TextButton(
                onClick = { showDelete = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Text("删除模型组", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showMembers) {
        val enabledProviders = providers.filter { it.isEnabled }.map { it.id }.toSet()
        AlertDialog(
            onDismissRequest = { showMembers = false },
            title = { Text("选择成员模型") },
            text = {
                if (entries.isEmpty()) {
                    Text("还没有可用模型")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        items(entries, key = { it.id }) { entry ->
                            val enabled = entry.providerId in enabledProviders
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = enabled) {
                                        viewModel.toggleMember(group, entry.id)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = entry.id in group.memberEntryIds,
                                    onCheckedChange = { viewModel.toggleMember(group, entry.id) },
                                    enabled = enabled,
                                )
                                Column {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = providerNames[entry.providerId] ?: "未知服务商",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMembers = false }) { Text("完成") }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除模型组") },
            text = { Text("将删除「${group.name}」，不会影响组内模型本身。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        viewModel.delete(group)
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
