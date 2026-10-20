package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.GrowthPayload
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.SupplementLogPayload
import com.babycare.app.data.model.SupplementPlanPayload
import com.babycare.app.data.model.encodePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SupplementKitTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 18)

    private fun millis(month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun plan(
        name: String,
        timePoints: List<String>,
        startDate: Long,
        stopped: Boolean = false,
        uuid: String = "p-$name",
    ) = RecordEntity(
        uuid = uuid, type = RecordTypes.SUPPLEMENT_PLAN,
        payload = encodePayload(SupplementPlanPayload(name = name, dose = "", timePoints = timePoints, startDate = startDate, stopped = stopped)),
        occurredAt = startDate, updatedAt = 0, deleted = false, author = "t", babyId = "b1",
    )

    private fun log(name: String, planTime: String, takenAt: Long, uuid: String = "l-$name-$planTime") = RecordEntity(
        uuid = uuid, type = RecordTypes.SUPPLEMENT_LOG,
        payload = encodePayload(SupplementLogPayload(name = name, takenAt = takenAt, planTime = planTime)),
        occurredAt = takenAt, updatedAt = 0, deleted = false, author = "t", babyId = "b1",
    )

    @Test
    fun activePlans_filtersStoppedAndBadPayload() {
        val records = listOf(
            plan("VD3", listOf("08:00"), millis(10, 1)),
            plan("DHA", listOf("12:00"), millis(10, 1), stopped = true),
            RecordEntity(   // 类型不对（生长 payload）→ 跳过
                uuid = "g1", type = RecordTypes.GROWTH,
                payload = encodePayload(GrowthPayload(weightKg = 6.5)),
                occurredAt = 0, updatedAt = 0, deleted = false, author = "t", babyId = "b1",
            ),
        )
        val active = SupplementKit.activePlans(records)
        assertEquals(1, active.size)
        assertEquals("VD3", active.first().second.name)
    }

    @Test
    fun todayTasks_sortedByTimePoint() {
        val plans = SupplementKit.activePlans(listOf(plan("VD3", listOf("10:00", "08:00"), millis(10, 1))))
        val tasks = SupplementKit.todayTasks(plans, today, emptyList(), zone)
        assertEquals(listOf("08:00", "10:00"), tasks.map { it.timePoint })
        assertTrue(tasks.none { it.done })
    }

    @Test
    fun todayTasks_startDateBoundary() {
        // 明天才开始 → 今天不出任务；今天开始 → 出
        val tomorrow = SupplementKit.activePlans(listOf(plan("VD3", listOf("08:00"), millis(10, 19))))
        assertTrue(SupplementKit.todayTasks(tomorrow, today, emptyList(), zone).isEmpty())
        val startsToday = SupplementKit.activePlans(listOf(plan("AD", listOf("08:00"), millis(10, 18))))
        assertEquals(1, SupplementKit.todayTasks(startsToday, today, emptyList(), zone).size)
    }

    @Test
    fun todayTasks_doneMatchesNameTimePointAndDay() {
        val plans = SupplementKit.activePlans(listOf(plan("VD3", listOf("08:00", "21:00"), millis(10, 1))))
        val logs = listOf(
            log("VD3", "08:00", millis(10, 18, 8, 30)),  // 命中：同名同时间点、落今天
            log("DHA", "21:00", millis(10, 18, 21)),      // 名字不同 → 不命中
            log("VD3", "21:00", millis(10, 17, 21)),      // 昨天打的 → 不命中（跨午夜口径）
        )
        val tasks = SupplementKit.todayTasks(plans, today, logs, zone)
        assertEquals(listOf(true, false), tasks.map { it.done })
    }
}
