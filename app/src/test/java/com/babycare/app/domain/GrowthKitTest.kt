package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.encodePayload
import com.babycare.app.data.model.GrowthPayload
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GrowthKitTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun growth(
        month: Int,
        day: Int,
        weightKg: Double? = null,
        heightCm: Double? = null,
        headCm: Double? = null,
        source: String = "在家",
        uuid: String = "g$month-$day",
    ) = RecordEntity(
        uuid = uuid, type = RecordTypes.GROWTH,
        payload = encodePayload(GrowthPayload(weightKg, heightCm, headCm, source)),
        occurredAt = ZonedDateTime.of(2026, month, day, 10, 0, 0, 0, zone).toInstant().toEpochMilli(),
        updatedAt = 0, deleted = false, author = "t", babyId = "b1",
    )

    @Test
    fun latest_picksNewestNonNullPerMetric() {
        val latest = GrowthKit.latest(
            listOf(
                growth(10, 1, weightKg = 5.0),
                growth(10, 10, heightCm = 62.5),
                growth(10, 12, weightKg = 6.4, headCm = 40.8, source = "体检"),
                growth(10, 15, heightCm = 63.0),   // 更新一条身高；无体重
            )
        )
        assertEquals(6.4, latest.weight?.second?.weightKg!!, 1e-9)   // 10-12 的（10-15 没体重）
        assertEquals(63.0, latest.height?.second?.heightCm!!, 1e-9)  // 10-15 的
        assertEquals(40.8, latest.head?.second?.headCm!!, 1e-9)      // 10-12 的
        assertEquals("体检", latest.weight.second.source)
    }

    @Test
    fun latest_allMissingReturnsNull() {
        val latest = GrowthKit.latest(listOf(growth(10, 1)))
        assertEquals(null, latest.weight)
        assertEquals(null, latest.height)
        assertEquals(null, latest.head)
    }

    @Test
    fun history_descendingAndSkipsBadPayload() {
        val history = GrowthKit.history(
            listOf(
                growth(10, 1, weightKg = 5.0),
                growth(10, 12, weightKg = 6.4),
                RecordEntity(   // 坏 payload → 跳过
                    uuid = "bad", type = RecordTypes.GROWTH, payload = "{broken",
                    occurredAt = 0, updatedAt = 0, deleted = false, author = "t", babyId = "b1",
                ),
            )
        )
        assertEquals(listOf("g10-12", "g10-1"), history.map { it.first.uuid })
    }
}
