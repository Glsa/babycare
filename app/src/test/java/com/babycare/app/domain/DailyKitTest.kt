package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.DailyTaskLogPayload
import com.babycare.app.data.model.DailyTaskPayload
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.encodePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 每日必做（0013）：dateKey 命中、排序、seed 兜底 */
class DailyKitTest {

    private fun task(uuid: String, name: String, sortOrder: Int) = RecordEntity(
        uuid = uuid,
        type = RecordTypes.DAILY_TASK,
        payload = encodePayload(DailyTaskPayload(name = name, sortOrder = sortOrder)),
        occurredAt = 0L,
        updatedAt = 0L,
        deleted = false,
        author = "a",
        babyId = "b",
    )

    private fun log(uuid: String, taskUuid: String, dateKey: String) = RecordEntity(
        uuid = uuid,
        type = RecordTypes.DAILY_TASK_LOG,
        payload = encodePayload(DailyTaskLogPayload(taskUuid = taskUuid, dateKey = dateKey)),
        occurredAt = 0L,
        updatedAt = 0L,
        deleted = false,
        author = "a",
        babyId = "b",
    )

    @Test
    fun `dayLog 命中当天该任务的打卡`() {
        val logs = listOf(
            log("l1", "t1", "2026-10-18"),
            log("l2", "t2", "2026-10-17"),
        )
        assertEquals("l1", DailyKit.dayLog(logs, "t1", "2026-10-18")?.uuid)
    }

    @Test
    fun `dayLog 未命中_别的任务或别的日期`() {
        val logs = listOf(
            log("l1", "t1", "2026-10-18"),
            log("l2", "t2", "2026-10-17"),
        )
        assertNull(DailyKit.dayLog(logs, "t1", "2026-10-17")) // 同任务不同天
        assertNull(DailyKit.dayLog(logs, "t9", "2026-10-18")) // 同天不同任务
        assertNull(DailyKit.dayLog(emptyList(), "t1", "2026-10-18"))
    }

    @Test
    fun `sortedTasks 按 sortOrder 升序`() {
        val tasks = listOf(task("c", "洗脸", 2), task("a", "吃维AD", 0), task("b", "排气操", 1))
        assertEquals(listOf("a", "b", "c"), DailyKit.sortedTasks(tasks).map { it.uuid })
    }

    @Test
    fun `nextSortOrder 空表为0_否则最大值加一`() {
        assertEquals(0, DailyKit.nextSortOrder(emptyList()))
        val tasks = listOf(task("a", "吃维AD", 0), task("b", "排气操", 3))
        assertEquals(4, DailyKit.nextSortOrder(tasks))
    }

    @Test
    fun `taskPayload 类型不符返回null`() {
        assertNull(DailyKit.taskPayload(log("l1", "t1", "2026-10-18")))
        assertNotNull(DailyKit.taskPayload(task("t1", "洗脸", 0)))
    }

    @Test
    fun `DEFAULT_TASKS 预置 5 项`() {
        assertEquals(5, DailyKit.DEFAULT_TASKS.size)
        assertTrue(DailyKit.DEFAULT_TASKS.all { it.isNotBlank() })
    }
}
