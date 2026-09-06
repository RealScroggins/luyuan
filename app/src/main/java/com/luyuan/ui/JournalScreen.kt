package com.luyuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** 负一屏 · 日记：今天记了几条 + 一键开录 + 每天固定时间提醒 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(vm: LuyuanViewModel, onRecord: () -> Unit) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val enabled by vm.journalEnabled.collectAsStateWithLifecycle()
    val time by vm.journalTime.collectAsStateWithLifecycle()
    var showTime by remember { mutableStateOf(false) }

    val todayCount = remember(notes) {
        val today = LocalDate.now()
        notes.count { journalParseDay(it.created_at) == today }
    }
    val streak = remember(notes) {
        var s = 0
        var d = LocalDate.now()
        val days = notes.mapNotNull { journalParseDay(it.created_at) }.toSet()
        while (d in days) {
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
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("今天", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (todayCount > 0) "已记 $todayCount 条" else "还没记，说两句？",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Button(onClick = onRecord) { Text("🎤 记今天一笔") }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("连续记录", fontWeight = FontWeight.Bold)
                    Text(
                        if (streak > 0) "🔥 已连续 $streak 天" else "从今天开始连续记录吧",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
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
