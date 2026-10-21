package com.babycare.app.sync

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.DailyTaskPayload
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.encodePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分叉收敛规划单测（0016 K11）：双机各自建档 → 档案保最旧、平局比 uuid、
 * 换挂清单正确；每日必做同名去重；坏 payload 不误伤。
 */
class SyncConvergeTest {

    private fun profile(uuid: String, updatedAt: Long, deleted: Boolean = false) = RecordEntity(
        uuid = uuid, type = RecordTypes.BABY_PROFILE, payload = """{"name":"果果"}""",
        occurredAt = updatedAt, updatedAt = updatedAt, deleted = deleted,
        author = "dev-1", babyId = uuid, dirty = false,
    )

    private fun task(uuid: String, name: String, updatedAt: Long) = RecordEntity(
        uuid = uuid, type = RecordTypes.DAILY_TASK, payload = encodePayload(DailyTaskPayload(name)),
        occurredAt = updatedAt, updatedAt = updatedAt, deleted = false,
        author = "dev-1", babyId = "baby-1", dirty = false,
    )

    private fun feed(uuid: String, updatedAt: Long, babyId: String) = RecordEntity(
        uuid = uuid, type = RecordTypes.FEED_FORMULA, payload = """{"ml":60}""",
        occurredAt = updatedAt, updatedAt = updatedAt, deleted = false,
        author = "dev-1", babyId = babyId, dirty = false,
    )

    @Test
    fun `双档案保updatedAt最旧`() {
        val old = profile("p-old", updatedAt = 100L)
        val new = profile("p-new", updatedAt = 200L)
        val plan = SyncConverge.plan(listOf(old, new))
        assertEquals(old.uuid, plan.babyWinner?.uuid)
        assertEquals(listOf(new), plan.babyLosers)
    }

    @Test
    fun `平局比uuid字典序`() {
        // 两台设备几乎同时建档 → updatedAt 相同，必须靠 uuid 分出唯一赢家
        val a = profile("p-a", updatedAt = 100L)
        val b = profile("p-b", updatedAt = 100L)
        val plan = SyncConverge.plan(listOf(b, a))
        assertEquals(a.uuid, plan.babyWinner?.uuid)
        assertEquals(listOf(b), plan.babyLosers)
    }

    @Test
    fun `单档案无动作`() {
        val only = profile("p-only", updatedAt = 100L)
        val plan = SyncConverge.plan(listOf(only, feed("f1", 1L, "p-only")))
        assertTrue(!plan.hasWork)
        assertNull(plan.babyWinner)
    }

    @Test
    fun `墓碑档案不参与`() {
        // 输家被打墓碑推下去后的下一轮：只剩一份活档案，不得再起冲突
        val live = profile("p-live", updatedAt = 100L)
        val dead = profile("p-dead", updatedAt = 50L, deleted = true)
        val plan = SyncConverge.plan(listOf(live, dead))
        assertTrue(!plan.hasWork)
    }

    @Test
    fun `任务同名去重保最旧`() {
        val keep = task("t1", "抬头练习", updatedAt = 100L)
        val drop = task("t2", "抬头练习", updatedAt = 200L)
        val other = task("t3", "抚触", updatedAt = 300L)
        val plan = SyncConverge.plan(listOf(keep, drop, other))
        assertEquals(listOf(drop), plan.taskLosers)
        assertTrue(plan.babyLosers.isEmpty())
    }

    @Test
    fun `任务异名各自保留`() {
        val a = task("t1", "抬头练习", updatedAt = 100L)
        val b = task("t2", "看黑白卡", updatedAt = 200L)
        val plan = SyncConverge.plan(listOf(a, b))
        assertTrue(!plan.hasWork)
    }

    @Test
    fun `坏payload任务不归并不误删`() {
        // 坏 JSON 解不出名字 → 拿 uuid 自成一组，既不吞掉同名真任务也不被别人吞
        val good = task("t1", "抬头练习", updatedAt = 100L)
        val broken = RecordEntity(
            uuid = "t2", type = RecordTypes.DAILY_TASK, payload = """{not-json""",
            occurredAt = 1L, updatedAt = 1L, deleted = false,
            author = "dev-1", babyId = "baby-1", dirty = false,
        )
        val plan = SyncConverge.plan(listOf(good, broken))
        assertTrue(!plan.hasWork)
    }

    @Test
    fun `混合场景档案与任务一并规划`() {
        val profiles = listOf(profile("p-a", 100L), profile("p-b", 200L))
        val tasks = listOf(task("t1", "抬头练习", 100L), task("t2", "抬头练习", 150L))
        val plan = SyncConverge.plan(profiles + tasks + feed("f1", 1L, "p-b"))
        assertEquals("p-a", plan.babyWinner?.uuid)
        assertEquals(listOf("p-b"), plan.babyLosers.map { it.uuid })
        assertEquals(listOf("t2"), plan.taskLosers.map { it.uuid })
    }
}
