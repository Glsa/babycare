package com.babycare.app.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/**
 * 喂奶间隔设置（0011）：从开始喂奶起算「距下次喂奶」倒计时，白天/夜晚独立。
 * 设置存 prefs（非业务记录，不进 Room、不随备份迁移），装完即用默认 3 小时。
 */
@Serializable
data class FeedIntervalSettings(
    /** 白天间隔（分钟） */
    val dayMin: Int = 180,
    /** 夜晚间隔（分钟） */
    val nightMin: Int = 180,
    /** 白天时段起点（整点 0-23） */
    val dayStartHour: Int = 7,
    /** 夜晚时段起点（整点 0-23） */
    val nightStartHour: Int = 19,
)

object FeedIntervalKit {

    /** 这次喂奶发生在白天还是夜晚：[dayStart, nightStart) 为白天，含头不含尾；支持反向配置（如 22 起夜 6 起日） */
    fun isDayHour(hour: Int, s: FeedIntervalSettings): Boolean = when {
        s.dayStartHour < s.nightStartHour -> hour in s.dayStartHour until s.nightStartHour
        else -> hour >= s.dayStartHour || hour < s.nightStartHour
    }

    /** 距下次喂奶剩余毫秒；负数 = 已超时。间隔按「这次喂奶开始时刻」的昼夜取，倒计时跨分界不换档 */
    fun remainingMillis(lastFeedAt: Long, now: Long, s: FeedIntervalSettings, zone: ZoneId): Long {
        val hour = Instant.ofEpochMilli(lastFeedAt).atZone(zone).hour
        val intervalMin = if (isDayHour(hour, s)) s.dayMin else s.nightMin
        return lastFeedAt + intervalMin * 60_000L - now
    }

    /** badge 文案：null = 无喂奶记录不显示；Pair(text, overdue)，超时由 UI 换红色 */
    fun badgeText(lastFeedAt: Long?, now: Long, s: FeedIntervalSettings, zone: ZoneId): Pair<String, Boolean>? {
        if (lastFeedAt == null) return null
        val remain = remainingMillis(lastFeedAt, now, s, zone)
        val overdue = remain <= 0
        val text = if (overdue) "🍼 该喂奶了 · 超 ${hmText(-remain)}" else "⏱ 距下次喂奶 ${hmText(remain)}"
        return text to overdue
    }

    /** 分钟 → 「3h / 3.5h」（我的页副标题用） */
    fun hoursText(min: Int): String {
        val h = min / 60.0
        return if (h == h.toLong().toDouble()) "${h.toLong()}h" else "${h}h"
    }

    /** 毫秒 → "H:MM"，分钟粒度向上取整（剩 59 秒显示 0:01，不出现 0:00）；Widget 文案也用 */
    fun hmText(millis: Long): String {
        val mins = (millis + 59_999) / 60_000
        return "${mins / 60}:${(mins % 60).toString().padStart(2, '0')}"
    }
}
