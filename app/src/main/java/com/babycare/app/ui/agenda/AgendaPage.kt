package com.babycare.app.ui.agenda

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.SupplementPlanPayload
import com.babycare.app.domain.AgendaEntry
import com.babycare.app.domain.AgendaMonth
import com.babycare.app.domain.TimeKit
import com.babycare.app.domain.buildTimelineItems
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.today.RecordSheet
import com.babycare.app.ui.today.RecordSheetHost
import com.babycare.app.ui.today.TodayViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 行程 Tab（FR-12）：疫苗临近/补剂两卡置顶 → 今天 → 即将到来（7 天）→ 未来按月折叠；早教卡 0013 移除（今天页已有） */
@Composable
fun AgendaPage(
    todayVm: TodayViewModel,
    vm: AgendaViewModel,
    onOpenGrowth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.ui.collectAsState()
    val board = state.board
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var activeSheet by remember { mutableStateOf<RecordSheet?>(null) }
    // 补剂方案管理（两层：列表 → 编辑/新建）
    var showPlans by remember { mutableStateOf(false) }
    var planEdit by remember { mutableStateOf<Pair<RecordEntity, SupplementPlanPayload>?>(null) }
    var planCreate by remember { mutableStateOf(false) }
    // 月分组展开态（默认收起）；存逗号串以便 rememberSaveable
    var expandedRaw by rememberSaveable { mutableStateOf("") }
    val expanded = remember(expandedRaw) { expandedRaw.split(',').filter { it.isNotEmpty() }.toSet() }
    fun toggleMonth(m: YearMonth) {
        val key = m.toString()
        val next = if (key in expanded) expanded - key else expanded + key
        expandedRaw = next.sorted().joinToString(",")
    }

    fun openDetail(e: AgendaEntry) {
        buildTimelineItems(listOf(e.entity)).firstOrNull()?.let { activeSheet = RecordSheet.Detail(it) }
    }

    // 今日两卡（疫苗临近 / 补剂任务；早教 0013 起只在今天页）
    val tasks = remember(
        state.baby?.birthday, today, state.planRecords, state.supplementLogs, state.vaccineRecords, state.eduCheckins,
    ) {
        buildTodayTasks(
            state.baby?.birthday, today, zone,
            state.planRecords, state.supplementLogs, state.vaccineRecords, state.eduCheckins,
        )
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text("行程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "今天该做什么 · 体检按生日自动生成",
                        style = MaterialTheme.typography.labelMedium,
                        color = BabyTheme.colors.text3,
                    )
                }
            }

            // ---- 今日两卡（FR-12：疫苗置顶；早教卡 0013 移除） ----
            if (tasks.nearDoses.isNotEmpty()) {
                item(key = "near-vaccine") {
                    VaccineNearCard(doses = tasks.nearDoses, onOpenGrowth = onOpenGrowth)
                }
            }
            if (tasks.suppTasks.isNotEmpty()) {
                item(key = "today-supp") {
                    SupplementTodayCard(
                        tasks = tasks.suppTasks,
                        onToggle = { t ->
                            todayVm.toggleSupplementLog(findSuppLog(state.supplementLogs, t, today, zone), t.plan.name, t.timePoint)
                        },
                        onManage = { showPlans = true },
                    )
                }
            }

            // ---- 今天 ----
            item { SectionHeader("今天 · ${TimeKit.formatMonthDay(today)}", board.today.size) }
            if (board.today.isEmpty()) {
                item { Hint("今天没有行程") }
            } else {
                items(board.today, key = { it.entity.uuid }) { e ->
                    AgendaRow(
                        entry = e,
                        onToggleDone = { todayVm.toggleAgendaDone(e.entity) },
                        onClick = { openDetail(e) },
                    )
                }
            }

            // ---- 即将到来 ----
            item { SectionHeader("即将到来", board.upcoming.size) }
            if (board.upcoming.isEmpty()) {
                item { Hint("未来 7 天没有行程") }
            } else {
                items(board.upcoming, key = { it.entity.uuid }) { e ->
                    AgendaRow(
                        entry = e,
                        showDate = TimeKit.weekdayText(TimeKit.toLocalDate(e.entity.occurredAt, zone)),
                        onToggleDone = { todayVm.toggleAgendaDone(e.entity) },
                        onClick = { openDetail(e) },
                    )
                }
            }

            // ---- 未来计划（按月折叠） ----
            if (board.futureMonths.isNotEmpty()) {
                item { SectionHeader("未来计划", board.futureMonths.sumOf { it.items.size }) }
                board.futureMonths.forEach { block ->
                    item(key = "month-${block.month}") {
                        MonthHeader(
                            block = block,
                            expanded = block.month.toString() in expanded,
                            onToggle = { toggleMonth(block.month) },
                        )
                    }
                    if (block.month.toString() in expanded) {
                        items(block.items, key = { it.entity.uuid }) { e ->
                            AgendaRow(
                                entry = e,
                                showDate = TimeKit.formatMonthDay(TimeKit.toLocalDate(e.entity.occurredAt, zone)),
                                onToggleDone = { todayVm.toggleAgendaDone(e.entity) },
                                onClick = { openDetail(e) },
                            )
                        }
                    }
                }
            }

            if (board.today.isEmpty() && board.upcoming.isEmpty() && board.futureMonths.isEmpty()) {
                item { Hint("还没有行程，点右下角 ＋ 建一条") }
            }
        }

        FloatingActionButton(
            onClick = { activeSheet = RecordSheet.Agenda(null) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = BabyTheme.colors.primary,
            contentColor = Color.White,
        ) { Text("＋", fontSize = 22.sp) }
    }

    RecordSheetHost(
        sheet = activeSheet,
        vm = todayVm,
        onDismiss = { activeSheet = null },
        onEdit = { activeSheet = it },
    )

    // 补剂方案管理（两层：列表 → 编辑/新建；同时只开一层）
    if (showPlans) {
        SupplementPlanListSheet(
            planRecords = state.planRecords,
            onEdit = { planEdit = it },
            onCreate = { planCreate = true },
            onToggleStopped = { todayVm.toggleSupplementStopped(it) },
            onDismiss = { showPlans = false },
        )
    }
    if (planCreate || planEdit != null) {
        val editing = planEdit
        SupplementPlanEditSheet(
            editing = editing,
            onSave = { name, dose, timePoints, startDate ->
                val e = editing?.first
                if (e != null) todayVm.updateSupplementPlan(e, name, dose, timePoints, startDate)
                else todayVm.saveSupplementPlan(name, dose, timePoints, startDate)
                planEdit = null
                planCreate = false
            },
            onDelete = editing?.first?.let { e -> { todayVm.delete(e) } },
            onDismiss = { planEdit = null; planCreate = false },
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = BabyTheme.colors.text1,
            modifier = Modifier.weight(1f),
        )
        Text("$count 项", style = MaterialTheme.typography.labelSmall, color = BabyTheme.colors.text2)
    }
}

@Composable
private fun MonthHeader(block: AgendaMonth, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${block.month.year}年${block.month.monthValue}月",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = BabyTheme.colors.text1,
            modifier = Modifier.weight(1f),
        )
        Text("${block.items.size} 项", style = MaterialTheme.typography.labelSmall, color = BabyTheme.colors.text2)
        Text(
            if (expanded) "收起 ▲" else "展开 ▼",
            style = MaterialTheme.typography.labelMedium,
            color = BabyTheme.colors.primary,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = BabyTheme.colors.text3,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
