package com.luyuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luyuan.domain.Note

/**
 * 回收站（方案 A §五-6）：灰提示条 + 常驻勾选框行列表 + 批量条（已选 N · 全选/恢复/彻底删除）。
 * UI逻辑规则 §4：独立页 + 批量恢复 + 批量彻底删除；彻底删除=物理删，保留确认弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(vm: LuyuanViewModel, onBack: () -> Unit) {
    val trash by vm.trash.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var purgeBatch by remember { mutableStateOf(false) }
    var purgeTarget by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(Unit) { vm.refreshTrash() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = { Text("回收站", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "已选 ${selected.size}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = {
                        selected =
                            if (selected.size == trash.size) emptySet() else trash.map { it.id }.toSet()
                    }) { Text("全选") }
                    Button(
                        onClick = {
                            vm.restoreNotes(selected)
                            selected = emptySet()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) { Text("恢复") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { purgeBatch = true }) {
                        Text("彻底删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "删除的东西在这里躺 30 天，可恢复。恢复后经 Syncthing 同步，电脑端也会重新显示。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
            if (trash.isEmpty()) {
                Text(
                    "回收站是空的",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(40.dp)
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
            ) {
                items(trash, key = { it.id }) { note ->
                    TrashCard(
                        note = note,
                        checked = note.id in selected,
                        onCheck = {
                            selected =
                                if (note.id in selected) selected - note.id else selected + note.id
                        },
                        onRestore = { vm.restoreNote(note.id) },
                        onPurge = { purgeTarget = note }
                    )
                }
            }
        }
    }

    // 批量彻底删除确认（物理删，必须确认）
    if (purgeBatch) {
        AlertDialog(
            onDismissRequest = { purgeBatch = false },
            title = { Text("彻底删除 ${selected.size} 条？") },
            text = { Text("将永久删除选中内容，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.purgeNotes(selected)
                    selected = emptySet()
                    purgeBatch = false
                }) { Text("彻底删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { purgeBatch = false }) { Text("取消") }
            }
        )
    }

    // 单条彻底删除确认
    purgeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { purgeTarget = null },
            title = { Text("彻底删除？") },
            text = { Text("「${target.text.take(20)}…」将被永久删除，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.purgeNote(target.id)
                    purgeTarget = null
                }) { Text("彻底删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { purgeTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TrashCard(
    note: Note,
    checked: Boolean,
    onCheck: () -> Unit,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onCheck() }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = note.text.ifBlank { "（空）" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp
                )
                Text(
                    text = note.created_at.take(16).replace("T", " ") + " 删除",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Column(modifier = Modifier.padding(end = 6.dp)) {
                TextButton(onClick = onRestore) {
                    Text("恢复", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onPurge) {
                    Text("彻底删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
