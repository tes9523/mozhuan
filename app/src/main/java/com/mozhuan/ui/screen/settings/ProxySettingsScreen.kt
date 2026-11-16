package com.mozhuan.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.ProxySettings

/** 代理设置页：HTTP / SOCKS5 代理，保存后新请求立即生效。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(
    viewModel: ProxySettingsViewModel,
    onBack: () -> Unit,
) {
    val current by viewModel.proxySettings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var enabled by remember(current) { mutableStateOf(current.enabled) }
    var type by remember(current) { mutableStateOf(current.type) }
    var host by remember(current) { mutableStateOf(current.host) }
    var port by remember(current) { mutableStateOf(current.port.toString()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("代理设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "为所有 AI 请求设置代理。保存后下一次请求立即使用新代理，无需重启应用。",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsGroup(title = "代理") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用代理", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (current.enabled) current.summary else "当前直连",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                SettingsDivider()
                ProxyTypeRow(
                    label = ProxySettings.PROXY_HTTP,
                    selected = type == ProxySettings.PROXY_HTTP,
                    onClick = { type = ProxySettings.PROXY_HTTP },
                )
                SettingsDivider()
                ProxyTypeRow(
                    label = ProxySettings.PROXY_SOCKS,
                    selected = type == ProxySettings.PROXY_SOCKS,
                    onClick = { type = ProxySettings.PROXY_SOCKS },
                )
            }

            SettingsGroup(title = "地址") {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("代理地址") },
                    placeholder = { Text("例如 127.0.0.1") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                SettingsDivider()
                OutlinedTextField(
                    value = port,
                    onValueChange = { text -> port = text.filter { it.isDigit() }.take(5) },
                    label = { Text("端口") },
                    placeholder = { Text("7890") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            Button(
                onClick = {
                    val portValue = port.toIntOrNull() ?: 0
                    if (enabled && (host.isBlank() || portValue !in 1..65535)) {
                        Toast.makeText(context, "请填写有效的代理地址与端口", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.save(
                        ProxySettings(
                            enabled = enabled,
                            type = type,
                            host = host.trim(),
                            port = portValue.takeIf { it in 1..65535 } ?: 7890,
                        ),
                    )
                    Toast.makeText(context, "代理设置已保存", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Text("保存代理设置")
            }
        }
    }
}

@Composable
private fun ProxyTypeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
