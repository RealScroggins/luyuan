package com.luyuan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luyuan.ui.CourseScreen
import com.luyuan.ui.DetailEditScreen
import com.luyuan.ui.JournalScreen
import com.luyuan.ui.LedgerScreen
import com.luyuan.ui.LuyuanTheme
import com.luyuan.ui.LuyuanViewModel
import com.luyuan.ui.NoteListScreen
import androidx.compose.ui.graphics.graphicsLayer
import com.luyuan.ui.rememberPressScale
import com.luyuan.ui.PeopleScreen
import com.luyuan.ui.QuickInputSheet
import com.luyuan.ui.RecordScreen
import com.luyuan.ui.SettingsScreen
import com.luyuan.ui.AskKeyScreen
import com.luyuan.ui.TerminalCapsule
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
        autoRoute = routeFromIntent(intent)
    }

    /** 快捷磁贴/通知/桌面组件唤起时的目标页。
     *  "record"/"note" 走超级终端；"page"=跳到底栏某页；"detailId"=进某条笔记详情。 */
    private fun routeFromIntent(i: Intent?): String {
        when (i?.getStringExtra("auto")) {
            "record" -> return "record"
            "note" -> return "note"
        }
        i?.getStringExtra("page")?.let { return "tab:$it" }
        i?.getStringExtra("detailId")?.let { return "detail:$it" }
        return "list"
    }
}

/**
 * 手机 UI 方案 A「五键直达」（2026-09-08 路河拍板，施工合同=交接_App端_手机UI方案A）：
 * 底栏五键（笔记/记账/课程/日记/人脉）+ 悬浮超级终端胶囊（唯一录入口）；
 * 问路远/回收站=右上角 push 进出的独立页。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRoot(startDest: String) {
    val nav = rememberNavController()
    val vm: LuyuanViewModel = viewModel()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { 5 }
    val navInteraction = remember { MutableInteractionSource() }
    val navScale = rememberPressScale(navInteraction)
    var showQuickInput by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val multiSelect by vm.multiSelect.collectAsStateWithLifecycle()

    LaunchedEffect(startDest) {
        when {
            startDest == "record" -> {
                // 快捷磁贴/音量键/小部件唤起：直接开「录音待转写」
                vm.startWavRecording()
                nav.navigate("record") { launchSingleTop = true }
            }
            startDest == "note" -> {
                // 桌面小部件「记一笔」：落在笔记页并弹胶囊输入浮层（主页常驻输入框已由胶囊替代）
                pagerState.scrollToPage(0)
                showQuickInput = true
            }
            startDest.startsWith("tab:") -> {
                // 桌面组件今日卡：跳到底栏对应页
                val idx = when (startDest.removePrefix("tab:")) {
                    "notes" -> 0
                    "ledger" -> 1
                    "course" -> 2
                    "journal" -> 3
                    "people" -> 4
                    else -> 0
                }
                pagerState.scrollToPage(idx)
            }
            startDest.startsWith("detail:") -> {
                // 桌面组件今日卡「最近」：进对应笔记详情
                pagerState.scrollToPage(0)
                nav.navigate("detail/${startDest.removePrefix("detail:")}") { launchSingleTop = true }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute == "home") {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    val tabs = listOf(
                        Triple(0, "笔记", Icons.AutoMirrored.Filled.Notes),
                        Triple(1, "记账", Icons.Default.Payments),
                        Triple(2, "课程", Icons.Default.CalendarMonth),
                        Triple(3, "日记", Icons.Default.EditNote),
                        Triple(4, "人脉", Icons.Default.People)
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
                        label = { Text(label) },
                        interactionSource = navInteraction,
                        modifier = Modifier.graphicsLayer { scaleX = navScale; scaleY = navScale }
                    )
                    }
                }
            }
        }
    ) { pad ->
        Box(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            NavHost(
                navController = nav,
                startDestination = "home"
            ) {
                composable("home") {
                    // beyondBounds=4：五页全部常驻，翻页不丢输入框草稿/搜索词/列表位置
                    HorizontalPager(
                        state = pagerState,
                        beyondBoundsPageCount = 4,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> NoteListScreen(
                                vm = vm,
                                onRecord = {
                                    vm.startWavRecording()
                                    nav.navigate("record") { launchSingleTop = true }
                                },
                                onDetail = { id -> nav.navigate("detail/$id") },
                                onSettings = { nav.navigate("settings") },
                                onTrash = { nav.navigate("trash") }
                            )
                            1 -> LedgerScreen(
                                vm = vm,
                                onAsk = { nav.navigate("ask") },
                                onTrash = { nav.navigate("trash") }
                            )
                            2 -> CourseScreen(
                                vm = vm,
                                onAsk = { nav.navigate("ask") },
                                onTrash = { nav.navigate("trash") }
                            )
                            3 -> JournalScreen(vm = vm, onRecord = {
                                vm.startWavRecording()
                                nav.navigate("record") { launchSingleTop = true }
                            })
                            else -> PeopleScreen(vm = vm, onNoteClick = { nav.navigate("detail/$it") })
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
                    SettingsScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() },
                        onAsk = { nav.navigate("ask") },
                        onAskKey = { nav.navigate("askkey") }
                    )
                }
                composable("askkey") {
                    AskKeyScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() },
                        onAsk = { nav.navigate("ask") }
                    )
                }
                composable("ask") {
                    com.luyuan.ui.AskScreen(vm = vm, onBack = { nav.popBackStack() })
                }
                composable("trash") {
                    TrashScreen(vm = vm, onBack = { nav.popBackStack() })
                }
            }
            // 悬浮终端胶囊（路河拍板：替代主页常驻输入框与搜索框，一框三用=输入/搜索/录音；多选时收起）
            if (currentRoute == "home" && !multiSelect) {
                TerminalCapsule(
                    searchMode = searchMode,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { vm.setSearchQuery(it) },
                    onToggleSearch = {
                        searchMode = !searchMode
                        if (!searchMode) vm.setSearchQuery("")
                    },
                    onQuickInput = { showQuickInput = true },
                    onRecord = {
                        vm.startWavRecording()
                        nav.navigate("record") { launchSingleTop = true }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
    }

    if (showQuickInput) {
        QuickInputSheet(vm = vm, onDismiss = { showQuickInput = false })
    }
}
