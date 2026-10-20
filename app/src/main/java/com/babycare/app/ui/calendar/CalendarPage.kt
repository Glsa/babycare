package com.babycare.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babycare.app.domain.AgeCalc
import com.babycare.app.domain.AgendaKit
import com.babycare.app.domain.CalendarKit
import com.babycare.app.domain.TimeKit
import com.babycare.app.domain.buildTimelineItems
import com.babycare.app.ui.agenda.AgendaRow
import com.babycare.app.ui.onboarding.OnboardingScreen
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.theme.recordColors
import com.babycare.app.ui.today.QuickActions
import com.babycare.app.ui.today.RecordSheet
import com.babycare.app.ui.today.RecordSheetHost
import com.babycare.app.ui.today.SummaryCard
import com.babycare.app.ui.today.TimelineRow
import com.babycare.app.ui.today.TodayViewModel
import com.babycare.app.ui.today.TypeBadge
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 日历页（FR-11）：月视图色点回看 + 选中日明细 + 日历补录。写操作/弹层复用今天页 */
@Composable
fun CalendarPage(
    todayVm: TodayViewModel,
    vm: CalendarViewModel,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val state by vm.ui.collectAsState()
    if (!state.loaded) {
        Box(modifier)
        return
    }
    val baby = state.baby
    if (baby == null) {
        OnboardingScreen(
            onCreate = { nickname, birthday, gender -> todayVm.createBaby(nickname, birthday, gender) },
            modifier = modifier,
        )
        return
    }

    var activeSheet by remember { mutableStateOf<RecordSheet?>(null) }
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    // 月粒度页面：今天以进入页面时为准，翻页/重进刷新（design §5）
    val today = remember { LocalDate.now(zone) }
    val sel = state.selected
    val timeline = remember(state.selectedRecords) { buildTimelineItems(state.selectedRecords) }
    // 未来日：显示当天计划行程（0006 回填）
    val futureAgenda = remember(state.selectedRecords, sel) {
        sel?.takeIf { it.isAfter(today) }?.let { AgendaKit.dayAgendas(state.selectedRecords, it, zone) } ?: emptyList()
    }
    // 补录默认时间：选中日 12:00（弹层内可再调时分）；未来日不给
    val defaultTime = sel
        ?.takeIf { !it.isAfter(today) }
        ?.atTime(12, 0)?.atZone(zone)?.toInstant()?.toEpochMilli()

    Column(modifier.fillMaxSize()) {
        MonthBar(
            month = state.month,
            onPrev = vm::prevMonth,
            onNext = vm::nextMonth,
            onToday = vm::goToday,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp),
        )
        WeekdayRow(Modifier.padding(horizontal = 16.dp))
        MonthGrid(
            month = state.month,
            dayTypes = state.dayTypes,
            selected = sel,
            today = today,
            onSelect = vm::select,
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                sel == null -> item {
                    Text(
                        "点某一天，回看当天的记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BabyTheme.colors.text3,
                    )
                }

                sel.isAfter(today) -> {
                    item { DayTitle(sel, baby.birthday) }
                    if (futureAgenda.isEmpty()) {
                        item { FutureDayHint() }
                    } else {
                        items(futureAgenda, key = { it.entity.uuid }) { entry ->
                            AgendaRow(
                                entry = entry,
                                onToggleDone = { todayVm.toggleAgendaDone(entry.entity) },
                                onClick = {
                                    buildTimelineItems(listOf(entry.entity)).firstOrNull()
                                        ?.let { activeSheet = RecordSheet.Detail(it) }
                                },
                            )
                        }
                    }
                }

                else -> {
                    item { DayTitle(sel, baby.birthday) }
                    item { SummaryCard(state.selectedSummary) }
                    item {
                        // 日历里的睡眠/亲喂走手动补录表单（计时器只在今天页）
                        QuickActions(
                            onFormula = { activeSheet = RecordSheet.Formula(null) },
                            onNursing = { activeSheet = RecordSheet.Nursing(null) },
                            onDiaper = { activeSheet = RecordSheet.Diaper(null) },
                            onSleep = { activeSheet = RecordSheet.Sleep(null) },
                            onBath = {
                                todayVm.saveBath(defaultTime ?: System.currentTimeMillis(), "")
                                scope.launch { snackbar.showSnackbar("已补录 🛁 洗澡") }
                            },
                        )
                    }
                    if (timeline.isEmpty()) {
                        item {
                            Text(
                                "这天没有记录，可从上面补一笔",
                                style = MaterialTheme.typography.bodyMedium,
                                color = BabyTheme.colors.text3,
                            )
                        }
                    } else {
                        items(timeline, key = { it.entity.uuid }) { item ->
                            TimelineRow(
                                item = item,
                                onClick = { activeSheet = RecordSheet.Detail(item) },
                                authorLabel = { todayVm.authorLabel(item.entity.author) },
                            )
                        }
                    }
                }
            }
        }
    }

    RecordSheetHost(
        sheet = activeSheet,
        vm = todayVm,
        onDismiss = { activeSheet = null },
        onEdit = { activeSheet = it },
        defaultTime = defaultTime,
    )
}

/** 月份条：‹ 2026年10月 › + 今天快捷回当月 */
@Composable
private fun MonthBar(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev, modifier = Modifier.size(40.dp)) {
            Text("‹", fontSize = 20.sp, color = BabyTheme.colors.text1)
        }
        Text(
            "${month.year}年${month.monthValue}月",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        TextButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
            Text("›", fontSize = 20.sp, color = BabyTheme.colors.text1)
        }
        TextButton(onClick = onToday) { Text("今天") }
    }
}

@Composable
private fun WeekdayRow(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach {
            Text(
                it,
                Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = BabyTheme.colors.text3,
            )
        }
    }
}

/** 月网格：固定 6 行 × 7 列（数据量小，不用 Lazy 网格） */
@Composable
private fun MonthGrid(
    month: YearMonth,
    dayTypes: Map<LocalDate, Set<String>>,
    selected: LocalDate?,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val matrix = remember(month) { CalendarKit.monthMatrix(month) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        matrix.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        isToday = date == today,
                        isSelected = date == selected,
                        types = date?.let { dayTypes[it] } ?: emptySet(),
                        onClick = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 单日格：日期数字（今天主色圆白字 / 选中 soft 圆）+ 下方类型色点（≤5） */
@Composable
private fun DayCell(
    date: LocalDate?,
    isToday: Boolean,
    isSelected: Boolean,
    types: Set<String>,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BabyTheme.colors
    if (date == null) {
        Spacer(modifier.height(52.dp))
        return
    }
    Column(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick(date) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bg = when {
            isToday -> c.primary
            isSelected -> c.primarySoft
            else -> Color.Transparent
        }
        val fg = when {
            isToday -> Color.White
            isSelected -> c.primary
            else -> c.text1
        }
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Spacer(Modifier.height(3.dp))
        // 色点行固定占位高度，无点也占位保持行对齐
        Row(
            Modifier.height(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CalendarKit.dotTypes(types).forEach { type ->
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(recordColors(type, c).first)
                )
            }
        }
    }
}

/** 选中日标题：10月15日 周四 · 第 N 天（生日前的日期不显示天数） */
@Composable
private fun DayTitle(date: LocalDate, birthday: LocalDate) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${TimeKit.formatMonthDay(date)} ${TimeKit.weekdayText(date)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (!date.isBefore(birthday)) {
            TypeBadge(
                "第${AgeCalc.dayNumber(date, birthday)}天",
                BabyTheme.colors.primary,
                BabyTheme.colors.primarySoft,
            )
        }
    }
}

/** 未来日空态：行程 feature 上线后回填 */
@Composable
private fun FutureDayHint() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("🗓️", fontSize = 32.sp)
        Text("这天还没有计划", style = MaterialTheme.typography.bodyMedium, color = BabyTheme.colors.text2)
        Text(
            "行程功能上线后会在这里显示",
            style = MaterialTheme.typography.labelSmall,
            color = BabyTheme.colors.text3,
        )
    }
}
