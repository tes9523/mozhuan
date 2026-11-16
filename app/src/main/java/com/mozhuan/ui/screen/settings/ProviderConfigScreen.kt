package com.mozhuan.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.ApiSettings
import java.util.Locale
import kotlin.math.roundToInt

private val MODEL_SUGGESTIONS = listOf(
    "gpt-4o-mini",
    "gpt-4.1-mini",
    "deepseek-chat",
    "deepseek-reasoner",
    "qwen-plus",
)

/** 多服务商管理：列表、默认项、编辑、独立参数与连接测试。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigScreen(
    viewModel: ProviderConfigViewModel,
    onBack: () -> Unit,
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val defaultProviderId by viewModel.defaultProviderId.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showApiKey by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ApiSettings?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("管理提供商") },
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
                .imePadding()
                .padding(bottom = 24.dp),
        ) {
            SettingsGroup(title = "服务商列表") {
                if (providers.isEmpty()) {
                    Text(
                        text = "尚未添加服务商",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                providers.forEachIndexed { index, provider ->
                    if (index > 0) SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.edit(provider) }
                            .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = provider.id == defaultProviderId,
                            onClick = { viewModel.setDefault(provider.id) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = buildString {
                                    if (provider.id == defaultProviderId) append("默认 · ")
                                    append(provider.model.ifBlank { "未填写模型" })
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { pendingDelete = provider }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除 ${provider.displayName}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = viewModel::startNew,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    Text("新增服务商")
                }
            }

            SettingsGroup(title = "连接信息") {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { value -> viewModel.update { it.copy(name = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("服务商名称") },
                        placeholder = { Text("例如：DeepSeek") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = draft.baseUrl,
                        onValueChange = { value -> viewModel.update { it.copy(baseUrl = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("chat/completions 端点") },
                        placeholder = { Text("https://api.example.com/v1/chat/completions") },
                        singleLine = true,
                    )
                    Text(
                        text = "端点将原样发送，不自动拼接路径",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = draft.apiKey,
                        onValueChange = { value -> viewModel.update { it.copy(apiKey = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key") },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        visualTransformation = if (showApiKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { showApiKey = !showApiKey }) {
                                Text(if (showApiKey) "隐藏" else "显示")
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    ExposedDropdownMenuBox(
                        expanded = modelMenuExpanded,
                        onExpandedChange = { modelMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = draft.model,
                            onValueChange = { value -> viewModel.update { it.copy(model = value) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryEditable),
                            label = { Text("模型名称（可选择或手动输入）") },
                            placeholder = { Text("输入自定义模型") },
                            singleLine = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded)
                            },
                        )
                        ExposedDropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false },
                        ) {
                            MODEL_SUGGESTIONS.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        viewModel.update { it.copy(model = model) }
                                        modelMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            SettingsGroup(title = "生成参数（当前服务商独立保存）") {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = draft.maxTokens.takeIf { it > 0 }?.toString().orEmpty(),
                        onValueChange = { value ->
                            val number = value.filter { it.isDigit() }.take(6).toIntOrNull() ?: 0
                            viewModel.update { it.copy(maxTokens = number) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("max_tokens") },
                        placeholder = { Text("2048") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = draft.contextTurns.toString(),
                        onValueChange = { value ->
                            val number = value.filter { it.isDigit() }.take(3).toIntOrNull() ?: 0
                            viewModel.update { it.copy(contextTurns = number) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("上下文轮数") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(
                        text = "0 表示不携带历史，仅发送当前消息",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))
                    SliderField(
                        label = "temperature",
                        valueText = String.format(Locale.US, "%.1f", draft.temperature),
                        value = draft.temperature,
                        valueRange = 0f..2f,
                        steps = 19,
                        onValueChange = { value ->
                            val rounded = (value * 10).roundToInt() / 10f
                            viewModel.update { it.copy(temperature = rounded) }
                        },
                    )

                    Spacer(Modifier.height(12.dp))
                    SliderField(
                        label = "top_p",
                        valueText = String.format(Locale.US, "%.2f", draft.topP),
                        value = draft.topP,
                        valueRange = 0.05f..1f,
                        steps = 18,
                        onValueChange = { value ->
                            val rounded = (value * 20).roundToInt() / 20f
                            viewModel.update { it.copy(topP = rounded.coerceIn(0.05f, 1f)) }
                        },
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Button(
                    onClick = {
                        val valid = draft.isConfigured && draft.name.isNotBlank()
                        viewModel.save()
                        if (valid) Toast.makeText(context, "服务商配置已保存", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.resetParameters()
                            Toast.makeText(context, "已恢复默认参数，保存后生效", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("重置")
                    }
                    OutlinedButton(
                        onClick = viewModel::testConnection,
                        modifier = Modifier.weight(1f),
                        enabled = !testing,
                    ) {
                        if (testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("测试连接")
                        }
                    }
                }

                testResult?.let { result ->
                    Text(
                        text = result.message,
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.success) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { provider ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除服务商") },
            text = { Text("确定删除「${provider.displayName}」吗？其累计 Token 统计会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(provider.id)
                        pendingDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SliderField(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
    )
}
