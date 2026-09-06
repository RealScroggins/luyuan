package com.luyuan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luyuan.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailEditScreen(
    vm: LuyuanViewModel,
    noteId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    var text by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }
    var remindAt by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val isRecording by vm.isRecording.collectAsStateWithLifecycle()

    LaunchedEffect(noteId) {
        withContext(Dispatchers.IO) {
            val n = NoteRepository.getNote(context, noteId)
            n?.let {
                text = it.text
                tagsText = it.tags.joinToString(", ")
                remindAt = it.remind_at
                loaded = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记事详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("内容") },
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.68f),
                singleLine = false
            )
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = { Text("标签（逗号分隔，可选）") },
                modifier = Modifier.fillMaxWidth()
            )

            // 提醒：两端互通。这里设的到点手机响，电脑同步后也能看到
            Text("提醒", style = MaterialTheme.typography.titleMedium)
            Text(
                text = remindAt?.let { "已设：${formatRemind(it)}" } ?: "未设置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {
                    val iso = isoInHours(1)
                    vm.setReminder(noteId, iso)
                    remindAt = iso
                }, label = { Text("+1小时") })
                AssistChip(onClick = {
                    val iso = isoInHours(3)
                    vm.setReminder(noteId, iso)
                    remindAt = iso
                }, label = { Text("+3小时") })
                AssistChip(onClick = {
                    val iso = isoTomorrowAt(9, 0)
                    vm.setReminder(noteId, iso)
                    remindAt = iso
                }, label = { Text("明早9点") })
                AssistChip(onClick = {
                    val iso = isoTomorrowAt(
                        LocalDateTime.now().hour, LocalDateTime.now().minute
                    )
                    vm.setReminder(noteId, iso)
                    remindAt = iso
                }, label = { Text("明天此时") })
            }
            if (remindAt != null) {
                TextButton(onClick = {
                    vm.setReminder(noteId, null)
                    remindAt = null
                }) { Text("取消提醒") }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onBack) { Text("取消") }
                TextButton(onClick = {
                    val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    vm.updateNote(noteId, text, tags)
                    onBack()
                }) { Text("保存") }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除这条记事？") },
            text = { Text("将移入回收站（软删），同步后电脑端也会移入回收站，可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteNote(noteId)
                    showDelete = false
                    onBack()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

private fun formatRemind(iso: String): String = try {
    OffsetDateTime.parse(iso).toLocalDateTime()
        .format(java.time.format.DateTimeFormatter.ofPattern("M.d HH:mm"))
} catch (_: Exception) {
    try {
        LocalDateTime.parse(iso)
            .format(java.time.format.DateTimeFormatter.ofPattern("M.d HH:mm"))
    } catch (_: Exception) {
        iso
    }
}

private fun isoInHours(h: Long): String =
    LocalDateTime.now().plusHours(h).atOffset(OffsetDateTime.now().offset).toString()

private fun isoTomorrowAt(hour: Int, minute: Int): String =
    LocalDate.now().plusDays(1).atTime(hour, minute)
        .atOffset(OffsetDateTime.now().offset).toString()
