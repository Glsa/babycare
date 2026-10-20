package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.SupplementLogPayload
import com.babycare.app.data.model.SupplementPlanPayload
import com.babycare.app.data.model.decodePayloadOrNull
import java.time.LocalDate
import java.time.ZoneId

/** 一个当日补剂任务（方案 × 时间点） */
data class SuppTask(
    val entity: RecordEntity,
    val plan: SupplementPlanPayload,
    val timePoint: String,
    val done: Boolean,
)

/** 补剂当日任务推导（FR-10）：方案 = SUPPLEMENT_PLAN 记录，打卡 = SUPPLEMENT_LOG 记录 */
object SupplementKit {

    /** 进行中的方案（!stopped）；坏 payload / 停用的跳过 */
    fun activePlans(records: List<RecordEntity>): List<Pair<RecordEntity, SupplementPlanPayload>> =
        records.mapNotNull { r ->
            (decodePayloadOrNull(r.type, r.payload) as? SupplementPlanPayload)
                ?.takeUnless { it.stopped }
                ?.let { r to it }
        }

    /**
     * 当日任务（Q2/Q3）：开始日期 ≤ 今天才出；时间点按字符串排序展开；
     * done = 有 log（方案名相同 && planTime 相同 && takenAt 落今天）。
     */
    fun todayTasks(
        plans: List<Pair<RecordEntity, SupplementPlanPayload>>,
        today: LocalDate,
        todayLogs: List<RecordEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<SuppTask> {
        val logs = todayLogs.mapNotNull { r ->
            decodePayloadOrNull(r.type, r.payload)?.let { it as? SupplementLogPayload }?.let { r to it }
        }
        return plans.flatMap { (entity, plan) ->
            if (TimeKit.toLocalDate(plan.startDate, zone).isAfter(today)) return@flatMap emptyList()
            plan.timePoints.sorted().map { tp ->
                SuppTask(
                    entity = entity,
                    plan = plan,
                    timePoint = tp,
                    done = logs.any { (l, p) ->
                        p.name == plan.name && p.planTime == tp &&
                            TimeKit.toLocalDate(l.occurredAt, zone) == today
                    },
                )
            }
        }
    }
}
