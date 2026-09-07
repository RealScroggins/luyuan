package com.luyuan.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luyuan.data.AskRemote
import com.luyuan.data.NoteRepository
import com.luyuan.platform.PermissionHelper
import com.luyuan.platform.StorageLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 设置页：共享目录防呆选择器（扫描候选点选，杜绝手打名字出错）+ 权限引导 + 问路远配置 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: LuyuanViewModel, onBack: () -> Unit, onAsk: () -> Unit = {}) {
    val context = LocalContext.current
    val allFiles = PermissionHelper.hasAllFiles(context)
    val audio = PermissionHelper.hasAudio(context)

    var currentPath by remember { mutableStateOf(StorageLocator.getRoot(context).absolutePath) }
    var candidates by remember { mutableStateOf(listOf<StorageLocator.Candidate>()) }
    var scanning by remember { mutableStateOf(false) }

    // 问路远（A2）：Key 只存本机 App 私有目录（隐私红线，永不进同步目录/仓库）
    val askCfg = remember { AskRemote.loadConfig(context) }
    var askKey by remember { mutableStateOf(askCfg.key) }
    var askBase by remember { mutableStateOf(askCfg.baseUrl) }
    var askModel by remember { mutableStateOf(askCfg.model) }
    var askSaved by remember { mutableStateOf(false) }

    suspend fun rescan() {
        scanning = true
        val list = withContext(Dispatchers.IO) { StorageLocator.candidates(context) }
        candidates = list
        currentPath = StorageLocator.getRoot(context).absolutePath
        scanning = false
    }

    LaunchedEffect(Unit) { rescan() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("共享目录", style = MaterialTheme.typography.titleMedium)
            Text(
                "当前：$currentPath",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val curCount = candidates.firstOrNull { it.path == currentPath }?.count
            if (curCount != null) {
                Text(
                    "此目录扫到 $curCount 条笔记。" +
                            if (curCount == 0) "如果是 0，多半是选错目录或 Syncthing 还没同步。" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (curCount == 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("点选候选目录（按笔记数排序）：", style = MaterialTheme.typography.labelMedium)
            for (c in candidates) {
                val selected = c.path == currentPath
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            StorageLocator.setRootName(context, c.path.substringAfterLast('/'))
                            currentPath = c.path
                            vm.refresh()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            c.path.substringAfterLast('/') +
                                    if (selected) "（当前）" else "",
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            "${c.count} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (scanning) {
                Text("扫描中…", style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = { vm.refresh() }) { Text("重新读取列表") }

            Text(
                "注意：目录必须与 Syncthing App 里共享的文件夹完全一致（App 只负责读写，搬运交给 Syncthing）。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("权限", style = MaterialTheme.typography.titleMedium)
            Text("麦克风：${if (audio) "已授权" else "未授权"}")
            Text(
                "所有文件访问：${if (allFiles) "已授权" else "未授权（需在设置中开启，才能读写 Syncthing 共享目录）"}"
            )
            if (!allFiles) {
                Button(onClick = {
                    context.startActivity(PermissionHelper.allFilesSettingsIntent())
                }) { Text("去开启所有文件访问") }
            }

            // 实验性：双击音量下键直接录音（无障碍服务全局监听，不需要 adb）
            val volumeServiceOn = remember {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )?.contains("VolumeKeyService") == true
            }
            val overlayOn = remember {
                android.provider.Settings.canDrawOverlays(context)
            }
            Text("实体键快捷（实验性）", style = MaterialTheme.typography.titleMedium)
            Text(
                "双击音量下键 = 直接开始录音。需开启两处：①无障碍里的「路远·双击音量下键录音」（状态：${if (volumeServiceOn) "已开启" else "未开启"}）；②「显示在其他应用上层」（状态：${if (overlayOn) "已授权" else "未授权"}）。不拦截音量本身，双击时音量也会正常降低。若被 vivo 后台清理，请在管家里允许路远自启动并锁定后台。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    )
                }) { Text("去开无障碍") }
                Button(onClick = {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:com.luyuan")
                        )
                    )
                }) { Text("去开悬浮层") }
            }

            Text("语音模型离线导入", style = MaterialTheme.typography.titleMedium)
            Text(
                "在线下载慢？推荐免数据线的方式：\n" +
                        "① 电脑把 vosk-model-small-cn-0.22.zip 放进共享文件夹的 model\\ 子目录（D:\\Luyuan\\data\\notes\\model\\）；\n" +
                        "② 等 Syncthing 同步到手机（AA 路远/model/）；\n" +
                        "③ 重启路远自动识别（zip 或解压后的文件夹都认）。\n" +
                        "备选：数据线把 zip 拷到手机「Download」文件夹也可以。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ---------- 问路远（A2） ----------
            Text("🤖 问路远（AI 问答）", style = MaterialTheme.typography.titleMedium)
            Text(
                "填一次 OpenAI 兼容接口（默认 DeepSeek），就能随时用对话问它，回答会参考你本机的笔记和待办。" +
                    "Key 只存本机 App 私有目录，不进同步目录；私密标签的笔记不会发出去。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = askKey,
                onValueChange = { askKey = it; askSaved = false },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = askBase,
                    onValueChange = { askBase = it; askSaved = false },
                    label = { Text("接口地址") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = askModel,
                    onValueChange = { askModel = it; askSaved = false },
                    label = { Text("模型") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Button(onClick = {
                    AskRemote.saveConfig(context, askKey, askBase, askModel)
                    askSaved = true
                }) { Text(if (askSaved) "✅ 已保存" else "保存") }
                Button(
                    onClick = onAsk,
                    enabled = AskRemote.loadConfig(context).ready || askKey.isNotBlank()
                ) { Text("开始对话") }
            }

            Text("关于", style = MaterialTheme.typography.titleMedium)
            Text(
                "路远 安卓 App v1.3 · 去中心化本地记事\n数据按 SYNC_FORMAT 与电脑端双向同步（Syncthing）。\n语音识别走系统引擎（免费无密钥）；联系人来自共享目录 contacts/。",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
