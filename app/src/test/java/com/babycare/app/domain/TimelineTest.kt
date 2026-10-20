package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.BathPayload
import com.babycare.app.data.model.DiaperPayload
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

class TimelineTest {

    private var seq = 0

    private fun rec(type: String, payload: RecordPayload) = RecordEntity(
        uuid = "u${seq++}", type = type, payload = encodePayload(payload),
        occurredAt = 0L, updatedAt = 0L, deleted = false, author = "t", babyId = "b",
    )

    @Test
    fun titles_byType() {
        val titles = buildTimelineItems(
            listOf(
                rec(RecordTypes.FEED_FORMULA, FeedFormulaPayload(150)),
                rec(RecordTypes.FEED_FORMULA, FeedFormulaPayload(120, FeedFormulaPayload.KIND_BREAST)),
                rec(RecordTypes.FEED_NURSING, FeedNursingPayload(8, 10)),
                rec(RecordTypes.FEED_NURSING, FeedNursingPayload(leftMinutes = 8)),
                rec(RecordTypes.DIAPER, DiaperPayload(pee = true, poop = true)),
                rec(RecordTypes.DIAPER, DiaperPayload(pee = false, poop = true)),
                rec(RecordTypes.SLEEP, SleepPayload(startAt = 0, endAt = null, tag = "夜觉")),
                rec(RecordTypes.SLEEP, SleepPayload(startAt = 0, endAt = 1)),
                rec(RecordTypes.BATH, BathPayload()),
                rec(RecordTypes.GROWTH, GrowthPayload(weightKg = 6.4, heightCm = 62.5)),
            )
        ).map { it.title }

        assertEquals(
            listOf(
                "奶粉 150 ml",
                "母乳 120 ml",
                "亲喂 左8分 · 右10分",
                "亲喂 左8分",
                "换尿布 · 都有",
                "换尿布 · 大便",
                "睡眠 · 夜觉（进行中）",
                "睡眠",
                "洗澡",
                "体重6.4kg · 身高62.5cm",
            ),
            titles,
        )
    }

    @Test
    fun unknownType_skipped() {
        val r = RecordEntity("u", "future_type", "{}", 0, 0, false, "a", "b")
        assertTrue(buildTimelineItems(listOf(r)).isEmpty())
    }

    @Test
    fun `旧瓶喂记录无 kind 字段 解码为奶粉（0012 前向兼容）`() {
        // 0012 之前的存量 payload 只有 ml/note
        val legacy = RecordEntity(
            "u", RecordTypes.FEED_FORMULA, """{"ml":90,"note":""}""", 0, 0, false, "a", "b",
        )
        val item = buildTimelineItems(listOf(legacy)).single()
        assertEquals("奶粉 90 ml", item.title)
        assertEquals("🍼", item.emoji)
    }
}
