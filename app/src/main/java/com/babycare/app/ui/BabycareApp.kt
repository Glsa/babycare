package com.babycare.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.babycare.app.BabycareApplication
import com.babycare.app.ui.agenda.AgendaPage
import com.babycare.app.ui.agenda.AgendaViewModel
import com.babycare.app.ui.calendar.CalendarPage
import com.babycare.app.ui.calendar.CalendarViewModel
import com.babycare.app.ui.growth.GrowthPage
import com.babycare.app.ui.growth.GrowthViewModel
import com.babycare.app.ui.mine.MinePage
import com.babycare.app.ui.mine.MineViewModel
import com.babycare.app.ui.stats.StatsPage
import com.babycare.app.ui.today.TodayPage
import com.babycare.app.ui.today.TodayViewModel
import kotlinx.coroutines.flow.filterNotNull

private data class TabSpec(val emoji: String, val label: String)

// emoji 图标与 Figma 设计稿一致，省 material-icons-extended 依赖
private val Tabs = listOf(
    TabSpec("🏠", "今天"),
    TabSpec("📅", "日历"),
    TabSpec("🗓️", "行程"),
    TabSpec("🌱", "成长"),
    TabSpec("👤", "我的"),
)

/** 根布局：五 Tab 底部导航。五个平级页面无深链需求，不引导航库。 */
@Composable
fun BabycareApp() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val app = LocalContext.current.applicationContext as BabycareApplication
    // Widget 深链进来时切回今天 Tab（TodayPage 消费弹层键）
    LaunchedEffect(app) {
        app.pendingSheet.filterNotNull().collect { selected = 0 }
    }
    // 提醒通知点击直达指定 Tab（0014），消费后清空
    LaunchedEffect(app) {
        app.pendingTab.filterNotNull().collect {
            if (it in Tabs.indices) selected = it
            app.pendingTab.value = null
        }
    }
    val todayVm: TodayViewModel = viewModel(factory = viewModelFactory {
        initializer { TodayViewModel(app.repository, app.timerCenter, app.syncCenter, app.deviceId) }
    })
    val calendarVm: CalendarViewModel = viewModel(factory = viewModelFactory {
        initializer { CalendarViewModel(app.repository) }
    })
    val agendaVm: AgendaViewModel = viewModel(factory = viewModelFactory {
        initializer { AgendaViewModel(app.repository) }
    })
    val growthVm: GrowthViewModel = viewModel(factory = viewModelFactory {
        initializer { GrowthViewModel(app.repository) }
    })
    val mineVm: MineViewModel = viewModel(factory = viewModelFactory {
        initializer { MineViewModel(app.repository, app.timerCenter, app.contentResolver, app.syncCenter) }
    })

    // 统计页全屏层（0015）：盖住底栏，从我的页进入
    var showStats by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    Tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Text(tab.emoji) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            val modifier = Modifier.fillMaxSize().padding(innerPadding)
            when (selected) {
                0 -> TodayPage(
                    todayVm, snackbarHostState,
                    onOpenAgenda = { selected = 2 },
                    onOpenGrowth = { selected = 3 },
                    modifier,
                )
                1 -> CalendarPage(todayVm, calendarVm, snackbarHostState, modifier)
                2 -> AgendaPage(todayVm, agendaVm, onOpenGrowth = { selected = 3 }, modifier)
                3 -> GrowthPage(todayVm, growthVm, modifier)
                else -> MinePage(mineVm, snackbarHostState, onOpenStats = { showStats = true }, modifier)
            }
        }
        // 统计页盖在最上层（含底栏），返回键/左上角返回再回到我的页
        if (showStats) StatsPage(onBack = { showStats = false })
    }
}
