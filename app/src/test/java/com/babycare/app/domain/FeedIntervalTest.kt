package com.babycare.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** 喂奶间隔倒计时（0011）：昼夜判定按「喂奶开始时刻」，间隔取自所在时段 */
class FeedIntervalTest {

    private val zone = ZoneOffset.UTC
    private val default = FeedIntervalSettings()

    private fun at(hour: Int, minute: Int = 0, day: Int = 18): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).toInstant(zone).toEpochMilli()

    private fun min(m: Long) = m * 60_000L

    // ---- 昼夜判定 ----

    @Test
    fun `默认分界 7 与 19 的边界`() {
        assertFalse(FeedIntervalKit.isDayHour(6, default))   // 6:59 前是夜
        assertTrue(FeedIntervalKit.isDayHour(7, default))    // 7:00 起是白天（含头）
        assertTrue(FeedIntervalKit.isDayHour(18, default))   // 18:59 是白天
        assertFalse(FeedIntervalKit.isDayHour(19, default))  // 19:00 起是夜（不含尾）
        assertFalse(FeedIntervalKit.isDayHour(23, default))
        assertFalse(FeedIntervalKit.isDayHour(0, default))
    }

    @Test
    fun `反向分界配置 夜22起 日6起`() {
        val s = FeedIntervalSettings(dayStartHour = 6, nightStartHour = 22)
        assertFalse(FeedIntervalKit.isDayHour(23, s)) // 深夜是夜
        assertFalse(FeedIntervalKit.isDayHour(5, s))  // 凌晨是夜
        assertTrue(FeedIntervalKit.isDayHour(6, s))
        assertTrue(FeedIntervalKit.isDayHour(21, s))
        assertFalse(FeedIntervalKit.isDayHour(22, s))
    }

    // ---- 剩余时间 ----

    @Test
    fun `白天喂奶取白天间隔`() {
        // 8:00 喂（白天），默认 180 分；8:30 时剩 150 分
        assertEquals(min(150), FeedIntervalKit.remainingMillis(at(8), at(8, 30), default, zone))
    }

    @Test
    fun `夜晚喂奶取夜晚间隔`() {
        val s = default.copy(nightMin = 240)
        // 20:00 喂（夜），240 分；20:30 时剩 210 分
        assertEquals(min(210), FeedIntervalKit.remainingMillis(at(20), at(20, 30), s, zone))
    }

    @Test
    fun `白天按喂奶时刻定间隔 倒计时跨分界不换档`() {
        val s = default.copy(dayMin = 180, nightMin = 300)
        // 18:00 喂是白天（180 分），到点 21:00 已入夜，但本轮仍按白天间隔
        assertEquals(min(30), FeedIntervalKit.remainingMillis(at(18), at(20, 30), s, zone))
    }

    @Test
    fun `深夜喂奶跨午夜`() {
        // 23:00 喂（夜，默认 180），到点次日 2:00；次日 1:00 时剩 60 分
        assertEquals(min(60), FeedIntervalKit.remainingMillis(at(23), at(1, 0, day = 19), default, zone))
    }

    @Test
    fun `超时为负`() {
        // 8:00 喂，11:10 已超 10 分
        assertEquals(-min(10), FeedIntervalKit.remainingMillis(at(8), at(11, 10), default, zone))
    }

    // ---- badge 文案 ----

    @Test
    fun `无喂奶记录不显示`() {
        assertNull(FeedIntervalKit.badgeText(null, at(10), default, zone))
    }

    @Test
    fun `进行中文案 H 冒号 MM`() {
        // 8:00 喂，9:37 时剩 83 分 = 1:23
        val (text, overdue) = FeedIntervalKit.badgeText(at(8), at(9, 37), default, zone)!!
        assertEquals("⏱ 距下次喂奶 1:23", text)
        assertFalse(overdue)
    }

    @Test
    fun `不足一小时也用 H 冒号 MM`() {
        // 8:00 喂（180 分 → 11:00 到点），10:35 时剩 25 分 = 0:25
        val (text, _) = FeedIntervalKit.badgeText(at(8), at(10, 35), default, zone)!!
        assertEquals("⏱ 距下次喂奶 0:25", text)
    }

    @Test
    fun `超时文案与红标`() {
        // 8:00 喂，11:12 超时 12 分
        val (text, overdue) = FeedIntervalKit.badgeText(at(8), at(11, 12), default, zone)!!
        assertEquals("🍼 该喂奶了 · 超 0:12", text)
        assertTrue(overdue)
    }

    @Test
    fun `秒级向上取整不出现 0 冒号 00`() {
        // 剩 59 秒 → 0:01；超时 1 秒 → 超 0:01
        val t = at(8)
        val (ing, _) = FeedIntervalKit.badgeText(t, t + min(180) - 59_999, default, zone)!!
        assertEquals("⏱ 距下次喂奶 0:01", ing)
        val (over, _) = FeedIntervalKit.badgeText(t, t + min(180) + 1_000, default, zone)!!
        assertEquals("🍼 该喂奶了 · 超 0:01", over)
    }

    // ---- 副标题 ----

    @Test
    fun `小时文本 整数与半点`() {
        assertEquals("3h", FeedIntervalKit.hoursText(180))
        assertEquals("3.5h", FeedIntervalKit.hoursText(210))
    }
}
