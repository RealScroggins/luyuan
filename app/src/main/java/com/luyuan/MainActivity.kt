package com.luyuan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LuyuanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val vm: LuyuanViewModel = viewModel()
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
