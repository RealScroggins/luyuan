package com.luyuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luyuan.data.AskRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 问路远 · 钥匙子页（手机二期 #4）：从设置页拆出的二级页。
 * 只管钥匙（Key/接口地址/模型/连通性自检），聊天历史绝不出现（UI 逻辑规则 §5）。
 * 字段复用 AskRemote 现有 SharedPreferences（base_url/model/api_key），不新开存储。
 * 错误信息人话化口径沿用 v1.8（401/402/404，AskRemote.humanError）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskKeyScreen(vm: LuyuanViewModel, onBack: () -> Unit, onAsk: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cfg0 = remember { AskRemote.loadConfig(context) }
    var key by remember { mutableStateOf(cfg0.key) }
    var base by remember { mutableStateOf(cfg0.baseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AskRemote.CheckResult?>(null) }

    val model = remember { AskRemote.modelByKey(cfg0.modelKey) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("问路远 · 钥匙", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ---------- 钥匙卡 ----------
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "钥匙（只存本机，永不进同步目录/仓库）",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it; saved = false; result = null },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }) {
                                Text(if (showKey) "隐藏" else "显示", color = LuyuanColors.Green700, fontSize = 12.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = base,
                        onValueChange = { base = it; saved = false; result = null },
                        label = { Text("接口地址（默认 DeepSeek）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    // 模型：本页只显示当前项，点击回聊天页顶部选择器切（v1.9 四档）
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onAsk() }
                            .padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("模型", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(model.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LuyuanColors.Ink1)
                        }
                        Text("去对话页切", fontSize = 12.sp, color = LuyuanColors.Green700)
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null, tint = LuyuanColors.Green700
                        )
                    }
                    Text(
                        "DeepSeek / 其他 OpenAI 兼容服务都行。泄露 = 别人替你花钱，别截图发人。",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // ---------- 连通性卡 ----------
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("连通性", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                    if (checking) {
                        Text("自检中…", fontSize = 13.sp, color = LuyuanColors.Ink2, modifier = Modifier.padding(top = 8.dp))
                    } else if (result != null) {
                        val r = result!!
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                if (r.ok) "正常 ✓ ${r.message}" else "异常：${r.message}",
                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = if (r.ok) LuyuanColors.Green700 else MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Text(
                            "保存后自动测一发；点下面「保存钥匙」即触发。",
                            fontSize = 13.sp, color = LuyuanColors.Ink2, modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Text(
                        "401=Key 不对 / 402=余额 / 404=地址或模型名——错误会用人话说。",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // ---------- 保存 ----------
            Button(
                onClick = {
                    AskRemote.saveConfig(context, key, base)
                    saved = true
                    result = null
                    checking = true
                    scope.launch {
                        result = withContext(Dispatchers.IO) { AskRemote.checkConnectivity(context) }
                        checking = false
                    }
                },
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text(if (saved) "已保存" else "保存钥匙", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
