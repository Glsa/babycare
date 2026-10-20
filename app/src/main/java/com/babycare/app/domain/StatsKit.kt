package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.DiaperPayload
import com.babycare.app.data.model.EduCheckinPayload
import com.babycare.app.data.model.FeedFormulaPayload
import com.babycare.app.data.model.FeedNursingPayload
import com.babycare.app.data.model.SleepPayload
import com.babycare.app.data.model.decodePayloadOrNull
import java.time.LocalDate
import java.time.ZoneId

/** 一天的统计值（0015 统计页） */
data class DayStat(
    val date: LocalDate,
    val formulaMl: Int = 0,
    val formulaCount: Int = 0,
    val nursingCount: Int = 0,
    val nursingMinutes: Int = 0,
    val peeCount: Int = 0,
    val poopCount: Int = 0,
    val sleepMillis: Long = 0,
    /** 夜觉时长（sleep tag=夜觉），小睡 = sleepMillis - nightMillis */
    val nightMillis: Long = 0,
    val eduCount: Int = 0,
)

/** 生长曲线指标（0015） */
enum class GrowthMetric(val label: String, val unit: String) {
    WEIGHT("体重", "kg"),
    HEIGHT("身高", "cm"),
    HEAD("头围", "cm"),
}

/**
 * 统计聚合（0015 · FR-16）：纯函数。口径与 computeDaySummary 一致，多两条：
 * - 按天分桶，睡眠按 startAt 落桶（跨天夜觉整体算开始那天，与今天页同口径）
 * - 夜觉单独拆出（tag = "夜觉"）
 */
object StatsKit {

    /**
     * 全量记录 → 连续日序列。
     * days = 7/30：从 today 往回逐日补零；null：从最早记录日到 today（无任何记录返回 [today]）。
     */
    fun dailyStats(
        records: List<RecordEntity>,
        days: Int?,
        zone: ZoneId,
        today: LocalDate,
        now: Long,
    ): List<DayStat> {
        val firstDay = when {
            days != null -> today.minusDays((days - 1).toLong())
            else -> records.minOfOrNull { TimeKit.toLocalDate(it.occurredAt, zone) }?.let { minOf(it, today) } ?: today
        }
        val byDay = mutableMapOf<LocalDate, DayStat>()
        for (offset in 0..today.toEpochDay() - firstDay.toEpochDay()) {
            val d = LocalDate.ofEpochDay(firstDay.toEpochDay() + offset)
            byDay[d] = DayStat(date = d)
        }
        for (r in records) {
            val day = TimeKit.toLocalDate(r.occurredAt, zone)
            val cur = byDay[day] ?: continue // 窗口外（补零档只看近 N 天）
            when (val p = decodePayloadOrNull(r.type, r.payload) ?: continue) {
                is FeedFormulaPayload -> byDay[day] = cur.copy(
                    formulaMl = cur.formulaMl + p.ml, formulaCount = cur.formulaCount + 1,
                )
                is FeedNursingPayload -> byDay[day] = cur.copy(
                    nursingCount = cur.nursingCount + 1,
                    nursingMinutes = cur.nursingMinutes + p.leftMinutes + p.rightMinutes,
                )
                is SleepPayload -> {
                    val dur = ((p.endAt ?: now) - p.startAt).coerceAtLeast(0)
                    val night = if (p.tag == "夜觉") dur else 0L
                    byDay[day] = cur.copy(sleepMillis = cur.sleepMillis + dur, nightMillis = cur.nightMillis + night)
                }
                is DiaperPayload -> byDay[day] = cur.copy(
                    peeCount = cur.peeCount + if (p.pee) 1 else 0,
                    poopCount = cur.poopCount + if (p.poop) 1 else 0,
                )
                is EduCheckinPayload -> byDay[day] = cur.copy(eduCount = cur.eduCount + 1)
                else -> Unit
            }
        }
        return byDay.values.sortedBy { it.date }
    }

    /** 生长点：(月龄, 值)，按时间升序；一条记录只取该指标非空的值，同月龄多测都保留 */
    fun growthPoints(
        records: List<RecordEntity>,
        birthday: LocalDate,
        metric: GrowthMetric,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Pair<Double, Double>> {
        return records.mapNotNull { r ->
            val p = decodePayloadOrNull(r.type, r.payload) as? com.babycare.app.data.model.GrowthPayload ?: return@mapNotNull null
            val v = when (metric) {
                GrowthMetric.WEIGHT -> p.weightKg
                GrowthMetric.HEIGHT -> p.heightCm
                GrowthMetric.HEAD -> p.headCm
            } ?: return@mapNotNull null
            val monthAge = java.time.temporal.ChronoUnit.DAYS.between(birthday, TimeKit.toLocalDate(r.occurredAt, zone)) / 30.4375
            monthAge to v
        }.sortedBy { it.first }
    }

    /** CSV：日粒度一行，睡眠/夜觉折小时；utf-8 由调用方写入（Excel 中文表头需 BOM，导出时补） */
    fun toCsv(stats: List<DayStat>): String = buildString {
        appendLine("日期,瓶喂ml,瓶喂次数,亲喂次数,亲喂分钟,尿,便,睡眠小时,夜觉小时,早教次数")
        stats.forEach {
            appendLine(
                "${it.date},%d,%d,%d,%d,%d,%d,%.1f,%.1f,%d".format(
                    it.formulaMl, it.formulaCount, it.nursingCount, it.nursingMinutes,
                    it.peeCount, it.poopCount,
                    it.sleepMillis / 3_600_000.0, it.nightMillis / 3_600_000.0,
                    it.eduCount,
                ),
            )
        }
    }
}
