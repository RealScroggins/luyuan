package com.luyuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 删除撤回条（手机二期 #1，与 B2 删除族口径一致）：
 * 无确认弹窗；深绿条「message · 撤回 | 立即删除」，durationMs 倒计时后自动提交。
 * onUndo=撤回（原地恢复）；onCommit=立即删除（软删进回收站）。
 * 颜色走 LuyuanColors.Green700，禁止页内写死。
 */
@Composable
fun UndoBar(
    message: String,
    onUndo: () -> Unit,
    onCommit: () -> Unit,
    durationMs: Long = 5000,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        delay(durationMs)
        onCommit()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LuyuanColors.Green700)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "$message · 撤回",
            color = Color.White,
            fontSize = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onUndo) { Text("撤回", color = Color.White) }
            TextButton(onClick = onCommit) { Text("立即删除", color = Color(0xFFFFD9D9)) }
        }
    }
}
