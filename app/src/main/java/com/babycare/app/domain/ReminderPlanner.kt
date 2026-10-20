package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.AgendaPayload
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.SupplementPlanPayload
import com.babycare.app.data.model.VaccineDosePayload
import com.babycare.app.data.model.decodePayloadOrNull
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 提醒开关（0014）：prefs 单 key JSON，非业务记录不随备份迁移 */
@Serializable
data class ReminderSettings(
    val master: Boolean = true,
    val vaccine: Boolean = true,
    val supplement: Boolean = true,
    val agenda: Boolean = true,
    val milestone: Boolean = true,
    val feedInterval: Boolean = true,
) {
    /** 副标题用：开启的分项数 */
    fun enabledCount(): Int = listOf(vaccine, supplement, agenda, milestone, feedInterval).count { it }
}

/** 一条待发提醒：at 触发时刻；dedupeKey 兼作通知 ID（hashCode）保证重发是静默替换不是叠一条 */
data class ReminderSpec(
    val at: Long,
    val dedupeKey: String,
    val title: String,
    val text: String,
    /** 点击直达的底部 Tab 下标（0 今天 / 2 行程 / 3 成长） */
    val tab: Int,
)

/** 规划输入：全部现成数据，无 IO */
data class ReminderInput(
    val birthday: LocalDate,
    /** 全量 VACCINE_DOSE 记录（判已种/不接种） */
    val vaccineRecords: List<RecordEntity>,
    /** 全量 SUPPLEMENT_PLAN 记录 */
    val supplementPlans: List<RecordEntity>,
    /** 全量 AGENDA 记录（体检 + 自定义；done 的不发） */
    val agendas: List<RecordEntity>,
    val feedInterval: FeedIntervalSettings,
    /** 最近一次喂奶开始时刻（计时中亲喂优先，与 badge 同口径）；null = 还没喂过 */
    val lastFeedStart: Long? = null,
)

/**
 * 提醒规划（0014 · FR-13）：纯函数，未来 7 天窗口内全部提醒按 at 升序。
 * 调度器只挂「最近一条」的闹钟，到点发完再排（单闹钟链，见 remind/ReminderCenter）。
 */
object ReminderPlanner {

    /** 9:00 提醒时刻（疫苗/行程/节点统一上午九点，PRD FR-13） */
    private val NINE = LocalTime.of(9, 0)

    /** 补剂时间点解析失败的容忍：坏点跳过不崩 */
    private val timeRegex = Regex("^(\\d{1,2}):(\\d{2})$")

    fun plan(input: ReminderInput, settings: ReminderSettings, now: Long, zone: ZoneId): List<ReminderSpec> {
        if (!settings.master) return emptyList()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val horizon = today.plusDays(7)
        val out = mutableListOf<ReminderSpec>()
        if (settings.vaccine) out += vaccineReminders(input, today, horizon, zone, now)
        if (settings.supplement) out += supplementReminders(input, today, horizon, zone, now)
        if (settings.agenda) out += agendaReminders(input, today, horizon, zone, now)
        if (settings.milestone) out += milestoneReminders(input, today, horizon, zone, now)
        if (settings.feedInterval) out += feedReminder(input, now, zone)
        return out.filter { it.at > now }.sortedBy { it.at }
    }

    /** 闹钟到点时该发的：at ∈ (now-15min, now+60s]。过期太久的不补发（设备关机漏掉的早报不再轰炸） */
    fun due(list: List<ReminderSpec>, now: Long): List<ReminderSpec> =
        list.filter { it.at > now - DUE_GRACE_MS && it.at <= now + 60_000 }

    // ---- 疫苗：未完成剂次的前 3 / 前 1 / 当天，各 9:00 ----

    private fun vaccineReminders(
        input: ReminderInput,
        today: LocalDate,
        horizon: LocalDate,
        zone: ZoneId,
        now: Long,
    ): List<ReminderSpec> {
        val views = Vaccines.views(input.birthday, today, input.vaccineRecords)
        return views.filter {
            (it.status == DoseStatus.NOT_DUE || it.status == DoseStatus.DUE) &&
                !it.plan.date.isBefore(today) && !it.plan.date.isAfter(horizon)
        }.flatMap { v ->
            listOf(-3, -1, 0).mapNotNull { offset ->
                val day = v.plan.date.plusDays(offset.toLong())
                if (day.isBefore(today)) return@mapNotNull null
                val at = atNine(day, zone)
                if (at <= now) return@mapNotNull null
                ReminderSpec(
                    at = at,
                    dedupeKey = "vac:${v.plan.key}:$offset",
                    title = "💉 疫苗提醒",
                    text = "${v.plan.vaccine}第${v.plan.dose}剂 · ${day.monthValue}月${day.dayOfMonth}日接种",
                    tab = TAB_GROWTH,
                )
            }
        }
    }

    // ---- 补剂：生效方案 × 未来 7 天 × 各时间点 ----

    private fun supplementReminders(
        input: ReminderInput,
        today: LocalDate,
        horizon: LocalDate,
        zone: ZoneId,
        now: Long,
    ): List<ReminderSpec> {
        return input.supplementPlans.mapNotNull { e ->
            decodePayloadOrNull(e.type, e.payload) as? SupplementPlanPayload
        }.filter { !it.stopped }.flatMap { p ->
            val startDay = Instant.ofEpochMilli(p.startDate).atZone(zone).toLocalDate()
            (0..6).map { today.plusDays(it.toLong()) }.filter { d -> !d.isBefore(startDay) && !d.isAfter(horizon) }
                .flatMap { d ->
                    p.timePoints.mapNotNull { tp ->
                        val at = atTime(d, tp, zone) ?: return@mapNotNull null
                        if (at <= now) return@mapNotNull null
                        ReminderSpec(
                            at = at,
                            dedupeKey = "supp:${p.name}:${d}:$tp",
                            title = "💊 补剂提醒",
                            text = "${p.name}${if (p.dose.isNotBlank()) " ${p.dose}" else ""} · 到点打卡",
                            tab = TAB_TODAY,
                        )
                    }
                }
        }
    }

    // ---- 行程（体检 + 自定义）：按 remindDaysBefore 的 9:00 ----

    private fun agendaReminders(
        input: ReminderInput,
        today: LocalDate,
        horizon: LocalDate,
        zone: ZoneId,
        now: Long,
    ): List<ReminderSpec> {
        return input.agendas.mapNotNull { e ->
            (decodePayloadOrNull(e.type, e.payload) as? AgendaPayload)?.let { it to e }
        }.filter { (p, _) -> !p.done }.mapNotNull { (p, e) ->
            val day = Instant.ofEpochMilli(e.occurredAt).atZone(zone).toLocalDate()
            if (day.isBefore(today) || day.isAfter(horizon)) return@mapNotNull null
            val triggerDay = day.minusDays(p.remindDaysBefore.toLong())
            if (triggerDay.isBefore(today)) return@mapNotNull null
            val at = atNine(triggerDay, zone)
            if (at <= now) return@mapNotNull null
            ReminderSpec(
                at = at,
                dedupeKey = "agenda:${e.uuid}:$triggerDay",
                title = "🗓️ 行程提醒",
                text = "${p.title} · ${day.monthValue}月${day.dayOfMonth}日",
                tab = TAB_AGENDA,
            )
        }
    }

    // ---- 关键节点：满月/百天/半岁/周岁，前 1 天 9:00 拍照留念 ----

    /** 节点日期：满月/半岁/周岁按月（plusMonths，民俗口径），百天按天 */
    private fun milestoneDates(birthday: LocalDate): List<Pair<String, LocalDate>> = listOf(
        "满月" to birthday.plusMonths(1),
        "百天" to birthday.plusDays(100),
        "半岁" to birthday.plusMonths(6),
        "周岁" to birthday.plusMonths(12),
    )

    private fun milestoneReminders(
        input: ReminderInput,
        today: LocalDate,
        horizon: LocalDate,
        zone: ZoneId,
        now: Long,
    ): List<ReminderSpec> {
        return milestoneDates(input.birthday).mapNotNull { (name, day) ->
            val triggerDay = day.minusDays(1)
            if (triggerDay.isBefore(today) || day.isAfter(horizon)) return@mapNotNull null
            val at = atNine(triggerDay, zone)
            if (at <= now) return@mapNotNull null
            ReminderSpec(
                at = at,
                dedupeKey = "mile:$name",
                title = "📷 $name 到啦",
                text = "明天是$name，拍照留念吧",
                tab = TAB_TODAY,
            )
        }
    }

    // ---- 喂奶间隔：上次开始 + 昼夜间隔（超时排 now+1s 立即发，dedupeKey 随下次喂奶变化） ----

    private fun feedReminder(input: ReminderInput, now: Long, zone: ZoneId): List<ReminderSpec> {
        val start = input.lastFeedStart ?: return emptyList()
        val remain = FeedIntervalKit.remainingMillis(start, now, input.feedInterval, zone)
        return listOf(
            ReminderSpec(
                // 超时钳到 now+1s：既过 plan 的 at>now 过滤，又落在 due 窗口里立刻发
                at = if (remain > 0) now + remain else now + 1_000,
                dedupeKey = "feed:$start",
                title = "🍼 该喂奶了",
                text = "距上次喂奶已过 ${FeedIntervalKit.hmText(now - start)}",
                tab = TAB_TODAY,
            ),
        )
    }

    // ---- 工具 ----

    private fun atNine(day: LocalDate, zone: ZoneId): Long =
        day.atTime(NINE).atZone(zone).toInstant().toEpochMilli()

    /** "HH:mm"（或 "H:mm"）→ 时刻毫秒；坏格式返回 null */
    private fun atTime(day: LocalDate, hhmm: String, zone: ZoneId): Long? {
        val m = timeRegex.find(hhmm.trim()) ?: return null
        val (h, min) = m.destructured
        if (h.toInt() !in 0..23 || min.toInt() !in 0..59) return null
        return day.atTime(h.toInt(), min.toInt()).atZone(zone).toInstant().toEpochMilli()
    }

    /** 过期补发容忍窗 */
    const val DUE_GRACE_MS = 15 * 60_000L

    const val TAB_TODAY = 0
    const val TAB_AGENDA = 2
    const val TAB_GROWTH = 3
}
