package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.RecordTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class CalendarKitTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun monthMatrix_mondayFirst() {
        // 2026-10-01 是周四 → 首行前置 3 个空格，第一格是周一 9-28…（null 补位），首格日期 = 10-01 在第 4 列
        val rows = CalendarKit.monthMatrix(YearMonth.of(2026, 10))
        assertEquals(6, rows.size)
        assertTrue(rows.all { it.size == 7 })
        assertEquals(listOf(null, null, null), rows[0].take(3))            // 周一~周三补位
        assertEquals(LocalDate.of(2026, 10, 1), rows[0][3])                // 周四
        assertEquals(LocalDate.of(2026, 10, 17), rows[2][5])               // 演示口径：17 号周六
        assertEquals(LocalDate.of(2026, 10, 31), rows[4][5])               // 31 号周六（周一开头，周六=第 6 列）
        assertTrue(rows[5].all { it == null })                             // 第 6 行整行补位
    }

    @Test
    fun monthMatrix_sundayStartMonth_fitsSixRows() {
        // 2026-11-01 是周日 → 前置 6 个空格，31 天 = 37 格，6 行装得下
        val rows = CalendarKit.monthMatrix(YearMonth.of(2026, 11))
        assertEquals(6, rows.size)
        assertEquals(LocalDate.of(2026, 11, 1), rows[0].last())
        assertEquals(LocalDate.of(2026, 11, 30), rows[5].first())
    }

    @Test
    fun groupDayTypes_bucketsByLocalDate() {
        fun at(day: Int, hour: Int, type: String) = RecordEntity(
            uuid = "u$day-$hour-$type", type = type, payload = "{}",
            occurredAt = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli(),
            updatedAt = 0, deleted = false, author = "t", babyId = "b",
        )
        val grouped = CalendarKit.groupDayTypes(
            listOf(
                at(15, 7, RecordTypes.FEED_FORMULA),
                at(15, 12, RecordTypes.DIAPER),
                at(15, 23, RecordTypes.SLEEP),
                at(16, 8, RecordTypes.FEED_FORMULA),
            ),
            zone,
        )
        assertEquals(
            setOf(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16)),
            grouped.keys,
        )
        assertEquals(
            setOf(RecordTypes.FEED_FORMULA, RecordTypes.DIAPER, RecordTypes.SLEEP),
            grouped.getValue(LocalDate.of(2026, 10, 15)),
        )
    }

    @Test
    fun dotTypes_orderedAndCapped() {
        val dots = CalendarKit.dotTypes(
            setOf(RecordTypes.SLEEP, RecordTypes.AGENDA, RecordTypes.DIAPER, RecordTypes.FEED_FORMULA, RecordTypes.BATH, RecordTypes.EDU_CHECKIN),
        )
        // 固定顺序取前 5：AGENDA 排最后被裁掉
        assertEquals(
            listOf(RecordTypes.FEED_FORMULA, RecordTypes.DIAPER, RecordTypes.SLEEP, RecordTypes.BATH, RecordTypes.EDU_CHECKIN),
            dots,
        )
    }
}
