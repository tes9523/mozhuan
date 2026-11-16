package com.mozhuan.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Token 用量：当前会话与按服务商 + 模型的累计统计，不涉及金额与预算。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenUsageScreen(
    viewModel: TokenUsageViewModel,
    onBack: () -> Unit,
) {
    val session by viewModel.sessionUsage.collectAsStateWithLifecycle()
    val cumulative by viewModel.cumulativeUsage.collectAsStateWithLifecycle()
    var showClearAllDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Token 用量") },
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
            SettingsGroup(title = "当前会话") {
                UsageNumbers(
                    input = session.inputTokens,
                    output = session.outputTokens,
                    total = session.totalTokens,
                )
                OutlinedButton(
                    onClick = viewModel::clearSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("清空会话用量")
                }
            }

            SettingsGroup(title = "累计用量（按服务商 + 模型）") {
                if (cumulative.isEmpty()) {
                    Text(
                        text = "暂无累计统计",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                cumulative.forEachIndexed { index, stat ->
                    if (index > 0) SettingsDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = stat.providerName.ifBlank { "未命名服务商" },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stat.model,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "输入 ${stat.inputTokens} · 输出 ${stat.outputTokens} · 总计 ${stat.totalTokens}",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (cumulative.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showClearAllDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    ) {
                        Text("清空全部统计")
                    }
                }
            }

            Text(
                text = "统计来自 API 返回的 usage 数据；服务商未返回 usage 的请求不计入。",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清空全部统计") },
            text = { Text("将同时清空当前会话与所有服务商、模型的累计 Token 统计。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearAllDialog = false
                    },
                ) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun UsageNumbers(input: Int, output: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UsageNumber("输入", input, Modifier.weight(1f))
        UsageNumber("输出", output, Modifier.weight(1f))
        UsageNumber("总计", total, Modifier.weight(1f))
    }
}

@Composable
private fun UsageNumber(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
