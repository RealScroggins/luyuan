package com.luyuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import com.luyuan.domain.Note
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

private fun journalParseDay(created: String): LocalDate? = try {
    OffsetDateTime.parse(created).toLocalDate()
} catch (_: Exception) {
    try { LocalDateTime.parse(created).toLocalDate() } catch (_: Exception) { null }
}

/** 负一屏 · 日记：一天一篇（tags=["日记"]，与 PC 端统一），键盘/语音都能写 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(vm: LuyuanViewModel, onRecord: () -> Unit) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val todayDiary by vm.todayDiary.collectAsStateWithLifecycle()
    val enabled by vm.journalEnabled.collectAsStateWithLifecycle()
    val time by vm.journalTime.collectAsStateWithLifecycle()
    var showTime by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var diaryText by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }
    // 回到本页/同步完成后填充，正在打字时不覆盖
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(todayDiary) {
        if (!dirty) diaryText = todayDiary?.text ?: ""
    }

    val streak = remember(notes, todayDiary) {
        val diaryDays = mutableSetOf<LocalDate>()
        for (n in notes) {
            if (n.tags.contains("日记")) journalParseDay(n.created_at)?.let { diaryDays.add(it) }
        }
        todayDiary?.let { journalParseDay(it.created_at)?.let { d -> diaryDays.add(d) } }
        var s = 0
        var d = LocalDate.now()
        while (d in diaryDays) {
            s++
            d = d.minusDays(1)
        }
        s
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("日记", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("今天", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        Text(
                            if (streak > 0) "🔥 连续 $streak 天" else "从今天开始",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = diaryText,
                        onValueChange = { diaryText = it; dirty = true },
                        placeholder = { Text("今天想记录点什么？支持换行") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        minLines = 4,
                        maxLines = 12
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Button(
                            onClick = {
                                vm.saveDiary(diaryText)
                                dirty = false
                            },
                            enabled = dirty && diaryText.isNotBlank()
                        ) { Text(if (todayDiary == null) "保存日记" else "更新日记") }
                        Text(
                            if (todayDiary == null) "今天还没写" else "已有 ${todayDiary!!.text.length} 字，可继续修改",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(start = 10.dp)
                        )
                        Button(
                            onClick = {
                                vm.startRecording(diary = true)
                                onRecord()
                            },
                            modifier = Modifier.size(width = 96.dp, height = 40.dp)
                        ) {
                            Text("🎤 说一段")
                        }
                    }
                    Text(
                        "一天一篇：语音说的会自动并入今天这篇；保存后同步到电脑端日记页",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("每天提醒我记日记", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { vm.setJournalEnabled(it) })
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Text(
                            "提醒时间  %02d:%02d".format(time.first, time.second),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showTime = true }) { Text("修改") }
                    }
                }
            }
        }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(
            initialHour = time.first, initialMinute = time.second, is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("日记提醒时间") },
            confirmButton = {
                TextButton(onClick = {
                    vm.setJournalTime(timeState.hour, timeState.minute)
                    showTime = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text("取消") }
            },
            text = { TimePicker(state = timeState) }
        )
    }
}
