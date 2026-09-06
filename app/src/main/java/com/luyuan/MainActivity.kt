package com.luyuan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luyuan.ui.DetailEditScreen
import com.luyuan.ui.JournalScreen
import com.luyuan.ui.LuyuanTheme
import com.luyuan.ui.LuyuanViewModel
import com.luyuan.ui.NoteListScreen
import com.luyuan.ui.PeopleScreen
import com.luyuan.ui.RecordScreen
import com.luyuan.ui.SettingsScreen
import com.luyuan.ui.TrashScreen

class MainActivity : ComponentActivity() {

    /** 快捷磁贴/通知唤起时的目标页（"record" = 进录音页直接开录） */
    private var autoRoute by mutableStateOf("list")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.luyuan.platform.CrashLogger.install(this)
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
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = setOf("list", "journal", "people")

    LaunchedEffect(startDest) {
        if (startDest == "record") {
            vm.startRecording()
            nav.navigate("record") { launchSingleTop = true }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in topLevel) {
                NavigationBar {
                    val tabs = listOf(
                        Triple("list", "记事", Icons.AutoMirrored.Filled.Notes),
                        Triple("journal", "日记", Icons.Default.EditNote),
                        Triple("people", "人脉", Icons.Default.People)
                    )
                    for ((route, label, icon) in tabs) {
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                if (currentRoute != route) {
                                    nav.navigate(route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "list",
            modifier = Modifier.padding(pad)
        ) {
            composable("list") {
                NoteListScreen(
                    vm = vm,
                    onRecord = { nav.navigate("record") },
                    onDetail = { id -> nav.navigate("detail/$id") },
                    onSettings = { nav.navigate("settings") },
                    onTrash = { nav.navigate("trash") }
                )
            }
            composable("journal") {
                JournalScreen(vm = vm, onRecord = { nav.navigate("record") })
            }
            composable("people") {
                PeopleScreen(vm = vm)
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
}
