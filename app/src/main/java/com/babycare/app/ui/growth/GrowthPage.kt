package com.babycare.app.ui.growth

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.EduCheckinPayload
import com.babycare.app.data.model.VaccineDosePayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.domain.AgeCalc
import com.babycare.app.domain.DoseStatus
import com.babycare.app.domain.DoseView
import com.babycare.app.domain.EduKit
import com.babycare.app.domain.GrowthKit
import com.babycare.app.domain.GrowthLatest
import com.babycare.app.domain.TimeKit
import com.babycare.app.domain.Vaccines
import com.babycare.app.domain.buildTimelineItems
import com.babycare.app.ui.agenda.EduCenterCard
import com.babycare.app.ui.agenda.findEduCheckin
import com.babycare.app.ui.onboarding.OnboardingScreen
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.theme.recordColors
import com.babycare.app.ui.today.RecordSheet
import com.babycare.app.ui.today.RecordSheetHost
import com.babycare.app.ui.today.TimelineRow
import com.babycare.app.ui.today.TodayViewModel
import com.babycare.app.ui.today.TypeBadge
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 成长页（FR-7/FR-9，帧 06 前两段）：生长数据卡 → 生长记录 → 疫苗进度 → 剂次列表 */
@Composable
fun GrowthPage(
    todayVm: TodayViewModel,
    vm: GrowthViewModel,
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
    // 从剂次行点入的未种计划（有记录的走 RecordSheet.Vaccine 编辑路径）
    var markPlan by remember { mutableStateOf<DoseView?>(null) }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val c = BabyTheme.colors

    val latest = remember(state.growthRecords) { GrowthKit.latest(state.growthRecords) }
    val historyItems = remember(state.growthRecords) { buildTimelineItems(state.growthRecords) }
    val doseViews = remember(baby.birthday, state.vaccineRecords) {
        Vaccines.views(baby.birthday, today, state.vaccineRecords)
    }
    val progress = remember(doseViews) { Vaccines.progress(doseViews) }
    val next = remember(doseViews) { Vaccines.nextDose(doseViews) }
    // 早教中心：今日推荐（与今天页同算法）+ 本周打卡数
    fun eduTitles(date: LocalDate): Set<String> = state.eduCheckins
        .filter { TimeKit.toLocalDate(it.occurredAt, zone) == date }
        .mapNotNull { (decodePayloadOrNull(it.type, it.payload) as? EduCheckinPayload)?.item }
        .toSet()
    val eduPicks = remember(baby.birthday, state.eduCheckins) {
        EduKit.picks(EduKit.pool(AgeCalc.monthAge(baby.birthday, today)), today, eduTitles(today.minusDays(1)))
    }
    val eduDoneToday = remember(state.eduCheckins) { eduTitles(today) }
    val weekCount = remember(state.eduCheckins) {
        val from = today.minusDays(6)
        state.eduCheckins.count {
            val d = TimeKit.toLocalDate(it.occurredAt, zone)
            !d.isBefore(from) && !d.isAfter(today)
        }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🌱 成长", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TypeBadge("第${AgeCalc.dayNumber(today, baby.birthday)}天", c.primary, c.primarySoft)
                Text(
                    AgeCalc.monthAgeText(baby.birthday, today),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.text2,
                )
            }
        }
        item {
            GrowthLatestCard(
                latest = latest,
                zone = zone,
                onAdd = { activeSheet = RecordSheet.Growth(null) },
            )
        }
        item {
            Text("生长记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
        if (historyItems.isEmpty()) {
            item {
                Text(
                    "还没有生长记录，点上面「记一笔」开始",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text3,
                )
            }
        } else {
            items(historyItems, key = { it.entity.uuid }) { item ->
                TimelineRow(
                    item = item,
                    onClick = { activeSheet = RecordSheet.Detail(item) },
                    authorLabel = { todayVm.authorLabel(item.entity.author) },
                )
            }
        }
        item {
            VaccineProgressCard(done = progress.first, total = progress.second, next = next, today = today)
        }
        item {
            EduCenterCard(
                picks = eduPicks,
                doneTitles = eduDoneToday,
                weekCount = weekCount,
                onToggle = { item ->
                    todayVm.toggleEduCheckin(findEduCheckin(state.eduCheckins, item, today, zone), item.category, item.title)
                },
            )
        }
        item {
            Text("接种计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
        items(doseViews, key = { it.plan.key }) { view ->
            DoseRow(
                view = view,
                today = today,
                onClick = {
                    if (view.record != null) activeSheet = RecordSheet.Vaccine(view.record)
                    else markPlan = view
                },
            )
        }
    }

    RecordSheetHost(
        sheet = activeSheet,
        vm = todayVm,
        onDismiss = { activeSheet = null },
        onEdit = { activeSheet = it },
    )

    // 未种路径：计划预填（名称剂次带出、提示计划日），可标已种或不接种
    markPlan?.let { view ->
        val planMillis = view.plan.date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        VaccineSheet(
            vaccine = view.plan.vaccine,
            dose = view.plan.dose,
            editing = null,
            planDate = planMillis,
            onDone = { date, location, note ->
                todayVm.saveVaccineDose(view.plan.vaccine, view.plan.dose, view.plan.key, date, location, note)
            },
            onSkip = {
                todayVm.markVaccineSkipped(view.plan.vaccine, view.plan.dose, view.plan.key, planMillis)
            },
            onDismiss = { markPlan = null },
        )
    }
}

/** 生长数据卡：三项最新值 + 记录日期；缺项显示占位 */
@Composable
private fun GrowthLatestCard(
    latest: GrowthLatest,
    zone: ZoneId,
    onAdd: () -> Unit,
) {
    val c = BabyTheme.colors
    Surface(shape = RoundedCornerShape(20.dp), color = c.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GrowthMetric(
                    emoji = "⚖️",
                    label = "体重",
                    value = latest.weight?.let { fmtKg(it.second.weightKg!!) },
                    unit = "kg",
                    date = latest.weight?.first?.occurredAt,
                    zone = zone,
                    modifier = Modifier.weight(1f),
                )
                GrowthMetric(
                    emoji = "📏",
                    label = "身高",
                    value = latest.height?.let { fmtCm(it.second.heightCm!!) },
                    unit = "cm",
                    date = latest.height?.first?.occurredAt,
                    zone = zone,
                    modifier = Modifier.weight(1f),
                )
                GrowthMetric(
                    emoji = "🎯",
                    label = "头围",
                    value = latest.head?.let { fmtCm(it.second.headCm!!) },
                    unit = "cm",
                    date = latest.head?.first?.occurredAt,
                    zone = zone,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("＋ 记一笔生长") }
        }
    }
}

@Composable
private fun GrowthMetric(
    emoji: String,
    label: String,
    value: String?,
    unit: String,
    date: Long?,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val c = BabyTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "$emoji $label",
            style = MaterialTheme.typography.labelMedium,
            color = c.text2,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (value != null) c.growth else c.text3,
            )
            if (value != null) {
                Text(unit, style = MaterialTheme.typography.labelMedium, color = c.text3, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Text(
            date?.let { TimeKit.formatMonthDay(TimeKit.toLocalDate(it, zone)) } ?: "还没记录",
            style = MaterialTheme.typography.labelSmall,
            color = c.text3,
        )
    }
}

/** 疫苗进度卡：已种 X/22 进度条 + 下一剂 */
@Composable
private fun VaccineProgressCard(done: Int, total: Int, next: DoseView?, today: LocalDate) {
    val c = BabyTheme.colors
    Surface(shape = RoundedCornerShape(20.dp), color = c.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("💉 疫苗接种", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(
                    "已种 $done/$total",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.text2,
                )
            }
            // 进度条：底 primarySoft，填充主色
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.primarySoft)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(if (total > 0) done.toFloat() / total else 0f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.primary)
                )
            }
            next?.let { n ->
                val days = ChronoUnit.DAYS.between(today, n.plan.date)
                val timing = when {
                    days > 0 -> "${days} 天后"
                    days == 0L -> "就是今天"
                    else -> "已到期 ${-days} 天"
                }
                Text(
                    "下一剂 · ${n.plan.vaccine} 第${n.plan.dose}剂 · ${TimeKit.formatMonthDay(n.plan.date)}（$timing）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text1,
                )
            } ?: Text(
                "全部剂次已完成 🎉",
                style = MaterialTheme.typography.bodyMedium,
                color = c.text2,
            )
        }
    }
}

/** 剂次行：疫苗名+剂次 / 月龄+计划日 | 状态（已种带日期 / 待种 / 未到月龄 / 不接种） */
@Composable
private fun DoseRow(view: DoseView, today: LocalDate, onClick: () -> Unit) {
    val c = BabyTheme.colors
    val (vcColor, vcSoft) = recordColors(RecordTypes.VACCINE_DOSE, c)
    val p = view.record?.let { decodePayloadOrNull(it.type, it.payload) as? VaccineDosePayload }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = c.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${view.plan.vaccine} 第${view.plan.dose}剂",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (view.status == DoseStatus.SKIPPED) c.text3 else c.text1,
                    textDecoration = if (view.status == DoseStatus.SKIPPED) TextDecoration.LineThrough else null,
                )
                Text(
                    "${view.plan.monthLabel} · 计划 ${TimeKit.formatMonthDay(view.plan.date)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.text3,
                )
            }
            when (view.status) {
                DoseStatus.DONE -> TypeBadge(
                    "已种 ${p?.let { TimeKit.formatMonthDay(TimeKit.toLocalDate(it.date)) } ?: ""}".trim(),
                    vcColor,
                    vcSoft,
                )
                DoseStatus.DUE -> TypeBadge("待种", c.primary, c.primarySoft)
                DoseStatus.NOT_DUE -> TypeBadge("未到月龄", c.text2, c.surfaceAlt)
                DoseStatus.SKIPPED -> Text(
                    "不接种",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.text3,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
        }
    }
}
