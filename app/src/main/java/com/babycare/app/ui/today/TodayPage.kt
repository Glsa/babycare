package com.babycare.app.ui.today

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.babycare.app.BabycareApplication
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.SleepPayload
import com.babycare.app.data.model.SupplementPlanPayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.domain.AgeCalc
import com.babycare.app.domain.AgendaKit
import com.babycare.app.domain.DailyKit
import com.babycare.app.domain.FeedIntervalKit
import com.babycare.app.domain.TimeKit
import com.babycare.app.domain.buildTimelineItems
import com.babycare.app.ui.agenda.DailyTasksCard
import com.babycare.app.ui.agenda.EduTodayCard
import com.babycare.app.ui.agenda.SupplementPlanEditSheet
import com.babycare.app.ui.agenda.SupplementPlanListSheet
import com.babycare.app.ui.agenda.SupplementTodayCard
import com.babycare.app.ui.agenda.TodayAgendaCard
import com.babycare.app.ui.agenda.VaccineNearCard
import com.babycare.app.ui.agenda.buildTodayTasks
import com.babycare.app.ui.agenda.findEduCheckin
import com.babycare.app.ui.agenda.findSuppLog
import com.babycare.app.ui.onboarding.OnboardingScreen
import com.babycare.app.ui.theme.BabyTheme
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.ZoneId

/** 今天页（0013 重排：概要 → 进行中计时 → 快捷记录 → 每日必做 → 补剂/早教/疫苗 → 行程 → 时间轴） */
@Composable
fun TodayPage(
    vm: TodayViewModel,
    snackbar: SnackbarHostState,
    onOpenAgenda: () -> Unit,
    onOpenGrowth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.ui.collectAsState()

    if (!state.loaded) {
        // Room 首次加载的短瞬留白，避免误闪首启引导
        Box(modifier)
        return
    }
    val baby = state.baby
    if (baby == null) {
        OnboardingScreen(
            onCreate = { nickname, birthday, gender -> vm.createBaby(nickname, birthday, gender) },
            modifier = modifier,
        )
        return
    }

    var activeSheet by remember { mutableStateOf<RecordSheet?>(null) }
    // 补剂方案管理（与行程 Tab 同款两层弹层）
    var showPlans by remember { mutableStateOf(false) }
    var planEdit by remember { mutableStateOf<Pair<RecordEntity, SupplementPlanPayload>?>(null) }
    var planCreate by remember { mutableStateOf(false) }
    // 每日必做管理弹层（0013）
    var showDailyManage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val timeline = remember(state.records) { buildTimelineItems(state.records) }
    // 今日行程卡：今天的 AGENDA 记录就在当日查询结果里，内存过滤即可
    val todayAgenda = remember(state.records, state.date) { AgendaKit.dayAgendas(state.records, state.date) }
    // 三来源卡（疫苗临近/补剂/早教）：当日 records 里就含今天的补剂打卡
    val tasks = remember(
        baby.birthday, state.date, state.records, state.supplementPlans, state.vaccineRecords, state.eduCheckins,
    ) {
        buildTodayTasks(
            baby.birthday, state.date, zone,
            state.supplementPlans, state.records, state.vaccineRecords, state.eduCheckins,
        )
    }
    val ongoingSleepPayload = remember(state.ongoingSleep) {
        state.ongoingSleep?.let { decodePayloadOrNull(it.type, it.payload) as? SleepPayload }
    }

    // 计时常驻通知需要权限（API 33+）；拒绝不阻断计时，只是不显示通知
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Widget 深链：pendingSheet 键 → 直开弹层；bath = 即记一笔（与 App 内快捷条同口径）
    LaunchedEffect(Unit) {
        (context.applicationContext as BabycareApplication).pendingSheet.filterNotNull().collect { key ->
            if (key == "bath") {
                vm.saveBath(System.currentTimeMillis(), "")
                scope.launch { snackbar.showSnackbar("已记录 🛁 洗澡") }
            } else {
                RecordSheet.fromWidgetKey(key)?.let { activeSheet = it }
            }
            (context.applicationContext as BabycareApplication).pendingSheet.value = null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp)) {
            Text(
                "${TimeKit.formatMonthDay(state.date)} ${TimeKit.weekdayText(state.date)}",
                style = MaterialTheme.typography.labelMedium,
                color = BabyTheme.colors.text3,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(baby.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TypeBadge(
                    "第${AgeCalc.dayNumber(state.date, baby.birthday)}天",
                    BabyTheme.colors.primary,
                    BabyTheme.colors.primarySoft,
                )
                Text(
                    AgeCalc.monthAgeText(baby.birthday, state.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = BabyTheme.colors.text2,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeBadge(
                    TimeKit.sinceFeedText(state.lastFeed?.occurredAt, state.nowMillis),
                    BabyTheme.colors.feed,
                    BabyTheme.colors.feedSoft,
                )
                // 距下次喂奶倒计时（0011）：间隔设置在「我的」，超时换红色；无喂奶记录不显示
                val app = context.applicationContext as BabycareApplication
                val interval by app.feedInterval.collectAsState()
                val nursingStart = state.nursingTimer?.startAt
                val lastFeedStart = maxOf(
                    nursingStart ?: Long.MIN_VALUE,
                    state.lastFeed?.occurredAt ?: Long.MIN_VALUE,
                ).takeIf { it != Long.MIN_VALUE }
                FeedIntervalKit.badgeText(lastFeedStart, state.nowMillis, interval, zone)?.let { (text, overdue) ->
                    TypeBadge(
                        text,
                        if (overdue) MaterialTheme.colorScheme.error else BabyTheme.colors.feed,
                        if (overdue) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else BabyTheme.colors.feedSoft,
                    )
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryCard(state.summary) }
            // ---- 进行中计时（0004）----
            state.ongoingSleep?.let { e ->
                item(key = "ongoing-sleep") {
                    ongoingSleepPayload?.let { p ->
                        SleepOngoingCard(
                            startAt = p.startAt,
                            nowMillis = state.nowMillis,
                            onOpenStop = { activeSheet = RecordSheet.StopSleep(e) },
                            onCancel = { vm.cancelSleep() },
                        )
                    }
                }
            }
            state.nursingTimer?.let { st ->
                item(key = "ongoing-nursing") {
                    NursingOngoingCard(
                        state = st,
                        nowMillis = state.nowMillis,
                        onSwitchSide = { vm.switchNursingSide() },
                        onTogglePause = { vm.toggleNursingPause() },
                        onStop = {
                            vm.stopNursing()
                            scope.launch { snackbar.showSnackbar("已记录 🤱 亲喂") }
                        },
                        onCancel = { vm.cancelNursing() },
                    )
                }
            }
            // ---- 快捷记录（0013 上移：高频操作置顶） ----
            item {
                QuickActions(
                    onFormula = { activeSheet = RecordSheet.Formula(null) },
                    onNursing = {
                        if (state.nursingTimer != null) activeSheet = RecordSheet.NursingTimer
                        else {
                            ensureNotifPermission()
                            activeSheet = RecordSheet.NursingStart
                        }
                    },
                    onDiaper = { activeSheet = RecordSheet.Diaper(null) },
                    onSleep = {
                        if (state.ongoingSleep != null) activeSheet = RecordSheet.StopSleep(state.ongoingSleep!!)
                        else {
                            ensureNotifPermission()
                            vm.startSleep()
                            scope.launch { snackbar.showSnackbar("😴 开始睡眠计时") }
                        }
                    },
                    onBath = {
                        vm.saveBath(System.currentTimeMillis(), "")
                        scope.launch { snackbar.showSnackbar("已记录 🛁 洗澡") }
                    },
                    nursingLabel = if (state.nursingTimer != null) "计时中" else "亲喂",
                    sleepLabel = if (state.ongoingSleep != null) "结束" else "睡眠",
                )
            }
            // ---- 每日必做（0013）：快捷记录下方，全部展示 ----
            val dailySorted = DailyKit.sortedTasks(state.dailyTasks)
            if (dailySorted.isNotEmpty()) {
                item(key = "daily-tasks") {
                    DailyTasksCard(
                        tasks = dailySorted,
                        logs = state.dailyLogs,
                        dateKey = state.date.toString(),
                        onToggle = { task, log -> vm.toggleDailyTask(log, task) },
                        onManage = { showDailyManage = true },
                    )
                }
            }
            // ---- 三来源卡（0008 回填；为空不占位；0013 起疫苗挪到补剂/早教之后） ----
            if (tasks.suppTasks.isNotEmpty()) {
                item(key = "today-supp") {
                    SupplementTodayCard(
                        tasks = tasks.suppTasks,
                        onToggle = { t ->
                            vm.toggleSupplementLog(findSuppLog(state.records, t, state.date, zone), t.plan.name, t.timePoint)
                        },
                        onManage = { showPlans = true },
                    )
                }
            }
            if (tasks.eduPicks.isNotEmpty()) {
                item(key = "today-edu") {
                    EduTodayCard(
                        picks = tasks.eduPicks,
                        doneTitles = tasks.eduDoneTitles,
                        onToggle = { item ->
                            vm.toggleEduCheckin(findEduCheckin(state.eduCheckins, item, state.date, zone), item.category, item.title)
                        },
                    )
                }
            }
            if (tasks.nearDoses.isNotEmpty()) {
                item(key = "near-vaccine") {
                    VaccineNearCard(doses = tasks.nearDoses, onOpenGrowth = onOpenGrowth)
                }
            }
            if (todayAgenda.isNotEmpty()) {
                item(key = "today-agenda") {
                    TodayAgendaCard(
                        items = todayAgenda,
                        onToggleDone = { vm.toggleAgendaDone(it.entity) },
                        onOpenAll = onOpenAgenda,
                    )
                }
            }
            item {
                Text(
                    "今日记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = BabyTheme.colors.text1,
                )
            }
            if (timeline.isEmpty()) {
                item {
                    Text(
                        "今天还没有记录，点上面的按钮记一笔吧",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BabyTheme.colors.text3,
                    )
                }
            } else {
                items(timeline, key = { it.entity.uuid }) { item ->
                    TimelineRow(
                        item = item,
                        onClick = { activeSheet = RecordSheet.Detail(item) },
                        authorLabel = { vm.authorLabel(item.entity.author) },
                    )
                }
            }
        }
    }

    RecordSheetHost(
        sheet = activeSheet,
        vm = vm,
        onDismiss = { activeSheet = null },
        onEdit = { activeSheet = it },
    )

    // 补剂方案管理（与行程 Tab 同款）
    if (showPlans) {
        SupplementPlanListSheet(
            planRecords = state.supplementPlans,
            onEdit = { planEdit = it },
            onCreate = { planCreate = true },
            onToggleStopped = { vm.toggleSupplementStopped(it) },
            onDismiss = { showPlans = false },
        )
    }
    if (planCreate || planEdit != null) {
        val editing = planEdit
        SupplementPlanEditSheet(
            editing = editing,
            onSave = { name, dose, timePoints, startDate ->
                val e = editing?.first
                if (e != null) vm.updateSupplementPlan(e, name, dose, timePoints, startDate)
                else vm.saveSupplementPlan(name, dose, timePoints, startDate)
                planEdit = null
                planCreate = false
            },
            onDelete = editing?.first?.let { e -> { vm.delete(e) } },
            onDismiss = { planEdit = null; planCreate = false },
        )
    }

    // 每日必做管理（0013）
    if (showDailyManage) {
        DailyTaskManageSheet(
            tasks = state.dailyTasks,
            onAdd = { vm.addDailyTask(it) },
            onDelete = { vm.deleteDailyTask(it) },
            onDismiss = { showDailyManage = false },
        )
    }
}
