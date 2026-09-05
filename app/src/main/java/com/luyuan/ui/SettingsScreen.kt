package com.luyuan.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.luyuan.platform.PermissionHelper
import com.luyuan.platform.StorageLocator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: LuyuanViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var rootName by remember { mutableStateOf(StorageLocator.getRoot(context).name) }

    val allFiles = PermissionHelper.hasAllFiles(context)
    val audio = PermissionHelper.hasAudio(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("共享目录", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = rootName,
                onValueChange = {
                    rootName = it
                    StorageLocator.setRootName(context, it)
                    vm.refresh()
                },
                label = { Text("Luyuan 根目录名（位于 /storage/emulated/0/ 下）") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "当前完整路径：${StorageLocator.getRoot(context).absolutePath}",
                style = MaterialTheme.typography.labelSmall
            )

            HorizontalDivider()
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

            HorizontalDivider()
            Text("关于", style = MaterialTheme.typography.titleMedium)
            Text(
                "路远 安卓 App · 去中心化本地记事\n数据按 SYNC_FORMAT 与电脑端双向同步（Syncthing）。",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
