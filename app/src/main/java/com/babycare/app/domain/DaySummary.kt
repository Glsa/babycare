package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.BathPayload
import com.babycare.app.data.model.DiaperPayload
import com.babycare.app.data.model.EduCheckinPayload
import com.babycare.app.data.model.FeedFormulaPayload
import com.babycare.app.data.model.FeedNursingPayload
import com.babycare.app.data.model.SleepPayload
import com.babycare.app.data.model.decodePayloadOrNull

/** 当日汇总结果（今天页/日历日详情共用） */
data class DaySummary(
    val formulaMl: Int = 0,
    val formulaCount: Int = 0,
    val nursingCount: Int = 0,
    val nursingMinutes: Int = 0,
    val sleepMillis: Long = 0,
    val sleepOngoing: Boolean = false,
    val peeCount: Int = 0,
    val poopCount: Int = 0,
    val bathDone: Boolean = false,
    val eduCount: Int = 0,
)

/**
 * 当日汇总。口径：
 * - 奶量 = 瓶喂毫升合计（0012 起奶粉/母乳瓶喂同质累加）；亲喂按次数/时长并列，不折算毫升（Q-7）
 * - 睡眠合计含进行中（按 now 折算）
 * - 不认识的类型/坏数据跳过，不影响其余汇总
 */
fun computeDaySummary(records: List<RecordEntity>, now: Long): DaySummary {
    var formulaMl = 0
    var formulaCount = 0
    var nursingCount = 0
    var nursingMinutes = 0
    var sleepMillis = 0L
    var sleepOngoing = false
    var peeCount = 0
    var poopCount = 0
    var bathDone = false
    var eduCount = 0

    for (r in records) {
        when (val p = decodePayloadOrNull(r.type, r.payload) ?: continue) {
            is FeedFormulaPayload -> {
                formulaMl += p.ml
                formulaCount++
            }
            is FeedNursingPayload -> {
                nursingCount++
                nursingMinutes += p.leftMinutes + p.rightMinutes
            }
            is SleepPayload -> {
                val end = p.endAt ?: now
                sleepMillis += (end - p.startAt).coerceAtLeast(0)
                if (p.endAt == null) sleepOngoing = true
            }
            is DiaperPayload -> {
                if (p.pee) peeCount++
                if (p.poop) poopCount++
            }
            is BathPayload -> bathDone = true
            is EduCheckinPayload -> eduCount++
            else -> Unit // 其余类型不进当日汇总
        }
    }
    return DaySummary(
        formulaMl = formulaMl,
        formulaCount = formulaCount,
        nursingCount = nursingCount,
        nursingMinutes = nursingMinutes,
        sleepMillis = sleepMillis,
        sleepOngoing = sleepOngoing,
        peeCount = peeCount,
        poopCount = poopCount,
        bathDone = bathDone,
        eduCount = eduCount,
    )
}
