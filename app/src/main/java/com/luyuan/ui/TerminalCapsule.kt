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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast

/**
 * 悬浮超级终端胶囊（方案 A §二-2）：全 App 唯一录入口，悬浮在列表上方、底栏之上（路河拍板原稿样式）。
 * 安卓没有 HTML backdrop-filter 等价物，玻璃感用「高透纸白渐变 + 大阴影 + 淡描边」近似（稿子本身 96% 不透明）。
 * B1 简版：点胶囊 = 底部输入浮层（存普通笔记）；点麦克风 = 开录音。
 * B3 升级：输入浮层接意图识别（确定性规则 → PC /api/terminal 确认卡）。
 */
@Composable
fun TerminalCapsule(onQuickInput: () -> Unit, onRecord: () -> Unit, modifier: Modifier = Modifier) {
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
            .clickable { onQuickInput() }
            .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Text(
            "记一笔 / 说一句 / 问路远…",
            fontSize = 13.sp,
            color = LuyuanColors.Ink3,
            modifier = Modifier.weight(1f)
        )
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
