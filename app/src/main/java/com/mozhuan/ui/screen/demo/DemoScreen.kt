package com.mozhuan.ui.screen.demo

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 演示页：阶段1 仅提供基础交互演示（点击按钮弹出 Toast），
 * 不包含网络、数据库或任何业务逻辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen() {
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("演示页") }) },
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
                text = "基础交互演示",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "点击下方按钮，弹出 Toast 提示",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Button(
                onClick = {
                    Toast.makeText(context, "你好，墨撰！按钮点击成功", Toast.LENGTH_SHORT).show()
                },
            ) {
                Text("点击弹出 Toast")
            }
        }
    }
}
