package com.mozhuan.ui.screen.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mozhuan.data.model.Book
import com.mozhuan.ui.component.MozhuanChip

/**
 * 书稿详情页：本书设定（世界观 / 人物）、专属技能绑定、章节列表，
 * 并提供进入本书专属 AI 对话的入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    viewModel: BookDetailViewModel,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenChapter: (String) -> Unit,
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val skills by viewModel.skills.collectAsStateWithLifecycle()

    var showAddChapterDialog by remember { mutableStateOf(false) }
    var newChapterTitle by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            title = { Text(book?.title ?: "书稿") },
        )

        val current = book
        if (current == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("书稿不存在或已被删除")
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // ── 进入本书专属对话 ──────────────────────────────────
            Button(
                onClick = onOpenChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text("进入本书 AI 对话", modifier = Modifier.padding(start = 8.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── 本书设定 ─────────────────────────────────────────
            SectionTitle("本书设定")
            OutlinedTextField(
                value = current.worldview,
                onValueChange = viewModel::updateWorldview,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                label = { Text("世界观设定") },
                placeholder = { Text("时代背景、力量体系、地理格局…") },
                minLines = 3,
            )
            OutlinedTextField(
                value = current.characters,
                onValueChange = viewModel::updateCharacters,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                label = { Text("人物设定") },
                placeholder = { Text("主角、配角、关系与性格…") },
                minLines = 3,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── 专属技能 ─────────────────────────────────────────
            SectionTitle("专属技能")
            Text(
                text = "书稿绑定的技能优先于全局技能，AI 对话与续写时会自动注入",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                skills.chunked(2).forEach { rowSkills ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowSkills.forEach { skill ->
                            MozhuanChip(
                                text = skill.name,
                                selected = skill.id in current.boundSkillIds,
                                onClick = { viewModel.toggleBoundSkill(skill.id) },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── 章节列表 ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("章节（${current.chapters.size}）", modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        newChapterTitle = "第 ${current.chapters.size + 1} 章"
                        showAddChapterDialog = true
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("添加章节", modifier = Modifier.padding(start = 4.dp))
                }
            }

            if (current.chapters.isEmpty()) {
                Text(
                    text = "还没有章节，点击「添加章节」开始写作",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                current.chapters.forEachIndexed { index, chapter ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onOpenChapter(chapter.id) },
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "${index + 1}. ${chapter.title}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "${countWords(chapter.content)} 字",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddChapterDialog) {
        AlertDialog(
            onDismissRequest = { showAddChapterDialog = false },
            title = { Text("添加章节") },
            text = {
                OutlinedTextField(
                    value = newChapterTitle,
                    onValueChange = { newChapterTitle = it },
                    label = { Text("章节标题") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = viewModel.addChapter(newChapterTitle)
                        showAddChapterDialog = false
                        if (id != null) onOpenChapter(id)
                    },
                    enabled = newChapterTitle.isNotBlank(),
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showAddChapterDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun countWords(text: String): Int = text.count { !it.isWhitespace() }
