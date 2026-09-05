package com.luyuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullToRefreshState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luyuan.domain.Note
import com.luyuan.platform.PermissionHelper

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    vm: LuyuanViewModel,
    onRecord: () -> Unit,
    onDetail: (String) -> Unit,
    onSettings: () -> Unit
) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    val context = LocalContext.current
    val allFilesGranted = PermissionHelper.hasAllFiles(context)

    // 每次回到列表都重新读盘，授权后/同步后立刻可见
    LaunchedEffect(Unit) { vm.refresh() }

    val filtered = remember(notes, query) {
        if (query.isBlank()) notes else notes.filter {
            it.text.contains(query, ignoreCase = true) ||
                    it.tags.any { t -> t.contains(query, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("路远 · 记事") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onRecord) {
                Icon(Icons.Default.Mic, contentDescription = "录音")
            }
        }
    ) { padding ->
        val sttReady by vm.sttReady.collectAsStateWithLifecycle()
        val sttMessage by vm.sttMessage.collectAsStateWithLifecycle()
        val refreshing by vm.refreshing.collectAsStateWithLifecycle()

        // 下拉刷新：列表到顶继续下拉 → 触发 vm.refresh() → 读盘完成自动收起
        val pullState = rememberPullToRefreshState()
        LaunchedEffect(pullState.isRefreshing) {
            if (pullState.isRefreshing) vm.refresh()
        }
        LaunchedEffect(refreshing) {
            if (!refreshing && pullState.isRefreshing) pullState.endRefresh()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullState.nestedScrollConnection)
                .pullRefresh(pullState)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (!allFilesGranted) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "需要「所有文件访问」权限才能读取 Syncthing 同步目录，否则看不到电脑同步来的笔记。",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(onClick = {
                                context.startActivity(PermissionHelper.allFilesSettingsIntent())
                            }) { Text("去开启") }
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索…") },
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("记一笔，回车保存") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (draft.isNotBlank()) {
                            vm.addManual(draft)
                            draft = ""
                        }
                    })
                )
                Text(
                    text = when {
                        sttReady -> "🎤 语音转写已就绪"
                        sttMessage.isNotBlank() -> "🎤 $sttMessage"
                        else -> "🎤 语音模型加载中…"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 88.dp)
                ) {
                    items(filtered, key = { it.id }) { note ->
                        NoteCard(note = note, onClick = { onDetail(note.id) })
                    }
                }
            }
            PullRefreshIndicator(
                isRefreshing = pullState.isRefreshing,
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
                // M2 指示器默认浅色，深色 UI 下显式配色
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun NoteCard(note: Note, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = note.text.ifBlank { "（空）" },
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(if (note.source == "voice") "语音" else "手动") }
                )
                if (note.device == "phone") {
                    AssistChip(onClick = {}, label = { Text("手机") })
                }
                Text(
                    note.created_at.take(16).replace("T", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
