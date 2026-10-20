package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.AgendaPayload
import com.babycare.app.data.model.decodePayloadOrNull
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 行程条目：实体 + 解码后的 payload */
data class AgendaEntry(val entity: RecordEntity, val payload: AgendaPayload)

/** 行程分区：今天 / 即将到来（明天 ~ 今天+7）/ 未来按月（往期不进 Tab，历史在日历看） */
data class AgendaBoard(
    val today: List<AgendaEntry> = emptyList(),
    val upcoming: List<AgendaEntry> = emptyList(),
    val futureMonths: List<AgendaMonth> = emptyList(),
)

data class AgendaMonth(val month: YearMonth, val items: List<AgendaEntry>)

/** 行程纯函数：解码、排序、分区（行程量小，全量内存分组） */
object AgendaKit {

    /** 解码为条目（坏 payload / 非行程记录跳过），按行程时间升序 */
    fun agendaEntries(records: List<RecordEntity>): List<AgendaEntry> =
        records.mapNotNull { r ->
            (decodePayloadOrNull(r.type, r.payload) as? AgendaPayload)?.let { AgendaEntry(r, it) }
        }.sortedBy { it.entity.occurredAt }

    /** 某一天的行程（今天页卡片、日历选中日用） */
    fun dayAgendas(records: List<RecordEntity>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<AgendaEntry> =
        agendaEntries(records).filter { TimeKit.toLocalDate(it.entity.occurredAt, zone) == date }

    fun groupAgendas(records: List<RecordEntity>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): AgendaBoard {
        val dated = agendaEntries(records).map { TimeKit.toLocalDate(it.entity.occurredAt, zone) to it }
        val upcomingEnd = today.plusDays(7)
        return AgendaBoard(
            today = dated.filter { it.first == today }.map { it.second },
            upcoming = dated.filter { it.first > today && it.first <= upcomingEnd }.map { it.second },
            futureMonths = dated.filter { it.first > upcomingEnd }
                .groupBy { YearMonth.from(it.first) }
                .toSortedMap()
                .map { (m, list) -> AgendaMonth(m, list.map { it.second }) },
        )
    }
}
