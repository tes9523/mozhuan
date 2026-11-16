package com.mozhuan.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.mozhuan.system.file.TextFiles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 备份与恢复：导出单个 JSON 备份，或从备份整体恢复配置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: BackupRestoreViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var showConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching { TextFiles.write(context, uri, viewModel.exportJson()) }
                .onSuccess {
                    Toast.makeText(context, "备份已导出", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching { TextFiles.read(context, uri) }
                .onSuccess { content ->
                    pendingImport = content
                    showConfirm = true
                }
                .onFailure {
                    Toast.makeText(context, "读取备份失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("备份与恢复") },
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
                    text = "备份包含服务商、模型参数、模型组、人格与技能。API Key 会以明文写入备份文件，请妥善保管。",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsGroup(
                title = "备份文件",
                footer = "恢复会覆盖当前服务商、模型、模型组、人格与技能配置，请先导出备份。",
            ) {
                SettingsRow(
                    icon = Icons.Filled.Share,
                    title = "导出备份",
                    subtitle = "生成 JSON 文件并保存到手机",
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
                            .format(Date())
                        exportLauncher.launch("mozhuan-backup-$stamp.json")
                    },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.List,
                    title = "从备份恢复",
                    subtitle = "选择之前导出的 JSON 备份文件",
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                )
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = {
                showConfirm = false
                pendingImport = null
            },
            title = { Text("确认恢复备份") },
            text = {
                Text("将用备份文件覆盖当前全部服务商、模型、模型组、人格与技能配置。此操作无法撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        val content = pendingImport
                        pendingImport = null
                        if (content != null) {
                            viewModel.importJson(content)
                                .onSuccess { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        "恢复失败：${it.message}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                        }
                    },
                ) {
                    Text("开始恢复", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        pendingImport = null
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }
}
