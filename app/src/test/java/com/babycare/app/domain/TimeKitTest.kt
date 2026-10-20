package com.babycare.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeKitTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun durationText_table() {
        val cases = listOf(
            0L to "0分",
            45 * 60_000L to "45分",
            3 * 3_600_000L + 5 * 60_000L to "3小时05分",     // 演示口径
            14 * 3_600_000L + 12 * 60_000L to "14小时12分",
            -5_000L to "0分",                                 // 未来时间按 0
            60_001L to "2分",                                 // 不足一分钟按一分钟算
        )
        for ((millis, expected) in cases) assertEquals(expected, TimeKit.durationText(millis))
    }

    @Test
    fun hoursText_oneDecimal() {
        assertEquals("14.2", TimeKit.hoursText(14 * 3_600_000L + 12 * 60_000L))
        assertEquals("0.0", TimeKit.hoursText(0))
    }

    @Test
    fun dayBounds() {
        val date = LocalDate.of(2026, 10, 17)
        val start = TimeKit.dayStart(date, zone)
        val end = TimeKit.dayEndExclusive(date, zone)
        assertEquals(ZonedDateTime.of(2026, 10, 17, 0, 0, 0, 0, zone).toInstant().toEpochMilli(), start)
        assertEquals(start + 24 * 3_600_000L, end)
        assertEquals(date, TimeKit.toLocalDate(start, zone))
        assertEquals(date, TimeKit.toLocalDate(end - 1, zone))   // 23:59:59.999 仍算当天
    }

    @Test
    fun sinceFeedText_table() {
        val now = 1_000_000_000_000L
        assertEquals("还没记录喂奶", TimeKit.sinceFeedText(null, now))
        assertEquals("还没记录喂奶", TimeKit.sinceFeedText(now + 1, now))   // 时间超前（设备时钟差）
        assertEquals("距上次喂奶 3小时05分", TimeKit.sinceFeedText(now - (3 * 3_600_000L + 5 * 60_000L), now))
    }

    @Test
    fun formatAndTexts() {
        val millis = ZonedDateTime.of(2026, 10, 17, 7, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("07:30", TimeKit.formatHm(millis, zone))
        assertEquals("10月17日", TimeKit.formatMonthDay(LocalDate.of(2026, 10, 17)))
        assertEquals("周六", TimeKit.weekdayText(LocalDate.of(2026, 10, 17)))   // 演示口径：10-17 周六
        assertEquals("周一", TimeKit.weekdayText(LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun sleepTag_table() {
        val cases = listOf(
            19 to "夜觉", 23 to "夜觉", 0 to "夜觉", 6 to "夜觉",
            7 to "小睡", 12 to "小睡", 18 to "小睡",
        )
        for ((hour, expected) in cases) assertEquals("hour=$hour", expected, TimeKit.sleepTag(hour))
    }
}
