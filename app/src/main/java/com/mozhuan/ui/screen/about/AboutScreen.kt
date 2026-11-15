package com.mozhuan.ui.screen.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 关于页：展示应用名称、版本与阶段说明，并提供全局设置入口。
 * 主题切换、存储目录、保活等能力仍在设置页中维护。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("关于页") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "墨撰",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "版本 $versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "面向网文创作者的本地写作工具\n阶段1：导航框架与基础页面",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            )
            OutlinedButton(onClick = onOpenSettings) {
                Text("打开全局设置")
            }
        }
    }
}
