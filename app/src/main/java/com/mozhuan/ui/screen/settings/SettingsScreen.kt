package com.mozhuan.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.business.model.ThemeMode
import com.mozhuan.system.permission.NotificationPermission
import com.mozhuan.system.power.BatteryOptimizationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val storageName by viewModel.storageName.collectAsStateWithLifecycle()
    val storageReady by viewModel.storageReady.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var notificationGranted by remember { mutableStateOf(NotificationPermission.isGranted(context)) }
    var batteryIgnoring by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringOptimizations(context))
    }

    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.selectStorage(uri)
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        notificationGranted = NotificationPermission.isGranted(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = NotificationPermission.isGranted(context)
                batteryIgnoring = BatteryOptimizationHelper.isIgnoringOptimizations(context)
                viewModel.refreshStorage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                title = { Text("设置") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionTitle("外观")
            ThemeOption("跟随系统", ThemeMode.SYSTEM, themeMode) {
                viewModel.setThemeMode(it)
            }
            ThemeOption("浅色", ThemeMode.LIGHT, themeMode) {
                viewModel.setThemeMode(it)
            }
            ThemeOption("深色", ThemeMode.DARK, themeMode) {
                viewModel.setThemeMode(it)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("书稿存储")
            ListItem(
                headlineContent = { Text("存储目录") },
                supportingContent = {
                    Column {
                        Text(storageName ?: "未选择")
                        if (storageName != null && !storageReady) {
                            Text(
                                text = "目录权限已失效，请重新授权",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                trailingContent = {
                    TextButton(onClick = { storageLauncher.launch(null) }) {
                        Text(if (storageName == null) "选择" else "更换")
                    }
                },
            )
            Text(
                text = "只会将书稿的 Markdown 文件写入该目录；书名、章节索引等元数据保存在应用数据库中。",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("后台与通知")
            ListItem(
                headlineContent = { Text("通知权限") },
                supportingContent = {
                    Text(
                        if (notificationGranted) {
                            "已授权，编辑时显示常驻写作通知"
                        } else {
                            "未授权，通知栏不显示写作状态"
                        },
                    )
                },
                trailingContent = {
                    TextButton(
                        onClick = {
                            runCatching {
                                notificationLauncher.launch(NotificationPermission.settingsIntent(context))
                            }
                        },
                    ) {
                        Text("去设置")
                    }
                },
            )
            ListItem(
                headlineContent = { Text("电池优化") },
                supportingContent = {
                    Text(
                        if (batteryIgnoring) {
                            "已允许后台运行"
                        } else {
                            "可能被系统限制后台，建议关闭优化"
                        },
                    )
                },
                trailingContent = {
                    TextButton(
                        onClick = {
                            val intent = if (batteryIgnoring) {
                                BatteryOptimizationHelper.appDetailsIntent(context)
                            } else {
                                BatteryOptimizationHelper.requestIgnoreIntent(context)
                            }
                            runCatching { context.startActivity(intent) }.onFailure {
                                runCatching {
                                    context.startActivity(
                                        BatteryOptimizationHelper.appDetailsIntent(context),
                                    )
                                }
                            }
                        },
                    ) {
                        Text("去设置")
                    }
                },
            )
            Text(
                text = "编辑书稿时会启动前台服务并常驻通知，以减少切到后台后被系统回收的概率。",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("关于")
            ListItem(
                headlineContent = { Text("墨撰") },
                supportingContent = {
                    Text("版本 0.2.0 · 纯本地书稿模块，未启用任何 AI 能力")
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ThemeOption(
    label: String,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) }
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = mode == current, onClick = { onSelect(mode) })
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
