package com.mozhuan.ui.screen.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.system.notify.CompletionNotifier
import com.mozhuan.system.power.BatteryOptimizationHelper

/** 后台与通知：生成完成 / 失败提醒、后台保活与电池优化白名单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundSettingsScreen(
    viewModel: BackgroundSettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.notificationSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var ignoringBatteryOptimizations by remember {
        mutableStateOf(viewModel.isIgnoringBatteryOptimizations())
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        ignoringBatteryOptimizations = viewModel.isIgnoringBatteryOptimizations()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("后台与通知") },
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
                    text = "生成在后台完成时通过系统通知提醒。首次使用需允许通知权限，可在系统设置中随时关闭。",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsGroup(title = "后台保活") {
                NotificationSwitchRow(
                    title = "后台保活",
                    subtitle = "常驻前台服务，降低切到后台后被系统清理的概率",
                    checked = settings.keepAlive,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.setKeepAlive(enabled)
                    },
                )
                SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = BatteryOptimizationHelper.requestIgnoreIntent(context)
                            runCatching { batteryLauncher.launch(intent) }
                                .onFailure {
                                    runCatching {
                                        batteryLauncher.launch(
                                            BatteryOptimizationHelper.appDetailsIntent(context),
                                        )
                                    }
                                }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("电池优化白名单", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (ignoringBatteryOptimizations) {
                                "已忽略电池优化，后台更稳定"
                            } else {
                                "未加入白名单，部分系统仍可能清理后台"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ignoringBatteryOptimizations) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = if (ignoringBatteryOptimizations) "已开启" else "去设置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            SettingsGroup(title = "通知") {
                NotificationSwitchRow(
                    title = "生成完成提醒",
                    subtitle = "AI 回复生成完成时发送通知",
                    checked = settings.notifyOnComplete,
                    onCheckedChange = viewModel::setNotifyOnComplete,
                )
                SettingsDivider()
                NotificationSwitchRow(
                    title = "生成失败提醒",
                    subtitle = "请求失败或服务商报错时发送通知",
                    checked = settings.notifyOnError,
                    onCheckedChange = viewModel::setNotifyOnError,
                )
            }

            Button(
                onClick = {
                    CompletionNotifier(context).notifyTest()
                    Toast.makeText(context, "已发送测试通知", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Text("发送测试通知")
            }
        }
    }
}

@Composable
fun NotificationSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
