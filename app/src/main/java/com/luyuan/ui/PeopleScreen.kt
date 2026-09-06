package com.luyuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 第二屏 · 人际：等 PC 端把 SYNC_FORMAT v2（kind:"contact"）契约落地后填充。
 *  数据流：主页说「向文成的生日是10月20日」→ 电脑端本地大模型抽取 → 写联系人 JSON →
 *  Syncthing 同步过来 → 本页展示联系人卡/待办置顶/字母索引。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("人际", fontWeight = FontWeight.Bold) }) }
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
                    Text("建设中 🚧", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "人际关系网正在与电脑端一起搭建：\n\n" +
                                "① 电脑端先落地联系人数据格式（SYNC_FORMAT v2）\n" +
                                "② 之后在主页说「向文成的生日是10月20日」会自动归档到这里，不再刷屏主列表\n" +
                                "③ 说「我今天要帮向文成带午饭」会在他的卡片上置顶待办并亮红点\n" +
                                "④ 卡片点开可打电话、跳微信/QQ，联系人支持搜索和字母索引\n\n" +
                                "电脑端上线后，这个页面会自动亮起来。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
