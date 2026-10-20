package com.babycare.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 时间工具：文案与边界计算集中在此，全部显式传 ZoneId（默认系统时区），跨天/深夜场景可测。
 */
object TimeKit {

    fun dayStart(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** 当天结束（次日 00:00，不含） */
    fun dayEndExclusive(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        dayStart(date.plusDays(1), zone)

    fun toLocalDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private val hmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun formatHm(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        hmFormatter.withZone(zone).format(Instant.ofEpochMilli(millis))

    fun formatMonthDay(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

    private val weekdays = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun weekdayText(date: LocalDate): String = weekdays[date.dayOfWeek.value - 1]

    /** "3小时05分" / "45分" / "0分"；不足一分钟按一分钟算 */
    fun durationText(millis: Long): String {
        val totalMin = (millis.coerceAtLeast(0) + 59_999) / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h == 0L) "${m}分" else "${h}小时${m.toString().padStart(2, '0')}分"
    }

    /** 今日概要用的一位小数小时："14.2" */
    fun hoursText(millis: Long): String = String.format(Locale.ROOT, "%.1f", millis / 3_600_000.0)

    /** 首页胶囊文案："距上次喂奶 3小时05分" / "还没记录喂奶" */
    fun sinceFeedText(lastFeedAt: Long?, now: Long): String =
        if (lastFeedAt == null || lastFeedAt > now) "还没记录喂奶"
        else "距上次喂奶 ${durationText(now - lastFeedAt)}"

    /** FR-6：19:00–07:00 结束的记夜觉，其余小睡（可手动改） */
    fun sleepTag(endHour: Int): String = if (endHour >= 19 || endHour < 7) "夜觉" else "小睡"
}
