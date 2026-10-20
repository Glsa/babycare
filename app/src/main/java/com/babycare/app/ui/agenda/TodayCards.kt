package com.babycare.app.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.EduCheckinPayload
import com.babycare.app.domain.DailyKit
import com.babycare.app.data.model.SupplementLogPayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.domain.AgeCalc
import com.babycare.app.domain.DoseStatus
import com.babycare.app.domain.DoseView
import com.babycare.app.domain.EduItem
import com.babycare.app.domain.EduKit
import com.babycare.app.domain.SuppTask
import com.babycare.app.domain.SupplementKit
import com.babycare.app.domain.TimeKit
import com.babycare.app.domain.Vaccines
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.today.TypeBadge
import java.time.LocalDate
import java.time.ZoneId

/**
 * 今日三张来源卡的推导胶水（今天页与行程 Tab 共用，一次 remember 算好）。
 * logs 传当日 records（今天页）或全量补剂 log（行程页）都行：只按「落在今天」过滤。
 */
internal data class TodayTaskBundle(
    val nearDoses: List<DoseView>,
    val suppTasks: List<SuppTask>,
    val eduPicks: List<EduItem>,
    val eduDoneTitles: Set<String>,
)

internal fun buildTodayTasks(
    birthday: LocalDate?,
    today: LocalDate,
    zone: ZoneId,
    plans: List<RecordEntity>,
    logs: List<RecordEntity>,
    vaccines: List<RecordEntity>,
    edus: List<RecordEntity>,
): TodayTaskBundle {
    val near = birthday?.let { Vaccines.nearDoses(Vaccines.views(it, today, vaccines), today) } ?: emptyList()
    val todayLogs = logs.filter { TimeKit.toLocalDate(it.occurredAt, zone) == today }
    val suppTasks = SupplementKit.todayTasks(SupplementKit.activePlans(plans), today, todayLogs, zone)
    fun eduTitles(date: LocalDate): Set<String> = edus
        .filter { TimeKit.toLocalDate(it.occurredAt, zone) == date }
        .mapNotNull { (decodePayloadOrNull(it.type, it.payload) as? EduCheckinPayload)?.item }
        .toSet()
    val picks = birthday?.let {
        EduKit.picks(EduKit.pool(AgeCalc.monthAge(it, today)), today, eduTitles(today.minusDays(1)))
    } ?: emptyList()
    return TodayTaskBundle(nearDoses = near, suppTasks = suppTasks, eduPicks = picks, eduDoneTitles = eduTitles(today))
}

/** 找今天这顿补剂已打的卡（取消打卡用）；没有返回 null */
internal fun findSuppLog(
    logs: List<RecordEntity>,
    task: SuppTask,
    today: LocalDate,
    zone: ZoneId,
): RecordEntity? = logs.firstOrNull { r ->
    val p = decodePayloadOrNull(r.type, r.payload) as? SupplementLogPayload
    p != null && p.name == task.plan.name && p.planTime == task.timePoint &&
        TimeKit.toLocalDate(r.occurredAt, zone) == today
}

/** 找今天这个早教项目已打的卡 */
internal fun findEduCheckin(
    edus: List<RecordEntity>,
    item: EduItem,
    today: LocalDate,
    zone: ZoneId,
): RecordEntity? = edus.firstOrNull { r ->
    val p = decodePayloadOrNull(r.type, r.payload) as? EduCheckinPayload
    p != null && p.item == item.title && TimeKit.toLocalDate(r.occurredAt, zone) == today
}

/**
 * 今日三张来源卡（FR-12 回填，0008）：疫苗临近（置顶）→ 今日补剂 → 今日早教。
 * 今天页与行程 Tab 共用；列表为空时调用方不渲染。
 */

/** 早教中心卡（成长页）：今日推荐 2~3 项打卡 + 本周打卡次数 */
@Composable
internal fun EduCenterCard(
    picks: List<EduItem>,
    doneTitles: Set<String>,
    weekCount: Int,
    onToggle: (EduItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BabyTheme.colors
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "🎨 早教中心",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text("本周打卡 $weekCount 次", style = MaterialTheme.typography.labelSmall, color = c.text2)
            }
            if (picks.isEmpty()) {
                Text(
                    "月龄池暂无推荐项目（12 月龄后的池子待扩充）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text3,
                )
            }
            picks.forEach { item ->
                val checked = item.title in doneTitles
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.surfaceAlt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckCircle(done = checked, onToggle = { onToggle(item) })
                        Spacer(Modifier.width(10.dp))
                        TypeBadge(item.category, c.edu, c.eduSoft)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (checked) c.text3 else c.text1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 26dp 打勾圆钮（与 AgendaRow 同视觉，抽出来三卡共用） */
@Composable
private fun CheckCircle(done: Boolean, onToggle: () -> Unit) {
    val c = BabyTheme.colors
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (done) c.primary else Color.Transparent)
            .border(1.5.dp, if (done) c.primary else c.text3, CircleShape)
            .clickable { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        if (done) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** 疫苗临近：待种（已到期）∪ 7 天内；行点击跳成长页标已种 */
@Composable
internal fun VaccineNearCard(
    doses: List<DoseView>,
    onOpenGrowth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BabyTheme.colors
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpenGrowth),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "💉 疫苗临近",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text("${doses.size} 剂", style = MaterialTheme.typography.labelSmall, color = c.text2)
            }
            doses.take(3).forEach { v ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.surfaceAlt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("💉", fontSize = 18.sp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${v.plan.vaccine} 第${v.plan.dose}剂",
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.text1,
                            )
                            Text(
                                "建议 ${TimeKit.formatMonthDay(v.plan.date)} · ${v.plan.monthLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = c.text2,
                            )
                        }
                        // 已到期的剂次给醒目提示；未到期的建议日已在副标题里
                        if (v.status == DoseStatus.DUE) TypeBadge("已到期", c.vaccine, c.vaccineSoft)
                    }
                }
            }
            Text("去标记已种 ›", style = MaterialTheme.typography.labelMedium, color = c.primary)
        }
    }
}

/** 今日补剂：方案 × 时间点任务，点圆钮打勾/取消；「管理」开方案弹层 */
@Composable
internal fun SupplementTodayCard(
    tasks: List<SuppTask>,
    onToggle: (SuppTask) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BabyTheme.colors
    val pending = tasks.count { !it.done }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "💊 今日补剂",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onManage, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("管理", style = MaterialTheme.typography.labelMedium, color = c.primary)
                }
                Text("剩 $pending 顿", style = MaterialTheme.typography.labelSmall, color = c.text2)
            }
            tasks.take(4).forEach { t ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.surfaceAlt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckCircle(done = t.done, onToggle = { onToggle(t) })
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    t.timePoint,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c.text2,
                                )
                                if (t.plan.dose.isNotBlank()) {
                                    Text(t.plan.dose, style = MaterialTheme.typography.labelSmall, color = c.text3)
                                }
                            }
                            Text(
                                t.plan.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (t.done) c.text3 else c.text1,
                            )
                        }
                    }
                }
            }
            if (tasks.size > 4) {
                Text("还有 ${tasks.size - 4} 顿", style = MaterialTheme.typography.labelMedium, color = c.primary)
            }
        }
    }
}

/** 今日早教：推荐 2~3 项，点圆钮打卡/取消 */
@Composable
internal fun EduTodayCard(
    picks: List<EduItem>,
    doneTitles: Set<String>,
    onToggle: (EduItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BabyTheme.colors
    val done = picks.count { it.title in doneTitles }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "🎨 今日早教",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text("$done/${picks.size}", style = MaterialTheme.typography.labelSmall, color = c.text2)
            }
            picks.forEach { item ->
                val checked = item.title in doneTitles
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.surfaceAlt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckCircle(done = checked, onToggle = { onToggle(item) })
                        Spacer(Modifier.width(10.dp))
                        TypeBadge(item.category, c.edu, c.eduSoft)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (checked) c.text3 else c.text1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 每日必做（0013）：任务清单当日勾选，全部展示不截断；「管理」开任务管理弹层 */
@Composable
internal fun DailyTasksCard(
    tasks: List<RecordEntity>,
    logs: List<RecordEntity>,
    dateKey: String,
    onToggle: (task: RecordEntity, log: RecordEntity?) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BabyTheme.colors
    val pending = tasks.count { DailyKit.dayLog(logs, it.uuid, dateKey) == null }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "✅ 每日必做",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onManage, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("管理", style = MaterialTheme.typography.labelMedium, color = c.primary)
                }
                Text("剩 $pending 项", style = MaterialTheme.typography.labelSmall, color = c.text2)
            }
            tasks.forEach { task ->
                val name = DailyKit.taskPayload(task)?.name ?: return@forEach
                val log = DailyKit.dayLog(logs, task.uuid, dateKey)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.surfaceAlt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckCircle(done = log != null, onToggle = { onToggle(task, log) })
                        Spacer(Modifier.width(10.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (log != null) c.text3 else c.text1,
                        )
                    }
                }
            }
        }
    }
}
