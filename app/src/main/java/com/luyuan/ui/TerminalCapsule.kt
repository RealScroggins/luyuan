package com.luyuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast

/**
 * 悬浮超级终端胶囊（方案 A §二-2 · 路河拍板迭代）：一框四用，**就在胶囊里输入**（不弹浮层）。
 * - 普通态：点主体 → 输入态（就地打字，回车/✅保存，草稿实时缓存）
 * - 🔍 = 搜索态（实时过滤笔记）；🎙 = 录音
 * - 玻璃感用高透纸白渐变+大阴影近似（安卓无 backdrop-filter 等价物）。
 */
@Composable
fun TerminalCapsule(
    searchMode: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    inputMode: Boolean,
    inputValue: String,
    onInputValueChange: (String) -> Unit,
    onToggleInput: () -> Unit,
    onCommitInput: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusReq = remember { FocusRequester() }
    LaunchedEffect(searchMode || inputMode) {
        if (searchMode || inputMode) {
            kotlinx.coroutines.delay(120)
            focusReq.requestFocus()
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(999.dp), clip = false)
            .background(
                Brush.horizontalGradient(listOf(Color(0xF5FAF7F0), Color(0xE9FAF7F0))),
                RoundedCornerShape(999.dp)
            )
            .border(1.dp, Color(0x1A224A3A), RoundedCornerShape(999.dp))
            .padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
    ) {
        val editing = inputMode || searchMode
        if (inputMode) {
            // 输入态：胶囊本体即输入框（路河拍板：不要额外浮层）
            BasicTextField(
                value = inputValue,
                onValueChange = onInputValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (inputValue.isNotBlank()) onCommitInput() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth()) {
                        if (inputValue.isEmpty()) {
                            Text("记一笔，回车保存…", fontSize = 14.sp, color = LuyuanColors.Ink3)
                        }
                        inner
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusReq)
                    .padding(vertical = 12.dp)
            )
        } else if (searchMode) {
            // 搜索态：实时过滤笔记（点 ✕ 退出并清空）
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text("搜索笔记…", fontSize = 14.sp, color = LuyuanColors.Ink3)
                        }
                        inner
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusReq)
                    .padding(vertical = 12.dp)
            )
        } else {
            Text(
                "记一笔 / 说一句 / 问路远…",
                fontSize = 13.sp,
                color = LuyuanColors.Ink3,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggleInput() }
            )
        }
        when {
            inputMode -> {
                // ✅ 保存
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { if (inputValue.isNotBlank()) onCommitInput() }
                ) { Text("✓", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(6.dp))
                // ✕ 退出输入态
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .background(LuyuanColors.Green100, CircleShape)
                        .clickable { onToggleInput() }
                ) { Text("✕", fontSize = 14.sp, color = LuyuanColors.Ink2) }
            }
            searchMode -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .background(LuyuanColors.Green100, CircleShape)
                        .clickable { onToggleSearch() }
                ) { Text("✕", fontSize = 15.sp) }
            }
            else -> {
                // 🔍 搜索
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .background(LuyuanColors.Green100, CircleShape)
                        .clickable { onToggleSearch() }
                ) { Text("🔍", fontSize = 15.sp) }
                Spacer(Modifier.width(6.dp))
                // 🎙 录音
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { onRecord() }
                ) { Text("🎙", fontSize = 15.sp) }
            }
        }
    }
}

/** 底部快速输入浮层（B1 简版：直接存普通笔记；意图识别 B3 接 /api/terminal 后切换） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickInputSheet(vm: LuyuanViewModel, onDismiss: () -> Unit) {
    // 草稿实时缓存（任务单 P0-2）：关浮层/切走/杀进程都不丢，保存成功即清
    val context = LocalContext.current
    val draftPrefs = remember { context.getSharedPreferences("luyuan_prefs", android.content.Context.MODE_PRIVATE) }
    var text by remember { mutableStateOf(draftPrefs.getString("terminal_draft", "") ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("记一笔", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    draftPrefs.edit().putString("terminal_draft", it).apply()
                },
                placeholder = { Text("想到什么写什么，保存后自动同步电脑", color = LuyuanColors.Ink3) },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val t = text.trim()
                    if (t.isNotBlank()) {
                        vm.addManual(t)
                        draftPrefs.edit().remove("terminal_draft").apply()
                        Toast.makeText(context, "✅ 已存为笔记", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }
            Spacer(Modifier.height(6.dp))
            Text(
                "输入即分类（待办 / 记账 / 建联系人）即将上线",
                fontSize = 10.5.sp,
                color = LuyuanColors.Ink3,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
