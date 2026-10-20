package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.VaccineDosePayload
import com.babycare.app.data.model.decodePayloadOrNull
import java.time.LocalDate

/** 一剂的接种计划（附录 A 免疫规划表，按生日推导，不落库） */
data class DosePlan(
    /** 稳定标识（如 b1 / dtap3），疫苗记录靠它对回计划 */
    val key: String,
    val vaccine: String,
    val dose: Int,
    /** 计划接种日：出生剂 = 生日当天，其余按月龄推 */
    val date: LocalDate,
    /** 出生 / 1月 / … / 6岁 */
    val monthLabel: String,
)

enum class DoseStatus { NOT_DUE, DUE, DONE, SKIPPED }

/** 计划 + 实际状态（record = 用户操作产生的那条 VACCINE_DOSE，null = 还没动过） */
data class DoseView(val plan: DosePlan, val status: DoseStatus, val record: RecordEntity?)

/**
 * 免疫规划疫苗排期（FR-9）：纯函数按生日推导，计划不生成记录；
 * 只有用户标「已种/不接种」才落 VACCINE_DOSE 记录（key 对回计划）。
 */
object Vaccines {

    /** status 常量（VaccineDosePayload.status） */
    const val STATUS_DONE = "done"
    const val STATUS_SKIPPED = "skipped"

    /** 月龄用月数表达；出生剂 days=0 表示生日当天 */
    private data class Spec(val key: String, val vaccine: String, val dose: Int, val months: Int)

    /** 附录 A · 2021 版免疫规划，22 剂，顺序即官方表序 */
    private val specs = listOf(
        Spec("b1", "乙肝疫苗", 1, 0),
        Spec("bcg", "卡介苗", 1, 0),
        Spec("b2", "乙肝疫苗", 2, 1),
        Spec("ipv1", "脊灰灭活（注射）", 1, 2),
        Spec("opv2", "脊灰减毒（口服）", 2, 3),
        Spec("dtap1", "百白破", 1, 3),
        Spec("opv3", "脊灰减毒（口服）", 3, 4),
        Spec("dtap2", "百白破", 2, 4),
        Spec("dtap3", "百白破", 3, 5),
        Spec("b3", "乙肝疫苗", 3, 6),
        Spec("mp1", "A群流脑多糖", 1, 6),
        Spec("mmr1", "麻腮风", 1, 8),
        Spec("je1", "乙脑减毒", 1, 8),
        Spec("mp2", "A群流脑多糖", 2, 9),
        Spec("dtap4", "百白破", 4, 18),
        Spec("mmr2", "麻腮风", 2, 18),
        Spec("hepa1", "甲肝减毒", 1, 18),
        Spec("je2", "乙脑减毒", 2, 24),
        Spec("mps1", "A+C群流脑多糖", 1, 36),
        Spec("opv4", "脊灰减毒（口服）", 4, 48),
        Spec("dt", "白破加强", 1, 72),
        Spec("mps2", "A+C群流脑多糖", 2, 72),
    )

    private fun monthLabel(months: Int): String = when {
        months == 0 -> "出生"
        months < 24 -> "${months}月"
        months % 12 == 0 -> "${months / 12}岁"
        else -> "${months / 12}岁${months % 12}月"
    }

    /** 全部计划（按官方表序，即时间升序） */
    fun doses(birthday: LocalDate): List<DosePlan> = specs.map {
        DosePlan(
            key = it.key, vaccine = it.vaccine, dose = it.dose,
            date = if (it.months == 0) birthday else birthday.plusMonths(it.months.toLong()),
            monthLabel = monthLabel(it.months),
        )
    }

    /**
     * 计划 × 记录 → 逐剂状态。
     * 匹配：payload.key 精确对（新数据）；key 为空的老数据按（疫苗名, 剂次）兜底。
     * 同一剂多条记录取 updatedAt 最新的，其余忽略（同步冲突的极端情况）。
     */
    fun views(birthday: LocalDate, today: LocalDate, vaccineRecords: List<RecordEntity>): List<DoseView> {
        val byKey = mutableMapOf<String, RecordEntity>()
        val byName = mutableMapOf<Pair<String, Int>, RecordEntity>()
        vaccineRecords.forEach { r ->
            val p = decodePayloadOrNull(r.type, r.payload) as? VaccineDosePayload ?: return@forEach
            if (p.status == STATUS_DONE || p.status == STATUS_SKIPPED) {
                if (p.key.isNotBlank()) byKey.mergeNewest(p.key, r)
                byName.mergeNewest(p.vaccine to p.dose, r)
            }
        }
        return doses(birthday).map { plan ->
            val record = if (byKey.containsKey(plan.key)) byKey[plan.key] else byName[plan.vaccine to plan.dose]
            val status = when {
                record != null -> {
                    val p = decodePayloadOrNull(record.type, record.payload) as? VaccineDosePayload
                    if (p?.status == STATUS_SKIPPED) DoseStatus.SKIPPED else DoseStatus.DONE
                }
                plan.date.isAfter(today) -> DoseStatus.NOT_DUE
                else -> DoseStatus.DUE
            }
            DoseView(plan, status, record)
        }
    }

    /** （已种数, 总数）；不接种不计入已种 */
    fun progress(views: List<DoseView>): Pair<Int, Int> =
        views.count { it.status == DoseStatus.DONE } to views.size

    /** 下一剂：未完成（非已种、非不接种）里计划日最早的 */
    fun nextDose(views: List<DoseView>): DoseView? =
        views.filter { it.status == DoseStatus.NOT_DUE || it.status == DoseStatus.DUE }
            .minByOrNull { it.plan.date }

    /** 临近剂次（FR-12 置顶口径）：待种（已到期）∪ 未到月龄且计划日 ≤ today+7，按计划日升序 */
    fun nearDoses(views: List<DoseView>, today: LocalDate): List<DoseView> =
        views.filter {
            it.status == DoseStatus.DUE ||
                (it.status == DoseStatus.NOT_DUE && !it.plan.date.isAfter(today.plusDays(7)))
        }.sortedBy { it.plan.date }

    /** 记录入桶：同 key 取 updatedAt 最新 */
    private fun <K> MutableMap<K, RecordEntity>.mergeNewest(key: K, r: RecordEntity) {
        val old = this[key]
        if (old == null || r.updatedAt >= old.updatedAt) this[key] = r
    }
}
