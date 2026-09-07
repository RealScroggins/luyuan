package com.luyuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luyuan.data.AskRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 问路远（手机版 · 开发计划终稿 §3.A2）：OpenAI 兼容 API 直连对话。
 * 上下文 = 本机近期笔记 + 今日待办摘要（AskRemote 组装，私密笔记不外发）；
 * 会话只在内存；Key 在设置页维护；断网/无 Key 报错但不影响记事。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(vm: LuyuanViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages = remember { mutableStateListOf<AskRemote.Turn>() }
    var question by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun send() {
        val q = question.trim()
        if (q.isBlank() || busy) return
        val history = messages.toList()
        messages.add(AskRemote.Turn("user", q))
        question = ""
        busy = true
        error = null
        scope.launch {
            val answer = try {
                withContext(Dispatchers.IO) {
                    val cfg = AskRemote.loadConfig(context)
                    AskRemote.ask(cfg, q, history, AskRemote.buildContext(context))
                }
            } catch (e: Exception) {
                error = e.message ?: "请求失败"
                ""
            }
            busy = false
            if (answer.isNotBlank()) {
                messages.add(AskRemote.Turn("assistant", answer))
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("问路远", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        messages.clear()
                        error = null
                    }) {
                        Text("新对话", fontSize = 13.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (messages.isEmpty() && !busy) {
                Text(
                    "问点什么。我会参考你本机最近的笔记和待办来回答（私密标签的笔记不会发出去）。\n" +
                        "API Key 在「设置 → 问路远」里填。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                items(messages) { m ->
                    val isUser = m.role == "user"
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            m.content,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                                .widthIn(max = 320.dp)
                                .background(
                                    if (isUser) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(
                                        14.dp, 14.dp,
                                        if (isUser) 4.dp else 14.dp,
                                        if (isUser) 14.dp else 4.dp
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }
                if (busy) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "翻笔记中…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            error?.let {
                Text(
                    "⚠️ $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = { Text("问点什么…") },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { send() },
                    enabled = question.isNotBlank() && !busy,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("发送")
                }
            }
        }
    }
}
