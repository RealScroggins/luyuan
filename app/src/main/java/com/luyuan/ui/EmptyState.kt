package com.luyuan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 通用空状态组件（手机二期 #8）。
 * 图标一律走 Material 矢量，严禁 emoji 当图标（对照 v2/icons.html 登记）。
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = LuyuanColors.Green500,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(vertical = 44.dp, horizontal = 28.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(46.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = LuyuanColors.Ink1)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, fontSize = 13.sp, color = LuyuanColors.Ink3, lineHeight = 20.sp)
        }
    }
}

// 六场面图标速查（Material 矢量，禁 emoji；与 v2/icons.html 登记一致）
val EmptyIconNote = Icons.Default.NoteAdd
val EmptyIconSearch = Icons.Default.SearchOff
val EmptyIconLedger = Icons.Default.AccountBalanceWallet
val EmptyIconCourse = Icons.Default.CalendarMonth
val EmptyIconTrash = Icons.Default.DeleteSweep
val EmptyIconPeople = Icons.Default.People
