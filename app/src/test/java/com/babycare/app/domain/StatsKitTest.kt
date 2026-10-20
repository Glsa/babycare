package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.DiaperPayload
import com.babycare.app.data.model.EduCheckinPayload
import com.babycare.app.data.model.FeedFormulaPayload
import com.babycare.app.data.model.FeedNursingPayload
import com.babycare.app.data.model.GrowthPayload
import com.babycare.app.data.model.RecordPayload
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.SleepPayload
import com.babycare.app.data.model.encodePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** 统计聚合（0015 · FR-16） */
class StatsKitTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 18)

    private fun at(d: LocalDate, hour: Int, minute: Int = 0): Long =
        d.atTime(hour, minute).toInstant(zone).toEpochMilli()

    private fun entity(type: String, payload: RecordPayload, occurredAt: Long) = RecordEntity(
        uuid = UUID.randomUUID().toString(),
        type = type, payload = encodePayload(payload),
        occurredAt = occurredAt, updatedAt = 0L, deleted = false, author = "a", babyId = "b",
    )

    @Test
    fun `七天档补零与聚合`() {
        val records = listOf(
            entity(RecordTypes.FEED_FORMULA, FeedFormulaPayload(120, "formula", ""), at(today, 8)),
            entity(RecordTypes.FEED_FORMULA, FeedFormulaPayload(90, "bm", ""), at(today, 12)),
            entity(RecordTypes.FEED_NURSING, FeedNursingPayload(10, 15, ""), at(today, 15)),
            entity(RecordTypes.DIAPER, DiaperPayload(true, false, ""), at(today, 9)),
            entity(RecordTypes.EDU_CHECKIN, EduCheckinPayload("运动", "抬头", null), at(today, 16)),
        )
        val stats = StatsKit.dailyStats(records, 7, zone, today, at(today, 18))
        assertEquals(7, stats.size)
        assertEquals(today.minusDays(6), stats.first().date)
        assertEquals(today, stats.last().date)
        // 前六天全零，今天聚合正确
        assertTrue(stats.dropLast(1).all { it.formulaMl == 0 && it.sleepMillis == 0L })
        val d = stats.last()
        assertEquals(210, d.formulaMl)
        assertEquals(2, d.formulaCount)
        assertEquals(25, d.nursingMinutes)
        assertEquals(1, d.peeCount)
        assertEquals(1, d.eduCount)
    }

    @Test
    fun `睡眠夜觉拆分与窗口外丢弃`() {
        val night = entity(RecordTypes.SLEEP, SleepPayload(at(today, 20), at(today, 23), "夜觉", ""), at(today, 20))
        val nap = entity(RecordTypes.SLEEP, SleepPayload(at(today, 13), at(today, 14), "小睡", ""), at(today, 13))
        val outside = entity(RecordTypes.FEED_FORMULA, FeedFormulaPayload(60, "formula", ""), at(today.minusDays(7), 8))
        val stats = StatsKit.dailyStats(listOf(night, nap, outside), 7, zone, today, at(today, 18))
        val d = stats.last()
        assertEquals(4 * 3_600_000L, d.sleepMillis)
        assertEquals(3 * 3_600_000L, d.nightMillis)
        assertEquals(0, d.formulaMl) // 7 天前的不进窗口
    }

    @Test
    fun `进行中睡眠按now折算`() {
        val ongoing = entity(RecordTypes.SLEEP, SleepPayload(at(today, 9), null, "小睡", ""), at(today, 9))
        val d = StatsKit.dailyStats(listOf(ongoing), 7, zone, today, at(today, 10, 30)).last()
        assertEquals(90 * 60_000L, d.sleepMillis)
    }

    @Test
    fun `全部档从最早记录日起`() {
        val old = entity(RecordTypes.DIAPER, DiaperPayload(true, true, ""), at(today.minusDays(10), 8))
        val stats = StatsKit.dailyStats(listOf(old), null, zone, today, at(today, 12))
        assertEquals(11, stats.size)
        assertEquals(1, stats.first().poopCount)
        // 无任何记录 → 只有 today 一行
        assertEquals(1, StatsKit.dailyStats(emptyList(), null, zone, today, at(today, 12)).size)
    }

    @Test
    fun `生长点月龄换算与指标过滤`() {
        val birthday = LocalDate.of(2026, 8, 10)
        val w = entity(RecordTypes.GROWTH, GrowthPayload(6.0, null, null, "在家"), at(LocalDate.of(2026, 10, 10), 10))
        val h = entity(RecordTypes.GROWTH, GrowthPayload(null, 60.0, null, "体检"), at(LocalDate.of(2026, 10, 12), 10))
        val wp = StatsKit.growthPoints(listOf(w, h), birthday, GrowthMetric.WEIGHT, zone)
        assertEquals(1, wp.size)
        // 61 天 / 30.4375 ≈ 2.0033 月龄
        assertEquals(2.0033, wp[0].first, 0.001)
        assertEquals(6.0, wp[0].second, 1e-9)
        assertEquals(1, StatsKit.growthPoints(listOf(w, h), birthday, GrowthMetric.HEIGHT, zone).size)
        assertTrue(StatsKit.growthPoints(listOf(w, h), birthday, GrowthMetric.HEAD, zone).isEmpty())
    }

    @Test
    fun `csv表头与行`() {
        val records = listOf(entity(RecordTypes.FEED_FORMULA, FeedFormulaPayload(120, "formula", ""), at(today, 8)))
        val csv = StatsKit.toCsv(StatsKit.dailyStats(records, 2, zone, today, at(today, 18)))
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertEquals("日期,瓶喂ml,瓶喂次数,亲喂次数,亲喂分钟,尿,便,睡眠小时,夜觉小时,早教次数", lines[0])
        assertEquals("120,1,0,0,0,0,0.0,0.0,0", lines[2].split(",", limit = 2)[1])
    }
}
