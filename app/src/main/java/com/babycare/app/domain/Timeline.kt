package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.AgendaPayload
import com.babycare.app.data.model.BathPayload
import com.babycare.app.data.model.DiaperPayload
import com.babycare.app.data.model.EduCheckinPayload
import com.babycare.app.data.model.FeedFormulaPayload
import com.babycare.app.data.model.FeedNursingPayload
import com.babycare.app.data.model.GrowthPayload
import com.babycare.app.data.model.SleepPayload
import com.babycare.app.data.model.SupplementLogPayload
import com.babycare.app.data.model.SupplementPlanPayload
import com.babycare.app.data.model.VaccineDosePayload
import com.babycare.app.data.model.decodePayloadOrNull

/**
 * 时间轴条目展示模型：标题/副标题纯数据；颜色与时间格式化在 UI 层做。
 * 不认识的类型直接跳过（新版本设备产生的新类型在旧设备不展示也不崩）。
 */
data class TimelineItemModel(
    val entity: RecordEntity,
    val emoji: String,
    val title: String,
    val subtitle: String,
)

fun buildTimelineItems(records: List<RecordEntity>): List<TimelineItemModel> =
    records.mapNotNull { r ->
        when (val p = decodePayloadOrNull(r.type, r.payload)) {
            is FeedFormulaPayload -> TimelineItemModel(
                r,
                if (p.kind == FeedFormulaPayload.KIND_BREAST) "🥛" else "🍼",
                "${if (p.kind == FeedFormulaPayload.KIND_BREAST) "母乳" else "奶粉"} ${p.ml} ml",
                p.note,
            )
            is FeedNursingPayload -> {
                val sides = buildString {
                    if (p.leftMinutes > 0) append("左${p.leftMinutes}分")
                    if (p.rightMinutes > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("右${p.rightMinutes}分")
                    }
                }
                TimelineItemModel(r, "🤱", if (sides.isEmpty()) "亲喂" else "亲喂 $sides", p.note)
            }
            is DiaperPayload -> TimelineItemModel(
                r, "💧",
                when {
                    p.pee && p.poop -> "换尿布 · 都有"
                    p.pee -> "换尿布 · 小便"
                    else -> "换尿布 · 大便"
                },
                p.note,
            )
            is BathPayload -> TimelineItemModel(r, "🛁", "洗澡", p.note)
            is SleepPayload -> {
                val tag = if (p.tag.isBlank()) "" else " · ${p.tag}"
                val ongoing = if (p.endAt == null) "（进行中）" else ""
                TimelineItemModel(r, "😴", "睡眠$tag$ongoing", p.note)
            }
            is GrowthPayload -> TimelineItemModel(
                r, "📏",
                listOfNotNull(
                    p.weightKg?.let { "体重${it}kg" },
                    p.heightCm?.let { "身高${it}cm" },
                    p.headCm?.let { "头围${it}cm" },
                ).joinToString(" · ").ifBlank { "生长记录" },
                p.source,
            )
            is EduCheckinPayload -> TimelineItemModel(r, "🎨", "早教 · ${p.item}", p.category)
            is VaccineDosePayload -> TimelineItemModel(
                r, "💉",
                "${p.vaccine} 第${p.dose}剂" + if (p.status == Vaccines.STATUS_SKIPPED) " · 不接种" else "",
                p.note,
            )
            is SupplementLogPayload -> TimelineItemModel(r, "💊", "补剂 · ${p.name}", p.planTime)
            is SupplementPlanPayload -> TimelineItemModel(r, "💊", "补剂方案 · ${p.name}", "")
            is AgendaPayload -> TimelineItemModel(r, "🗓️", "行程 · ${p.title}${if (p.done) " · 已完成" else ""}", p.note)
            null -> null      // 坏 JSON / 未知类型
            else -> null      // RecordPayload 非密封，补全 when 分支；未识别的同样跳过
        }
    }
