package com.luyuan.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luyuan.platform.PermissionHelper

private const val MODE_SYSTEM = 0
private const val MODE_DICTATION = 1

/** 录音页：系统语音识别（vivo 内置讯飞引擎）为主，不可用/出错自动切键盘长按空格兜底 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(vm: LuyuanViewModel, onBack: () -> Unit) {
    val recording by vm.isRecording.collectAsStateWithLifecycle()
    val live by vm.liveText.collectAsStateWithLifecycle()
    val voiceError by vm.voiceError.collectAsStateWithLifecycle()
    val savedMsg by vm.savedMsg.collectAsStateWithLifecycle()
    val diaryMode by vm.diaryMode.collectAsStateWithLifecycle()
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
    var mode by remember { mutableStateOf(if (canSystem) MODE_SYSTEM else MODE_DICTATION) }

    // 系统识别不可用/报错 → 自动退到键盘兜底
    LaunchedEffect(voiceError) {
        if (voiceError == "unavailable" && mode == MODE_SYSTEM) mode = MODE_DICTATION
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (mode == MODE_SYSTEM) {
                Spacer(Modifier.height(8.dp))
                // 小猫录音动画占位：朋友的图/动图画好后替换这行（竖耳朵=聆听中）
                Text("🐱", fontSize = 64.sp)
                Text(
                    text = when {
                        recording -> (live.ifBlank { "聆听中…说完自动保存" })
                        savedMsg.isNotBlank() -> "✅ 已记下"
                        else -> "点下方按钮开始说话"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                if (savedMsg.isNotBlank()) {
                    Text(
                        savedMsg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                TextButton(onClick = {
                    vm.cancelRecording()
                    mode = MODE_DICTATION
                }) { Text("改用键盘输入") }
            } else {
                // 键盘兜底：长按空格语音输入（讯飞引擎），或直接打字
                var dictation by remember { mutableStateOf("") }
                var prefilled by remember { mutableStateOf(false) }
                LaunchedEffect(diaryMode) {
                    if (diaryMode && !prefilled) {
                        dictation = vm.todayDiary.value?.text ?: ""
                        prefilled = true
                    }
                }
                voiceError?.takeIf { it != "unavailable" }?.let {
                    Text(
                        "⚠️ $it",
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
                    TextButton(onClick = {
                        vm.cancelRecording()
                        mode = MODE_SYSTEM
                    }) { Text("改用语音识别") }
                    TextButton(onClick = onBack) { Text("完成") }
                }
            }
        }
    }
}
