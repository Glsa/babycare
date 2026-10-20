package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.RecordTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class AgendaKitTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 18)

    private fun agenda(day: Int, month: Int = 10, hour: Int = 9, title: String = "行程$day-$hour", uuid: String = "u$day-$hour", payload: String = """{"title":"$title"}""") =
        RecordEntity(
            uuid = uuid, type = RecordTypes.AGENDA, payload = payload,
            occurredAt = ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli(),
            updatedAt = 0, deleted = false, author = "t", babyId = "b",
        )

    @Test
    fun groupAgendas_bucketsBySection() {
        val board = AgendaKit.groupAgendas(
            listOf(
                agenda(17, hour = 8),                      // 昨天 → 丢弃
                agenda(18, hour = 9),                      // 今天
                agenda(18, hour = 21, title = "夜里的"),   // 今天
                agenda(19, hour = 10),                     // 明天 → 即将到来
                agenda(25, hour = 10),                     // 第 7 天 → 即将到来（边界含）
                agenda(26, hour = 10),                     // 第 8 天 → 未来计划（边界不含）
                agenda(18, hour = 12, payload = "{broken"), // 坏 payload → 跳过
            ),
            today, zone,
        )
        assertEquals(listOf("行程18-9", "夜里的"), board.today.map { it.payload.title })
        assertEquals(listOf("行程19-10", "行程25-10"), board.upcoming.map { it.payload.title })
        assertEquals(1, board.futureMonths.size)
        assertEquals(listOf("行程26-10"), board.futureMonths.single().items.map { it.payload.title })
    }

    @Test
    fun groupAgendas_futureGroupedByMonthAsc() {
        val board = AgendaKit.groupAgendas(
            listOf(
                agenda(26, hour = 9),        // 10-26，未来 · 10 月
                agenda(26, hour = 15),       // 10-26，未来 · 10 月（同月两条）
                agenda(1, month = 11, hour = 10),  // 11-01，未来 · 11 月
            ),
            today, zone,
        )
        assertEquals(listOf("2026-10", "2026-11"), board.futureMonths.map { it.month.toString() })
        assertEquals(2, board.futureMonths[0].items.size)
        assertEquals(1, board.futureMonths[1].items.size)
    }

    @Test
    fun agendaEntries_sortsByTimeAsc_andSkipsBadPayload() {
        val entries = AgendaKit.agendaEntries(
            listOf(
                agenda(18, hour = 21, uuid = "late"),
                agenda(18, hour = 7, uuid = "early"),
                agenda(18, hour = 12, uuid = "bad", payload = "{broken"),
            ),
        )
        assertEquals(listOf("early", "late"), entries.map { it.entity.uuid })
    }

    @Test
    fun dayAgendas_crossMidnightBoundary() {
        val day = AgendaKit.dayAgendas(
            listOf(
                agenda(18, hour = 23, uuid = "in1"),   // 10-18 23:00 → 属于 18 号
                agenda(19, hour = 0, uuid = "next"),   // 10-19 00:00 → 属于 19 号
            ),
            LocalDate.of(2026, 10, 18), zone,
        )
        assertEquals(listOf("in1"), day.map { it.entity.uuid })
        assertTrue(day.all { it.payload.title.isNotBlank() })
    }
}
