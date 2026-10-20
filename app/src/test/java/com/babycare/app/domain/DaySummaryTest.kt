package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.BathPayload
import com.babycare.app.data.model.DiaperPayload
import com.babycare.app.data.model.EduCheckinPayload
import com.babycare.app.data.model.FeedFormulaPayload
import com.babycare.app.data.model.GrowthPayload
import com.babycare.app.data.model.RecordPayload
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.SleepPayload
import com.babycare.app.data.model.encodePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaySummaryTest {

    private val hour = 3_600_000L
    private val now = 1_000_000_000_000L
    private var seq = 0

    private fun rec(type: String, payload: RecordPayload): RecordEntity = RecordEntity(
        uuid = "u${seq++}", type = type, payload = encodePayload(payload),
        occurredAt = 0L, updatedAt = 0L, deleted = false, author = "test", babyId = "b1",
    )

    /** 演示数据口径一天：8 次奶粉 780ml、睡眠 14.2h（含进行中）、尿 6 便 2、洗澡 1、早教 1 */
    @Test
    fun demoDay() {
        val records = buildList {
            listOf(60, 90, 120, 150, 100, 110, 90, 60).forEach {
                add(rec(RecordTypes.FEED_FORMULA, FeedFormulaPayload(it)))
            }
            // 已完成 12h + 进行中 2.2h = 14.2h
            add(rec(RecordTypes.SLEEP, SleepPayload(startAt = now - 142 * hour / 10, endAt = now - 22 * hour / 10, tag = "夜觉")))
            add(rec(RecordTypes.SLEEP, SleepPayload(startAt = now - 22 * hour / 10, endAt = null, tag = "小睡")))
            repeat(4) { add(rec(RecordTypes.DIAPER, DiaperPayload(pee = true, poop = false))) }
            repeat(2) { add(rec(RecordTypes.DIAPER, DiaperPayload(pee = true, poop = true))) }
            add(rec(RecordTypes.BATH, BathPayload()))
            add(rec(RecordTypes.EDU_CHECKIN, EduCheckinPayload("大动作", "俯卧抬头", 10)))
            // 不进当日汇总的类型
            add(rec(RecordTypes.GROWTH, GrowthPayload(weightKg = 6.4)))
        }

        val s = computeDaySummary(records, now = now)
        assertEquals(780, s.formulaMl)
        assertEquals(8, s.formulaCount)
        assertEquals(0, s.nursingCount)
        assertEquals(142L * hour / 10, s.sleepMillis) // 14.2h
        assertTrue(s.sleepOngoing)
        assertEquals(6, s.peeCount)
        assertEquals(2, s.poopCount)
        assertTrue(s.bathDone)
        assertEquals(1, s.eduCount)
    }

    @Test
    fun emptyDay() {
        assertEquals(DaySummary(), computeDaySummary(emptyList(), now = now))
    }

    /** 未知类型/坏数据跳过，不崩也不影响其余汇总 */
    @Test
    fun unknownTypeAndBadJsonSkipped() {
        val bad1 = RecordEntity("u1", "future_type", "{\"x\":1}", 0, 0, false, "a", "b")
        val bad2 = RecordEntity("u2", RecordTypes.FEED_FORMULA, "not-json", 0, 0, false, "a", "b")
        val good = rec(RecordTypes.FEED_FORMULA, FeedFormulaPayload(90))
        val s = computeDaySummary(listOf(bad1, bad2, good), now = now)
        assertEquals(90, s.formulaMl)
        assertEquals(1, s.formulaCount)
        assertFalse(s.sleepOngoing)
    }
}
