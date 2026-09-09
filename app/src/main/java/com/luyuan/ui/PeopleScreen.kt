package com.luyuan.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luyuan.data.Contact
import com.luyuan.data.ContactTodo
import kotlinx.coroutines.launch

/** 第二屏 · 人脉：待办置顶红板 + 联系人字母索引 + 搜索，数据来自共享目录 contacts/ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(vm: LuyuanViewModel) {
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<Contact?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.refresh() }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val todoContacts = filtered.filter { it.undoneTodos.isNotEmpty() }
    val grouped = remember(filtered) { filtered.groupBy { it.displayLetter }.toSortedMap() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("人脉", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索联系人…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            )
            if (filtered.isEmpty()) {
                EmptyState(
                    icon = if (contacts.isEmpty()) EmptyIconPeople else EmptyIconSearch,
                    title = if (contacts.isEmpty()) "还没有联系人" else "没有匹配「${query.trim()}」的联系人",
                    subtitle = if (contacts.isEmpty())
                        "电脑端人脉页添加后会自动同步到这里（共享目录 contacts/ 文件夹）。"
                    else null
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                ) {
                    // ---------- 待办板块（有未办事项的联系人自动置顶 + 红点） ----------
                    if (todoContacts.isNotEmpty()) {
                        item(key = "sec_todo") {
                            Text(
                                "待办",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF374151),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }
                        for (c in todoContacts) {
                            item(key = "todo_${c.id}") {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth().clickable { detail = c }
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .background(Color(0xFFEF4444), CircleShape)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                        for (t in c.undoneTodos) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Checkbox(
                                                    checked = false,
                                                    onCheckedChange = { vm.toggleContactTodo(c.id, t.id) }
                                                )
                                                Text(
                                                    t.text,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.clickable { vm.toggleContactTodo(c.id, t.id) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // ---------- 联系人板块（按字母分组） ----------
                    item(key = "sec_all") {
                        Text(
                            "联系人 ${filtered.size}",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF374151),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                        )
                    }
                    for ((letter, list) in grouped) {
                        item(key = "letter_$letter") {
                            Text(
                                letter,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        for (c in list) {
                            item(key = "c_${c.id}") {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth().clickable { detail = c }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            if (c.birthday.isNotBlank() || c.phone.isNotBlank()) {
                                                Text(
                                                    listOfNotNull(
                                                        c.phone.ifBlank { null },
                                                        c.birthday.takeIf { it.isNotBlank() }?.let { "🎂 $it" }
                                                    ).joinToString("  "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (c.undoneTodos.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .background(Color(0xFFEF4444), CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "bottom_pad") { Spacer(Modifier.height(10.dp)) }
                }
                // ---------- 字母索引条 ----------
                if (grouped.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(28.dp)
                            .background(Color(0x22000000), RoundedCornerShape(14.dp))
                            .padding(vertical = 10.dp)
                    ) {
                        for (letter in grouped.keys) {
                            Text(
                                letter,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        // 定位到字母分组头：待办段 + 全部标题 + 前面各组(1头+人数)
                                        var headerIdx =
                                            (if (todoContacts.isNotEmpty()) 1 else 0) + todoContacts.size + 1
                                        for ((l, list) in grouped) {
                                            if (l == letter) break
                                            headerIdx += 1 + list.size
                                        }
                                        scope.launch {
                                            val max = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                            listState.scrollToItem(headerIdx.coerceIn(0, max))
                                        }
                                    }
                                    .padding(vertical = 1.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    detail?.let { picked ->
        // contacts 刷新后跟随最新数据（贪睡/勾选后弹窗内即时反映）
        val c = contacts.firstOrNull { it.id == picked.id } ?: picked
        ContactDetailDialog(
            contact = c,
            onDismiss = { detail = null },
            onToggleTodo = { todoId -> vm.toggleContactTodo(c.id, todoId) },
            onSnoozeTodo = { todoId, hours -> vm.snoozeContactTodo(c.id, todoId, hours) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactDetailDialog(
    contact: Contact,
    onDismiss: () -> Unit,
    onToggleTodo: (String) -> Unit,
    onSnoozeTodo: (String, Int?) -> Unit
) {
    val context = LocalContext.current
    val clipboard: ClipboardManager = LocalClipboardManager.current
    var snoozeTarget by remember { mutableStateOf<ContactTodo?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(contact.name, fontWeight = FontWeight.Bold) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 分享名片：拼成纯文本走系统分享——对方不需要装路远，微信/QQ/短信都能收
                TextButton(onClick = {
                    val lines = mutableListOf("【${contact.name}】")
                    if (contact.phone.isNotBlank()) lines.add("电话：${contact.phone}")
                    if (contact.wechat.isNotBlank()) lines.add("微信：${contact.wechat}")
                    if (contact.qq.isNotBlank()) lines.add("QQ：${contact.qq}")
                    if (contact.birthday.isNotBlank()) lines.add("生日：${contact.birthday}")
                    for (line in contact.info) lines.add("· $line")
                    try {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n"))
                                },
                                "分享联系人"
                            )
                        )
                    } catch (_: Exception) {
                    }
                }) { Text("📤 分享名片（文字形式，谁都能看）") }
                InfoRow("电话", contact.phone) {
                    if (contact.phone.isNotBlank()) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "拨号",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable {
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + contact.phone))
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                        )
                    }
                }
                InfoRow("微信", contact.wechat) {
                    if (contact.wechat.isNotBlank()) {
                        Text(
                            "复制并打开",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                clipboard.setText(AnnotatedString(contact.wechat))
                                val launch = context.packageManager.getLaunchIntentForPackage("com.tencent.mm")
                                if (launch != null) {
                                    context.startActivity(launch)
                                    Toast.makeText(context, "微信号已复制，去微信粘贴搜索", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "微信号已复制（未找到微信）", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }
                InfoRow("QQ", contact.qq) {
                    if (contact.qq.isNotBlank()) {
                        Text(
                            "去聊天",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("mqq://im/chat?chat_type=uin&uin=" + contact.qq))
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, "没有装 QQ 或无法跳转", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
                if (contact.birthday.isNotBlank()) InfoRow("生日", contact.birthday) {}
                for (line in contact.info) {
                    Text("· $line", style = MaterialTheme.typography.bodyMedium)
                }
                if (contact.todos.isNotEmpty()) {
                    HorizontalDivider()
                    Text("待办", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    for (t in contact.todos.sortedBy { it.done }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = t.done,
                                onCheckedChange = { onToggleTodo(t.id) }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.text,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = if (t.done) MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ) else MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { if (!t.done) snoozeTarget = t }
                                    )
                                )
                                if (!t.done && !t.remind_at.isNullOrBlank()) {
                                    Text(
                                        "⏰ " + (t.remind_at ?: "").take(16).replace('T', ' '),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!t.done) {
                                Text(
                                    "贪睡",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { snoozeTarget = t }
                                        .padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    )

    // 贪睡弹窗（v1.11）：推后 1/3 小时或取消提醒
    snoozeTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { snoozeTarget = null },
            title = { Text("提醒设置", fontWeight = FontWeight.Bold) },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { snoozeTarget = null }) { Text("算了") }
            },
            text = {
                Column {
                    Text(
                        "「${t.text.take(14)}${if (t.text.length > 14) "…" else ""}」",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { onSnoozeTodo(t.id, 1); snoozeTarget = null }) {
                        Text("⏰ 1 小时后提醒")
                    }
                    TextButton(onClick = { onSnoozeTodo(t.id, 3); snoozeTarget = null }) {
                        Text("⏰ 3 小时后提醒")
                    }
                    TextButton(onClick = { onSnoozeTodo(t.id, null); snoozeTarget = null }) {
                        Text("🚫 取消提醒")
                    }
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, trailing: @Composable () -> Unit) {
    if (value.isBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.width(42.dp)
        )
        Text(value, modifier = Modifier.weight(1f))
        trailing()
    }
}
