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
 * 悬浮超级终端胶囊（方案 A §二-2 · 路河拍板：替代主页常驻输入框与搜索框）：
 * - 胶囊主体点击 = 快速输入浮层；🔍 = 搜索态（胶囊变搜索框，实时过滤笔记列表）；🎙 = 录音。
 * - 悬浮在列表上方、底栏之上；玻璃感用高透纸白渐变+大阴影近似（安卓无 backdrop-filter 等价物）。
 * - B3：输入浮层接意图识别（确定性规则 → PC /api/terminal 确认卡）。
 */
@Composable
fun TerminalCapsule(
    searchMode: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onQuickInput: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchMode) {
        if (searchMode) {
            kotlinx.coroutines.delay(120) // 等胶囊形变稳定再拉键盘焦点
            searchFocus.requestFocus()
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
        if (searchMode) {
            // 搜索态：胶囊本体即搜索框，输入实时过滤（点 ✕ 退出并清空）
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
                    .focusRequester(searchFocus)
                    .padding(vertical = 12.dp)
            )
        } else {
            Text(
                "记一笔 / 说一句 / 问路远…",
                fontSize = 13.sp,
                color = LuyuanColors.Ink3,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onQuickInput() }
            )
        }
        // 搜索 / 退出搜索
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .background(LuyuanColors.Green100, CircleShape)
                .clickable { onToggleSearch() }
        ) {
            Text(if (searchMode) "✕" else "🔍", fontSize = 15.sp)
        }
        Spacer(Modifier.width(6.dp))
        // 录音（搜索态隐藏防误触）
        if (!searchMode) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { onRecord() }
            ) {
                Text("🎙", fontSize = 15.sp)
            }
        }
    }
}

/** 底部快速输入浮层（B1 简版：直接存普通笔记；意图识别 B3 接 /api/terminal 后切换） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickInputSheet(vm: LuyuanViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

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
                onValueChange = { text = it },
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
