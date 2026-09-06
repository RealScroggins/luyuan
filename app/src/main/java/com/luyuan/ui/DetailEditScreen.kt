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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    var audioRel by remember { mutableStateOf<String?>(null) }
    var rawText by remember { mutableStateOf<String?>(null) }
    var showRaw by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<java.time.LocalDate?>(null) }

    val isRecording by vm.isRecording.collectAsStateWithLifecycle()

    LaunchedEffect(noteId) {
        withContext(Dispatchers.IO) {
            val n = NoteRepository.getNote(context, noteId)
            n?.let {
                text = it.text
                tagsText = it.tags.joinToString(", ")
                remindAt = it.remind_at
                audioRel = it.audio
                rawText = it.raw_text
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

            // 原声回放：录音待转写/离线识别的笔记可直接听（audio/<id>.wav 在共享目录）
            audioRel?.let { rel ->
                val f = remember(rel) {
                    java.io.File(com.luyuan.platform.StorageLocator.getRoot(context), rel)
                }
                if (f.exists()) {
                    var playing by remember(rel) { mutableStateOf(false) }
                    val player = remember(rel) {
                        android.media.MediaPlayer().apply {
                            setOnCompletionListener { playing = false }
                        }
                    }
                    DisposableEffect(rel) {
                        onDispose {
                            try { if (player.isPlaying) player.stop() } catch (_: Exception) {}
                            try { player.release() } catch (_: Exception) {}
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = {
                                try {
                                    if (playing) {
                                        player.pause()
                                        playing = false
                                    } else {
                                        if (player.isPlaying) {
                                            player.start()
                                        } else {
                                            player.reset()
                                            player.setDataSource(f.absolutePath)
                                            player.prepare()
                                            player.start()
                                        }
                                        playing = true
                                    }
                                } catch (_: Exception) {
                                    playing = false
                                }
                            },
                            label = { Text(if (playing) "⏸ 暂停原声" else "▶ 播放原声") }
                        )
                    }
                } else {
                    Text(
                        "🎙 这条带原声录音（音频还没同步到手机）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 原始识别留底：电脑纠错前的原稿，转写不准时可对照修正
            rawText?.takeIf { it.isNotBlank() && it != text }?.let { raw ->
                AssistChip(
                    onClick = { showRaw = !showRaw },
                    label = { Text(if (showRaw) "🙈 收起原始识别" else "📜 看原始识别") }
                )
                if (showRaw) {
                    Text(
                        raw,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

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
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {
                    val now = LocalDateTime.now()
                    val iso = isoTomorrowAt(now.hour, now.minute)
                    vm.setReminder(noteId, iso)
                    remindAt = iso
                }, label = { Text("明天此时") })
                AssistChip(onClick = { showDatePicker = true }, label = { Text("自定义时间…") })
                if (remindAt != null) {
                    AssistChip(onClick = {
                        vm.setReminder(noteId, null)
                        remindAt = null
                    }, label = { Text("取消提醒") })
                }
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

    // 自定义提醒：日历选日期 → 拨盘选时间
    if (showDatePicker) {
        val dateState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms ->
                        pickedDate = java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        showDatePicker = false
                        showTimePicker = true
                    }
                }) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            androidx.compose.material3.DatePicker(state = dateState)
        }
    }
    if (showTimePicker) {
        val timeState = androidx.compose.material3.rememberTimePickerState(
            initialHour = 9, initialMinute = 0, is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选时间") },
            confirmButton = {
                TextButton(onClick = {
                    val d = pickedDate
                    if (d != null) {
                        val iso = d.atTime(timeState.hour, timeState.minute)
                            .atOffset(OffsetDateTime.now().offset).toString()
                        vm.setReminder(noteId, iso)
                        remindAt = iso
                    }
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            text = { androidx.compose.material3.TimePicker(state = timeState) }
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
