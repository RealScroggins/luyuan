package com.luyuan.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luyuan.platform.PermissionHelper
import kotlinx.coroutines.delay

private const val MODE_SYSTEM = 0
private const val MODE_RECORD = 1
private const val MODE_DICTATION = 2

/** 语音记事页：即时识别（系统引擎）/ 录音待转写（回电脑 SenseVoice）/ 键盘输入 三模式 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(vm: LuyuanViewModel, onBack: () -> Unit) {
    val recording by vm.isRecording.collectAsStateWithLifecycle()
    val live by vm.liveText.collectAsStateWithLifecycle()
    val voiceError by vm.voiceError.collectAsStateWithLifecycle()
    val savedMsg by vm.savedMsg.collectAsStateWithLifecycle()
    val diaryMode by vm.diaryMode.collectAsStateWithLifecycle()
    val wavStartedAt by vm.wavStartedAt.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (!PermissionHelper.hasAudio(context)) {
            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (!PermissionHelper.hasPostNotifications(context)) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val canSystem = remember { com.luyuan.platform.SystemSpeech.available(context) }
    var mode by remember {
        val pref = vm.preferredVoiceMode()
        mutableStateOf(
            when {
                pref == "dictation" -> MODE_DICTATION
                pref == "record" -> MODE_RECORD
                canSystem -> MODE_SYSTEM
                else -> MODE_RECORD
            }
        )
    }

    // 系统识别不可用 → 自动退到「录音待转写」（本机语音的最佳出路）
    LaunchedEffect(voiceError) {
        if (voiceError?.startsWith("unavailable") == true && mode == MODE_SYSTEM) {
            mode = MODE_RECORD
        }
    }
    // 键盘模式：自动聚焦并弹键盘
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(mode) {
        if (mode == MODE_DICTATION) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (diaryMode) "记日记" else "语音记事") },
                navigationIcon = {
                    IconButton(onClick = {
                        vm.cancelRecording()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 模式切换
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModeChip("🎙 即时识别", mode == MODE_SYSTEM) {
                    vm.cancelRecording()
                    vm.rememberVoiceMode("auto")
                    mode = MODE_SYSTEM
                }
                ModeChip("📼 录音待转写", mode == MODE_RECORD) {
                    vm.cancelRecording()
                    vm.rememberVoiceMode("record")
                    mode = MODE_RECORD
                }
                ModeChip("⌨ 键盘", mode == MODE_DICTATION) {
                    vm.cancelRecording()
                    vm.rememberVoiceMode("dictation")
                    mode = MODE_DICTATION
                }
            }

            when (mode) {
                MODE_SYSTEM -> {
                    Spacer(Modifier.height(4.dp))
                    Text("🐱", fontSize = 64.sp)
                    Text(
                        text = when {
                            recording -> live.ifBlank { "聆听中…说完自动保存" }
                            savedMsg.isNotBlank() -> "✅ 已记下"
                            else -> "点下方按钮开始说话"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (recording) {
                        Button(onClick = { vm.stopRecording() }, modifier = Modifier.size(120.dp)) {
                            Text("停止")
                        }
                    } else {
                        Button(
                            onClick = { vm.startRecording(diaryMode) },
                            modifier = Modifier.size(120.dp)
                        ) { Text(if (savedMsg.isBlank()) "开始" else "再说一句") }
                    }
                    voiceError?.takeIf { !it.startsWith("unavailable") }?.let {
                        Text(
                            "⚠️ $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                MODE_RECORD -> {
                    Spacer(Modifier.height(4.dp))
                    Text("🎙️", fontSize = 64.sp)
                    // 录音计时
                    var tick by remember { mutableLongStateOf(0L) }
                    LaunchedEffect(wavStartedAt) {
                        if (wavStartedAt > 0L) {
                            while (vm.isRecording.value) {
                                tick = System.currentTimeMillis()
                                delay(500)
                            }
                        }
                    }
                    if (recording) {
                        val secs = ((tick - wavStartedAt) / 1000).coerceAtLeast(0)
                        Text(
                            "● 录音中  %02d:%02d".format(secs / 60, secs % 60),
                            fontSize = 22.sp,
                            color = Color(0xFFEF4444)
                        )
                        Text(
                            "停止后原声自动同步回电脑，转写完成文字就回来了",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { vm.stopWavRecording() },
                            modifier = Modifier.size(120.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) { Text("停止") }
                    } else {
                        Text(
                            if (savedMsg.startsWith("已录音")) "✅ $savedMsg" else "录下原声，回家自动转写",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "电脑开机会用 SenseVoice 自动转写（延迟约十几秒到几分钟）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { vm.startWavRecording(diaryMode) },
                            modifier = Modifier.size(120.dp)
                        ) { Text(if (savedMsg.startsWith("已录音")) "再录一段" else "开始录音") }
                    }
                }

                else -> {
                    // 键盘兜底：长按空格语音输入（讯飞引擎），或直接打字
                    var dictation by remember { mutableStateOf("") }
                    var prefilled by remember { mutableStateOf(false) }
                    LaunchedEffect(diaryMode) {
                        if (diaryMode && !prefilled) {
                            dictation = vm.todayDiary.value?.text ?: ""
                            prefilled = true
                        }
                    }
                    voiceError?.let {
                        Text(
                            if (it.startsWith("unavailable")) "⚠️ 本机没有可用的系统语音服务（$it）"
                            else "⚠️ $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        "长按键盘空格说话，文字会打到这里；也可直接打字",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dictation,
                        onValueChange = { dictation = it },
                        placeholder = { Text(if (diaryMode) "今天的日记…" else "说点什么…") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .focusRequester(focusRequester),
                        minLines = 4
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                vm.saveDictation(dictation, diaryMode)
                                dictation = ""
                            },
                            enabled = dictation.isNotBlank()
                        ) { Text(if (diaryMode) "保存日记" else "保存笔记") }
                        TextButton(onClick = onBack) { Text("完成") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) { Text(label) }
}
