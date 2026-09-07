package com.luyuan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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
import kotlinx.coroutines.launch

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
        val r = routeFromIntent(intent)
        if (r == "record" || r == "note") autoRoute = r
    }

    private fun routeFromIntent(i: Intent?): String = when (i?.getStringExtra("auto")) {
        "record" -> "record"
        "note" -> "note"
        else -> "list"
    }
}

/** 三页横滑：日记(负一屏) ← 记事(主页) → 人脉(第二屏)，底部栏点选与手势互通（与 PC 面板同构） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRoot(startDest: String) {
    val nav = rememberNavController()
    val vm: LuyuanViewModel = viewModel()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 1) { 3 }

    LaunchedEffect(startDest) {
        when (startDest) {
            "record" -> {
                // 快捷磁贴/音量键/小部件唤起：直接开「录音待转写」（系统识别通道已移除）
                vm.startWavRecording()
                nav.navigate("record") { launchSingleTop = true }
            }
            "note" -> {
                // 桌面小部件「记一笔」：确保落在主页输入框并弹键盘
                pagerState.scrollToPage(1)
                vm.requestDraftFocus()
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute == "home") {
                NavigationBar {
                    val tabs = listOf(
                        Triple(0, "日记", Icons.Default.EditNote),
                        Triple(1, "记事", Icons.AutoMirrored.Filled.Notes),
                        Triple(2, "人脉", Icons.Default.People)
                    )
                    for ((page, label, icon) in tabs) {
                        NavigationBarItem(
                            selected = pagerState.currentPage == page,
                            onClick = {
                                if (pagerState.currentPage != page) {
                                    scope.launch { pagerState.animateScrollToPage(page) }
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
            startDestination = "home",
            modifier = Modifier.padding(pad)
        ) {
            composable("home") {
                // beyondBounds=2：三页常驻，翻页不丢输入框草稿/搜索词/列表位置
                HorizontalPager(
                    state = pagerState,
                    beyondBoundsPageCount = 2,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> JournalScreen(vm = vm, onRecord = { nav.navigate("record") })
                        1 -> NoteListScreen(
                            vm = vm,
                            onRecord = { nav.navigate("record") },
                            onDetail = { id -> nav.navigate("detail/$id") },
                            onSettings = { nav.navigate("settings") },
                            onTrash = { nav.navigate("trash") }
                        )
                        else -> PeopleScreen(vm = vm)
                    }
                }
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
