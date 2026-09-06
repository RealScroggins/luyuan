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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luyuan.domain.Note
import com.luyuan.platform.PermissionHelper
import kotlin.math.roundToInt

/** 自实现下拉刷新：列表到顶继续下拉累计 overPull，松手超过阈值触发刷新。
 *  不用任何 pullrefresh 库 API——不同 compose/material 版本签名差异太大，CI 连败两次的教训。 */
private class PullRefreshConnection(
    private val thresholdPx: Float,
    private val onRefresh: () -> Unit
) : NestedScrollConnection {
    var overPull by mutableFloatStateOf(0f)
        private set

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // 手指上滑时先把拉出的距离收回去，收完才放行给列表滚动
        if (overPull > 0f && available.y < 0f) {
            val take = available.y.coerceAtLeast(-overPull)
            overPull += take
            return Offset(0f, take)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // 列表到顶后仍往下拉的部分 → 计入下拉距离（带阻尼）
        if (available.y > 0f) {
            overPull = (overPull + available.y * 0.5f).coerceAtMost(thresholdPx * 1.8f)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (overPull >= thresholdPx) onRefresh()
        return Velocity.Zero
    }

    fun reset() {
        overPull = 0f
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

        // 下拉刷新：松手超阈值触发 vm.refresh()；刷新结束收起指示器
        val density = LocalDensity.current
        val thresholdPx = remember(density) { with(density) { 110.dp.toPx() } }
        val indicatorSizePx = remember(density) { with(density) { 44.dp.toPx() } }
        val pullConn = remember(thresholdPx) { PullRefreshConnection(thresholdPx) { vm.refresh() } }
        LaunchedEffect(refreshing) { if (!refreshing) pullConn.reset() }

        Box(modifier = Modifier.fillMaxSize().nestedScroll(pullConn)) {
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
            if (pullConn.overPull > 2f) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            IntOffset(
                                0,
                                (pullConn.overPull - indicatorSizePx).roundToInt().coerceAtLeast(0)
                            )
                        }
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp).size(28.dp),
                        strokeWidth = 3.dp
                    )
                }
            }
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
