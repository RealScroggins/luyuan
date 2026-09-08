package com.luyuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luyuan.data.Course
import com.luyuan.ui.CountUpText
import com.luyuan.ui.pressScale
import kotlin.math.roundToInt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * 课程页（手机 UI 方案 A · B1 只读）：下一节深绿卡 + 周条 + 当日课列表 + 全周摘要。
 * 数据 = SYNC_FORMAT v2 kind:"course"（PC 端课表导入产出）。周次（weeks）过滤 B1 不做（依赖 PC 的学期起点配置）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseScreen(vm: LuyuanViewModel, onAsk: () -> Unit, onTrash: () -> Unit) {
    val courses by vm.courses.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var selectedDay by remember { mutableStateOf(today.dayOfWeek.value) } // 1=周一…7=周日

    val dayCourses = remember(courses, selectedDay) { courses.filter { it.weekday == selectedDay } }
    val monday = remember(today) { today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val next = remember(courses) { nextCourse(courses) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("课程", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "周" + dayShort(selectedDay) + " · ${dayCourses.size}节",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Text("🤖", fontSize = 18.sp, modifier = Modifier
                        .clickable { onAsk() }
                        .padding(horizontal = 10.dp))
                    Text("🗑", fontSize = 17.sp, modifier = Modifier
                        .clickable { onTrash() }
                        .padding(horizontal = 10.dp))
                }
            )
        }
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 14.dp)
        ) {
            if (courses.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)
                    ) {
                        Text("📅", fontSize = 40.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "还没有课程表\n电脑端导入课表截图后，会自动同步到这里",
                            fontSize = 13.sp,
                            color = LuyuanColors.Ink3,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // 下一节深绿卡
            if (next != null) {
                val (c, mins) = next
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(LuyuanColors.GradGreenStart, LuyuanColors.GradGreenEnd)
                                ),
                                RoundedCornerShape(22.dp)
                            )
                            .padding(18.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "⏰ 下一节 · " + c.start,
                                fontSize = 11.sp,
                                color = Color(0xFFCFE0D6),
                                modifier = Modifier
                                    .background(Color(0x33FFFFFF), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(c.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                coursePlaceLine(c),
                                fontSize = 12.sp,
                                color = Color(0xFFCFE0D6)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CountUpText(
                                target = mins.toDouble(),
                                format = { it.roundToInt().toString() },
                                style = androidx.compose.ui.text.TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text("分钟后上课", fontSize = 11.sp, color = Color(0xFFCFE0D6))
                        }
                    }
                }
            }

            // 周条（一~日 7 格，今天默认选中）
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 1..7) {
                        val d = monday.plusDays((i - 1).toLong())
                        val isToday = d == today
                        val selected = i == selectedDay
                        val daySrc = remember { MutableInteractionSource() }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(interactionSource = daySrc, indication = null, onClick = { selectedDay = i })
                                .background(
                                    when {
                                        selected -> MaterialTheme.colorScheme.primary
                                        isToday -> LuyuanColors.Green100
                                        else -> MaterialTheme.colorScheme.surface
                                    },
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(vertical = 8.dp)
                                .pressScale(daySrc)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    dayShort(i),
                                    fontSize = 10.sp,
                                    color = if (selected) Color(0xFFCFE0D6) else LuyuanColors.Ink3
                                )
                                Text(
                                    "${d.dayOfMonth}",
                                    fontSize = 15.sp,
                                    fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else LuyuanColors.Ink1
                                )
                            }
                        }
                    }
                }
            }

            // 当日课列表（标题跟随选中日）
            val selDate = monday.plusDays((selectedDay - 1).toLong())
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        (if (selectedDay == today.dayOfWeek.value) "今天的课" else "${selDate.monthValue}月${selDate.dayOfMonth}日") +
                            " 周" + dayShort(selectedDay),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("${dayCourses.size}节", fontSize = 11.sp, color = LuyuanColors.Ink3)
                }
            }
            if (dayCourses.isEmpty()) {
                item {
                    Text(
                        "这天没有课",
                        fontSize = 12.sp,
                        color = LuyuanColors.Ink3,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            for (c in dayCourses) {
                item(key = c.id) {
                    CourseRow(c, showStatus = selectedDay == today.dayOfWeek.value)
                }
            }

            // 全周摘要
            if (courses.isNotEmpty()) {
                item {
                    Text("全周课程", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                for (i in 1..7) {
                    val list = courses.filter { it.weekday == i }
                    if (list.isEmpty()) continue
                    item(key = "wk_$i") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                "周" + dayShort(i) + " · ${list.size}节",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            for (c in list) {
                                Text(
                                    c.start + " " + c.name + " · " + coursePlaceLine(c),
                                    fontSize = 12.sp,
                                    color = LuyuanColors.Ink2,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseRow(c: Course, showStatus: Boolean) {
    val now = remember { LocalTime.now() }
    val st = parseHm(c.end)?.let { if (it.isBefore(now)) "已下课" else null }
    val ongoing = if (showStatus && st == null) {
        val s = parseHm(c.start)
        val e = parseHm(c.end)
        s != null && e != null && !s.isAfter(now) && e.isAfter(now)
    } else false

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ongoing) LuyuanColors.Green50 else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (ongoing) androidx.compose.foundation.BorderStroke(1.dp, LuyuanColors.Green500) else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(46.dp)) {
                Text(c.start, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(c.end, fontSize = 10.sp, color = LuyuanColors.Ink3)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name.ifBlank { "（未命名课）" }, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text(coursePlaceLine(c), fontSize = 11.sp, color = LuyuanColors.Ink3, maxLines = 1)
            }
            if (showStatus) {
                val label = if (ongoing) "进行中" else st
                if (label != null) {
                    Text(
                        label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (ongoing) LuyuanColors.Green700 else LuyuanColors.Ink3,
                        modifier = Modifier
                            .background(
                                if (ongoing) LuyuanColors.Green100 else Color(0xFFF6F3EB),
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

// ---------- 纯函数 ----------

internal fun parseHm(s: String): LocalTime? = try {
    LocalTime.parse(s)
} catch (_: Exception) {
    null
}

internal fun dayShort(i: Int): String =
    DayOfWeek.of(i.coerceIn(1, 7)).getDisplayName(TextStyle.SHORT, Locale.CHINA).removeSuffix("周")

/** 教室·老师（缺项自动略过） */
internal fun coursePlaceLine(c: Course): String = listOf(c.place, c.teacher)
    .filter { it.isNotBlank() }
    .joinToString(" · ")

/**
 * 下一节课：今天还没上课里找最近的；今天没了往后找一天，最多看 7 天。
 * 返回 (课, 距上课分钟)；没有课或时间解析失败返回 null。
 */
internal fun nextCourse(courses: List<Course>): Pair<Course, Int>? {
    val now = LocalTime.now()
    val today = LocalDate.now()
    for (offset in 0..6L) {
        val wd = today.plusDays(offset).dayOfWeek.value
        val sameDay = offset == 0L
        val best = courses.filter { it.weekday == wd }
            .mapNotNull { c ->
                val s = parseHm(c.start) ?: return@mapNotNull null
                // 今天已开始的课不算"下一节"
                if (sameDay && !s.isAfter(now)) null else c to s.toSecondOfDay()
            }
            .minByOrNull { it.second }
            ?: continue
        val c = best.first
        val mins = parseHm(c.start)!!.toSecondOfDay() / 60 - (now.hour * 60 + now.minute) + offset.toInt() * 1440
        return c to mins
    }
    return null
}
