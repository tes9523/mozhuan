package com.mozhuan.ui.screen.settings

import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import java.util.Locale

/**
 * LLM 提供商管理：接口地址 / API Key / 模型 + 生成参数，本地持久化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigScreen(
    viewModel: ProviderConfigViewModel,
    onBack: () -> Unit,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showApiKey by remember { mutableStateOf(false) }

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
            SettingsGroup(title = "连接信息") {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = draft.baseUrl,
                        onValueChange = { value -> viewModel.update { it.copy(baseUrl = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("接口地址") },
                        placeholder = { Text("https://api.example.com/v1") },
                        singleLine = true,
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
                    OutlinedTextField(
                        value = draft.model,
                        onValueChange = { value -> viewModel.update { it.copy(model = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("模型名称") },
                        placeholder = { Text("gpt-4o-mini / deepseek-chat …") },
                        singleLine = true,
                    )
                }
            }

            SettingsGroup(title = "生成参数") {
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
                        value = draft.contextTurns.takeIf { it > 0 }?.toString() ?: "0",
                        onValueChange = { value ->
                            val number = value.filter { it.isDigit() }.take(3).toIntOrNull() ?: 0
                            viewModel.update { it.copy(contextTurns = number) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("上下文轮数") },
                        placeholder = { Text("10") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(
                        text = "每次对话携带最近 N 轮历史，0 表示仅发送当前消息",
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
                        viewModel.save()
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "已恢复默认参数", Toast.LENGTH_SHORT).show()
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

                val result = testResult
                if (result != null) {
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
