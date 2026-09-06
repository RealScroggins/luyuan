package com.luyuan.ui

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luyuan.data.SttEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(vm: LuyuanViewModel, onBack: () -> Unit) {
    val recording by vm.isRecording.collectAsStateWithLifecycle()
    val live by vm.liveText.collectAsStateWithLifecycle()
    val sttReady by vm.sttReady.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("录音") },
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (recording) (live.ifBlank { "聆听中…" }) else "点击下方按钮开始录音",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                onClick = { if (recording) vm.stopRecording() else vm.startRecording() },
                modifier = Modifier.size(140.dp)
            ) {
                Text(if (recording) "停止" else "录音")
            }
            if (!sttReady) {
                val dl by SttEngine.downloadProgress.collectAsStateWithLifecycle()
                val status = when {
                    dl in 0..99 -> "模型下载中 $dl%…"
                    dl == 100 -> "模型解压中…"
                    else -> "本地识别模型未就绪：首次使用需联网下载（约 40MB），完成后即可离线转写。" +
                            "当前录音仅保存音频文件，转写文本会留空。"
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
