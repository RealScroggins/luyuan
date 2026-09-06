package com.luyuan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luyuan.ui.DetailEditScreen
import com.luyuan.ui.LuyuanTheme
import com.luyuan.ui.LuyuanViewModel
import com.luyuan.ui.NoteListScreen
import com.luyuan.ui.RecordScreen
import com.luyuan.ui.SettingsScreen
import com.luyuan.ui.TrashScreen

class MainActivity : ComponentActivity() {

    /** 快捷磁贴/通知唤起时的目标页（"record" = 进录音页直接开录） */
    private var autoRoute by mutableStateOf("list")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoRoute = routeFromIntent(intent)
        setContent {
            LuyuanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(startDest = autoRoute)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (routeFromIntent(intent) == "record") autoRoute = "record"
    }

    private fun routeFromIntent(i: Intent?): String =
        if (i?.getStringExtra("auto") == "record") "record" else "list"
}

@Composable
fun AppRoot(startDest: String) {
    val nav = rememberNavController()
    val vm: LuyuanViewModel = viewModel()
    LaunchedEffect(startDest) {
        if (startDest == "record") {
            vm.startRecording()
            nav.navigate("record") { launchSingleTop = true }
        }
    }
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            NoteListScreen(
                vm = vm,
                onRecord = { nav.navigate("record") },
                onDetail = { id -> nav.navigate("detail/$id") },
                onSettings = { nav.navigate("settings") },
                onTrash = { nav.navigate("trash") }
            )
        }
        composable("record") {
            RecordScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(
            "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { back ->
            val id = back.arguments?.getString("id") ?: ""
            DetailEditScreen(vm = vm, noteId = id, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("trash") {
            TrashScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
