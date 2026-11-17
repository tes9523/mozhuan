package com.mozhuan.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.ApiStyle
import com.mozhuan.data.model.ModelEntry
import com.mozhuan.data.model.ProviderInstance
import com.mozhuan.data.model.ProviderType
import com.mozhuan.data.model.ThinkingLevel
import java.util.Locale
import kotlin.math.roundToInt

/** 提供商管理：列表 → 类型 → 配置 → 模型参数，对齐 OpenMinis 的层级。 */
@Composable
fun ProviderConfigScreen(
    viewModel: ProviderConfigViewModel,
    onBack: () -> Unit,
) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    // 系统返回键先逐级回退（模型 → 表单 → 列表），不要整页退出到设置。
    BackHandler(enabled = stage != ProviderStage.LIST) { viewModel.back() }
    when (stage) {
        ProviderStage.LIST -> ProviderListContent(viewModel, onBack)
        ProviderStage.TYPE -> ProviderTypeContent(viewModel)
        ProviderStage.FORM -> ProviderFormContent(viewModel)
        ProviderStage.MODEL -> ModelDetailContent(viewModel)
    }
}

// ── 服务商列表 ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderListContent(viewModel: ProviderConfigViewModel, onBack: () -> Unit) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val selectedEntryId by viewModel.selectedEntryId.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("提供商") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::startAdd) {
                        Icon(Icons.Default.Add, contentDescription = "新增服务商")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (providers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "还没有服务商",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "点击右上角 + 添加 OpenAI、Anthropic、Gemini、OpenRouter 或任意 OpenAI 兼容服务商。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    SettingsGroup(
                        footer = "关闭的服务商不会出现在对话页；左侧圆点标记当前使用的模型。",
                    ) {
                        providers.forEachIndexed { index, provider ->
                            val providerEntries = entries.filter { it.providerId == provider.id }
                            val activeTitle = providerEntries
                                .firstOrNull { it.id == selectedEntryId }?.title
                            ProviderListRow(
                                provider = provider,
                                modelCount = providerEntries.size,
                                activeModel = activeTitle,
                                onOpen = { viewModel.editProvider(provider) },
                                onEnabledChange = { viewModel.setProviderEnabled(provider.id, it) },
                            )
                            if (index != providers.lastIndex) SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderListRow(
    provider: ProviderInstance,
    modelCount: Int,
    activeModel: String?,
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
        ProviderBadge(provider.type)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(provider.type.displayName)
                    append(" · ")
                    append(modelCount)
                    append(" 个模型")
                    if (activeModel != null) append(" · 当前 $activeModel")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = provider.isEnabled,
            onCheckedChange = onEnabledChange,
        )
    }
}

// ── 选择服务商类型 ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderTypeContent(viewModel: ProviderConfigViewModel) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("选择服务商类型") },
                navigationIcon = {
                    IconButton(onClick = viewModel::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                SettingsGroup(
                    title = "内置类型",
                    footer = "自定义类型适用于 DeepSeek、Kimi、GLM、通义千问等任意 OpenAI 兼容接口。",
                ) {
                    ProviderType.entries.forEachIndexed { index, type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.chooseType(type) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProviderBadge(type)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            ) {
                                Text(type.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = type.defaultBaseUrl.ifBlank { "需要手动填写请求地址" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        if (index != ProviderType.entries.lastIndex) SettingsDivider()
                    }
                }
            }
        }
    }
}

// ── 服务商配置 ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProviderFormContent(viewModel: ProviderConfigViewModel) {
    val draft by viewModel.draftProvider.collectAsStateWithLifecycle()
    val isNew by viewModel.isNew.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val selectedEntryId by viewModel.selectedEntryId.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val loadingModels by viewModel.loadingModels.collectAsStateWithLifecycle()
    val modelsError by viewModel.modelsError.collectAsStateWithLifecycle()
    val newModelInput by viewModel.newModelInput.collectAsStateWithLifecycle()

    val provider = draft ?: return
    val type = provider.type
    val providerEntries = entries.filter { it.providerId == provider.id }
    var showKey by remember { mutableStateOf(false) }
    var showDeleteProvider by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新增服务商" else "编辑服务商") },
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
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup(title = "基本信息") {
                FormField(
                    label = "服务商名称",
                    value = provider.label,
                    placeholder = type.displayName,
                    onValueChange = { text -> viewModel.updateDraft { it.copy(label = text) } },
                )
                SettingsDivider()
                ReadOnlyRow("类型", type.displayName)
            }

            SettingsGroup(
                title = "凭证",
                footer = "API Key 仅保存在本机；留空适用于无需鉴权的本地或内网服务。",
            ) {
                OutlinedTextField(
                    value = provider.apiKey,
                    onValueChange = { text -> viewModel.updateDraft { it.copy(apiKey = text.trim()) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "隐藏" else "显示")
                        }
                    },
                )
            }

            val hint = when (type) {
                ProviderType.GEMINI ->
                    "留空使用 Google 的 OpenAI 兼容地址；也可填写其他兼容网关。"
                ProviderType.CUSTOM ->
                    "填写完整的兼容地址。开启「追加 /v1」时，只在末尾没有 /v1 时自动补上。"
                else ->
                    "留空使用 ${type.displayName} 官方地址。开启「追加 /v1」时，只在末尾没有 /v1 时自动补上。"
            }
            SettingsGroup(title = "请求地址", footer = hint) {
                OutlinedTextField(
                    value = provider.customBaseUrl,
                    onValueChange = { text ->
                        viewModel.updateDraft { it.copy(customBaseUrl = text.trim()) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    label = { Text("自定义地址（可选）") },
                    placeholder = {
                        Text(type.defaultBaseUrl.ifBlank { "https://api.example.com" })
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                if (type != ProviderType.GEMINI) {
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自动追加 /v1", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "关闭后地址原样使用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = provider.appendV1Suffix,
                            onCheckedChange = { checked ->
                                viewModel.updateDraft { it.copy(appendV1Suffix = checked) }
                            },
                        )
                    }
                }
                if (provider.effectiveBaseUrl.isNotBlank()) {
                    SettingsDivider()
                    ReadOnlyRow(
                        label = "实际端点",
                        value = provider.effectiveBaseUrl + when (type.apiStyle) {
                            ApiStyle.OPENAI_CHAT -> "/chat/completions"
                            ApiStyle.ANTHROPIC_MESSAGES -> "/messages"
                        },
                        mono = true,
                    )
                }
            }

            if (isNew) {
                SettingsGroup(
                    title = "模型",
                    footer = "保存服务商后即可拉取模型列表，并为每个模型单独配置生成参数。",
                ) {
                    ReadOnlyRow("模型", "保存后配置")
                }
            } else {
                SettingsGroup(
                    title = "模型",
                    footer = "点击模型可单独设置 max_tokens（留空使用服务商默认）、上下文轮数、temperature 与 top_p；左侧圆点用于切换当前模型。",
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "模型列表",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (loadingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        TextButton(onClick = viewModel::refreshModels, enabled = !loadingModels) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("获取模型")
                        }
                    }

                    providerEntries.forEach { entry ->
                        SettingsDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.editEntry(entry) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = entry.id == selectedEntryId,
                                onClick = { viewModel.selectEntry(entry.id) },
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                            ) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "max_tokens " +
                                        (entry.maxTokens.takeIf { it > 0 }?.toString()
                                            ?: "服务商默认") +
                                        " · 上下文 ${entry.contextTurns} 轮",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    if (providerEntries.isEmpty()) {
                        SettingsDivider()
                        Text(
                            text = "尚未添加模型，可拉取列表或手动添加。",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newModelInput,
                            onValueChange = viewModel::onNewModelInputChange,
                            modifier = Modifier.weight(1f),
                            label = { Text("模型名称") },
                            placeholder = { Text("例如 deepseek-chat") },
                            singleLine = true,
                        )
                        TextButton(
                            onClick = { viewModel.addModel() },
                            enabled = newModelInput.isNotBlank(),
                        ) {
                            Text("添加")
                        }
                    }

                    val suggestions = type.defaultModels.filterNot { modelId ->
                        providerEntries.any { it.modelId == modelId }
                    }
                    if (suggestions.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            suggestions.forEach { modelId ->
                                AssistChip(
                                    onClick = { viewModel.addModel(modelId) },
                                    label = { Text(modelId, maxLines = 1) },
                                )
                            }
                        }
                    }
                }
            }

            if (modelsError != null) {
                Text(
                    text = modelsError.orEmpty(),
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SettingsGroup(title = "连接") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !testing, onClick = viewModel::testConnection)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("测试连接", modifier = Modifier.weight(1f))
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "运行",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                testResult?.let { result ->
                    SettingsDivider()
                    Text(
                        text = result.message,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.success) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }

            Button(
                onClick = viewModel::saveProvider,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Text("保存服务商")
            }

            if (!isNew) {
                TextButton(
                    onClick = { showDeleteProvider = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("删除服务商", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteProvider) {
        AlertDialog(
            onDismissRequest = { showDeleteProvider = false },
            title = { Text("删除服务商") },
            text = { Text("将同时删除该服务商下的 ${providerEntries.size} 个模型配置，已累计的 Token 统计会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteProvider = false
                        viewModel.deleteProvider()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProvider = false }) { Text("取消") }
            },
        )
    }
}

// ── 模型参数 ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDetailContent(viewModel: ProviderConfigViewModel) {
    val entry by viewModel.draftEntry.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val model = entry ?: return
    val provider = providers.firstOrNull { it.id == model.providerId }
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("模型参数") },
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
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup(title = "模型") {
                ReadOnlyRow("服务商", provider?.displayName ?: "未知")
                SettingsDivider()
                ReadOnlyRow("模型 ID", model.modelId, mono = true)
                SettingsDivider()
                FormField(
                    label = "显示名称（可选）",
                    value = model.displayName,
                    placeholder = model.modelId,
                    onValueChange = { text ->
                        viewModel.updateEntryDraft { it.copy(displayName = text) }
                    },
                )
            }

            SettingsGroup(
                title = "生成参数",
                footer = "max_tokens 留空时使用服务商默认值；上下文轮数为本地历史轮数，0 表示只发送当前消息。",
            ) {
                NumberField(
                    label = "max_tokens",
                    value = model.maxTokens,
                    onValueChange = { value ->
                        viewModel.updateEntryDraft { it.copy(maxTokens = value) }
                    },
                    allowEmpty = true,
                    placeholder = "服务商默认",
                )
                SettingsDivider()
                NumberField(
                    label = "上下文轮数",
                    value = model.contextTurns,
                    onValueChange = { value ->
                        viewModel.updateEntryDraft { it.copy(contextTurns = value) }
                    },
                )
                SettingsDivider()
                ThinkingLevelRow(
                    selected = model.thinkingLevel,
                    onSelect = { level ->
                        viewModel.updateEntryDraft { it.copy(thinkingLevel = level) }
                    },
                )
                SettingsDivider()
                SliderRow(
                    label = "temperature",
                    value = model.temperature,
                    valueRange = 0f..2f,
                    display = formatFloat(model.temperature),
                    onValueChange = { value ->
                        viewModel.updateEntryDraft { it.copy(temperature = value) }
                    },
                )
                SettingsDivider()
                SliderRow(
                    label = "top_p",
                    value = model.topP,
                    valueRange = 0f..1f,
                    display = formatFloat(model.topP),
                    onValueChange = { value ->
                        viewModel.updateEntryDraft { it.copy(topP = value) }
                    },
                )
            }

            Button(
                onClick = viewModel::saveEntry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Text("保存模型参数")
            }

            TextButton(
                onClick = { showDelete = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) {
                Text("删除模型", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除模型") },
            text = { Text("确定删除「${model.title}」及其参数配置？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        viewModel.deleteEntry()
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

// ── 小组件 ───────────────────────────────────────────────────────

@Composable
private fun ProviderBadge(type: ProviderType) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(providerColor(type)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = type.displayName.take(1).uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun providerColor(type: ProviderType): Color = when (type) {
    ProviderType.OPENAI -> Color(0xFF10A37F)
    ProviderType.ANTHROPIC -> Color(0xFFD97757)
    ProviderType.GEMINI -> Color(0xFF4285F4)
    ProviderType.OPENROUTER -> Color(0xFF6366F1)
    ProviderType.XAI -> Color(0xFF1F2937)
    ProviderType.CUSTOM -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun ReadOnlyRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (mono) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
    )
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    allowEmpty: Boolean = false,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = if (allowEmpty && value <= 0) "" else value.toString(),
        onValueChange = { text ->
            val digits = text.filter { it.isDigit() }.take(7)
            onValueChange(digits.toIntOrNull() ?: 0)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        label = { Text(label) },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThinkingLevelRow(
    selected: ThinkingLevel,
    onSelect: (ThinkingLevel) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text("思考强度", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "按服务商协议发送；不支持的模型可能忽略或拒绝该参数",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThinkingLevel.entries.forEach { level ->
                AssistChip(
                    onClick = { onSelect(level) },
                    label = { Text(level.label) },
                    leadingIcon = if (level == selected) {
                        { Text("✓") }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: String,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = display,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { onValueChange((it * 100).roundToInt() / 100f) },
            valueRange = valueRange,
        )
    }
}

private fun formatFloat(value: Float): String =
    String.format(Locale.US, "%.2f", value)
