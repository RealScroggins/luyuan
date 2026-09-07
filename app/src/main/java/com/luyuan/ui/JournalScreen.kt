package com.luyuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.luyuan.domain.Note
import com.luyuan.platform.StorageLocator
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/** 与 PC 端面板完全一致的表情库（app.js EMOJIS） */
private val EMOJIS = ("😀 😄 😅 😂 🤣 😊 😍 😘 😜 🤪 🤔 🤨 😐 😴 🤤 😪 🥱 😏 😒 🥺 😢 😭 😤 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤭 🤫 🤥 😶 😬 🙄 😯 😦 😧 😮 😲 🥳 😵 🤢 🤮 🤕 🤒 🤧 😷 🥴 😈 👿 💀 👻 👽 🤖 💩 😺 🙈 🙉 🐶 🐱 🐭 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐔 🐧 🦄 🐝 🦋 🌸 🌹 🌻 🌈 ⭐ 🌙 ☀️ ⛅ 🌧️ ❄️ 🔥 💧 🎉 🎊 🎁 🎂 🍀 🌰 🍕 🍔 🍜 🍚 🍣 🍰 🍦 ☕ 🍺 🍷 💪 👍 👎 👏 🙏 ❤️ 💔 💯 ⚡ 🎵 🎮 💤 💰 📚 ✏️ 🏃 🚴").split(" ")

private fun journalParseDay(created: String): LocalDate? = try {
    OffsetDateTime.parse(created).toLocalDate()
} catch (_: Exception) {
    try { LocalDateTime.parse(created).toLocalDate() } catch (_: Exception) { null }
}

private fun journalDayLabel(d: LocalDate): String {
    val dow = when (d.dayOfWeek.value) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日"
    }
    return "${d.monthValue}月${d.dayOfMonth}日 周$dow"
}

/** 负一屏 · 日记：一天一篇（tags=["日记"]，与 PC 端统一），键盘/语音/表情/配图 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(vm: LuyuanViewModel, onRecord: () -> Unit) {
    val todayDiary by vm.todayDiary.collectAsStateWithLifecycle()
    val diaries by vm.diaries.collectAsStateWithLifecycle()
    val enabled by vm.journalEnabled.collectAsStateWithLifecycle()
    val time by vm.journalTime.collectAsStateWithLifecycle()
    var showTime by remember { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var diaryValue by remember { mutableStateOf(TextFieldValue("")) }
    var dirty by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(todayDiary) {
        if (!dirty) diaryValue = TextFieldValue(todayDiary?.text ?: "")
    }

    // 连续天数数据源 = 全部日记（此前误用主列表 notes——它天生排除日记，火苗永远数不出昨天）
    val streak = remember(diaries) {
        val diaryDays = diaries.mapNotNull { journalParseDay(it.created_at) }.toSet()
        var s = 0
        var d = LocalDate.now()
        while (d in diaryDays) {
            s++
            d = d.minusDays(1)
        }
        s
    }

    val pastDiaries = remember(diaries) {
        val today = LocalDate.now()
        diaries.filter { journalParseDay(it.created_at) != today }
    }
    var viewingDiary by remember { mutableStateOf<Note?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) vm.addDiaryImage(uri) }
    var viewer by remember { mutableStateOf<String?>(null) }

    fun insertEmoji(e: String) {
        val s = diaryValue.selection.start.coerceIn(0, diaryValue.text.length)
        val newText = diaryValue.text.substring(0, s) + e + diaryValue.text.substring(s)
        diaryValue = TextFieldValue(newText, TextRange(s + e.length))
        dirty = true
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
                        value = diaryValue,
                        onValueChange = { diaryValue = it; dirty = true },
                        placeholder = { Text("今天想记录点什么？支持换行") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        minLines = 4,
                        maxLines = 12
                    )
                    // 表情 + 配图入口（点「表情」弹出全量表情面板，与 PC 端一致）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        TextButton(onClick = { showEmoji = true }) { Text("😊 表情") }
                        TextButton(onClick = { pickImage.launch("image/*") }) { Text("＋ 配图") }
                        Text(
                            "配图存共享 images/，电脑端可见",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // 配图（与 PC 端 images/ 共享）
                    val imgs = todayDiary?.images ?: emptyList()
                    if (imgs.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            for (rel in imgs) {
                                AsyncImage(
                                    model = File(StorageLocator.getRoot(context), rel),
                                    contentDescription = "日记配图",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { viewer = rel }
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Button(
                            onClick = {
                                vm.saveDiary(diaryValue.text)
                                dirty = false
                            },
                            enabled = dirty && diaryValue.text.isNotBlank()
                        ) { Text(if (todayDiary == null) "保存日记" else "更新日记") }
                        Text(
                            if (todayDiary == null) "今天还没写" else "已有 ${todayDiary!!.text.length} 字",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(start = 10.dp)
                        )
                        Button(
                            onClick = {
                                vm.startWavRecording(diary = true)
                                onRecord()
                            },
                            modifier = Modifier.size(width = 96.dp, height = 40.dp)
                        ) {
                            Text("🎤 说一段")
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Text(
                            "📷 配图存进共享 images/ 目录，电脑端同步可见",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { pickImage.launch("image/*") }) { Text("＋ 配图") }
                    }
                }
            }
            // ---------- 之前的日记（含电脑端写的，同步过来就能看） ----------
            if (pastDiaries.isNotEmpty()) {
                Text(
                    "之前的日记",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
                for (d in pastDiaries.take(30)) {
                    val day = journalParseDay(d.created_at)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewingDiary = d }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    day?.let { journalDayLabel(it) } ?: d.created_at.take(10),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (d.images.isNotEmpty()) {
                                    Text(
                                        "  🖼${d.images.size}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                d.text.replace("\n", " ").take(60) + if (d.text.length > 60) "…" else "",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
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

    if (showEmoji) {
        val stickerLoader = remember(context) {
            coil.ImageLoader.Builder(context)
                .components { add(coil.decode.SvgDecoder.Factory()) }
                .build()
        }
        var stickerTab by remember { mutableStateOf(0) }
        val stickerMsg by vm.stickerMsg.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showEmoji = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("插入表情", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showEmoji = false }) { Text("关闭") }
                }
            },
            confirmButton = {},
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { stickerTab = 0 }) {
                            Text(
                                "Emoji",
                                fontWeight = if (stickerTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (stickerTab == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { stickerTab = 1 }) {
                            Text(
                                "贴纸",
                                fontWeight = if (stickerTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (stickerTab == 1) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (stickerTab == 0) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(8),
                            modifier = Modifier.fillMaxWidth().height(300.dp)
                        ) {
                            items(EMOJIS) { e ->
                                Text(
                                    e,
                                    fontSize = 24.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier
                                        .clickable { insertEmoji(e) }
                                        .padding(4.dp)
                                )
                            }
                        }
                    } else {
                        var stickerPack by remember { mutableStateOf(0) }
                        val pack = com.luyuan.data.StickerCatalog.packs[stickerPack]
                        val stickersDir = java.io.File(
                            StorageLocator.getRoot(context), "images/stickers"
                        )
                        if (stickerMsg.isNotBlank()) {
                            Text(
                                stickerMsg,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for ((i, p) in com.luyuan.data.StickerCatalog.packs.withIndex()) {
                                TextButton(onClick = { stickerPack = i }) {
                                    Text(
                                        p.label,
                                        fontWeight = if (stickerPack == i) FontWeight.Bold else FontWeight.Normal,
                                        color = if (stickerPack == i) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            modifier = Modifier.fillMaxWidth().height(260.dp)
                        ) {
                            items(pack.stickers, key = { it }) { name ->
                                val cached = java.io.File(stickersDir, "${pack.id}_${name}.svg")
                                val model: Any =
                                    if (cached.exists()) cached
                                    else "https://api.iconify.design/${pack.id}/${name}.svg"
                                AsyncImage(
                                    model = model,
                                    imageLoader = stickerLoader,
                                    contentDescription = name,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .padding(4.dp)
                                        .clickable { vm.insertSticker(pack.id, name) }
                                )
                            }
                        }
                        Text(
                            "点贴纸自动下载并加入今天的配图（首次需联网，之后离线可用）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        )
    }

    viewer?.let { rel ->
        val imgFile = File(StorageLocator.getRoot(context), rel)
        AlertDialog(
            onDismissRequest = { viewer = null },
            title = { Text("配图", fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeDiaryImage(rel)
                    viewer = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { viewer = null }) { Text("关闭") } },
            text = {
                AsyncImage(
                    model = imgFile,
                    contentDescription = "配图大图",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    viewingDiary?.let { d ->
        AlertDialog(
            onDismissRequest = { viewingDiary = null },
            title = {
                val day = journalParseDay(d.created_at)
                Text(
                    day?.let { journalDayLabel(it) } ?: d.created_at.take(10),
                    fontWeight = FontWeight.Bold
                )
            },
            confirmButton = { TextButton(onClick = { viewingDiary = null }) { Text("关闭") } },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(d.text, style = MaterialTheme.typography.bodyMedium)
                    if (d.images.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            for (rel in d.images) {
                                AsyncImage(
                                    model = File(StorageLocator.getRoot(context), rel),
                                    contentDescription = "日记配图",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(10.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}
