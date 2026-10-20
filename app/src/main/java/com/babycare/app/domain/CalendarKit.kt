package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.RecordTypes
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 日历纯函数：月视图矩阵与色点聚合（UI 无关、可表驱动测试） */
object CalendarKit {

    /** 色点显示顺序（同一天多类型时的排列次序）；最多显示 5 个，超出省略 */
    private val dotOrder = listOf(
        RecordTypes.FEED_FORMULA, RecordTypes.FEED_NURSING, RecordTypes.DIAPER, RecordTypes.SLEEP,
        RecordTypes.BATH, RecordTypes.EDU_CHECKIN, RecordTypes.GROWTH, RecordTypes.VACCINE_DOSE,
        RecordTypes.SUPPLEMENT_LOG, RecordTypes.AGENDA,
    )

    /** 当日要显示的色点类型（按固定顺序取前 5） */
    fun dotTypes(types: Set<String>): List<String> =
        dotOrder.filter { it in types }.take(5)

    /** 月视图矩阵：周一开头、固定 6 行 × 7 列，跨月补位为 null */
    fun monthMatrix(month: YearMonth): List<List<LocalDate?>> {
        val lead = month.atDay(1).dayOfWeek.value - 1   // 周一=1 → 前置空格数
        val cells: List<LocalDate?> = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        val trail = (6 * 7 - cells.size).coerceAtLeast(0)
        return (cells + List(trail) { null }).chunked(7)
    }

    /** 月区间内的记录按日聚合出类型集合（色点数据源） */
    fun groupDayTypes(records: List<RecordEntity>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Set<String>> =
        records.groupBy({ TimeKit.toLocalDate(it.occurredAt, zone) }, { it.type })
            .mapValues { (_, types) -> types.toSet() }
}
