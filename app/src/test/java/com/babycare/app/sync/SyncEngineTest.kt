package com.babycare.app.sync

import com.babycare.app.data.entity.RecordEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 同步引擎单测（0016）：分批推送 / 在途编辑不清脏 / 拉取分页 / LWW 与墓碑下行 / 游标抬高。
 * 纯 JUnit + 内存 fake（K7），不碰 Room 与网络。
 */
class SyncEngineTest {

    // ---- fake ----

    private class FakeTransport : SyncTransport {
        val pushes = mutableListOf<List<PushChange>>()
        val pullCursors = mutableListOf<Long>()
        var seq = 100L
        var pullPages = ArrayDeque<PullResponse>()

        override suspend fun ping(url: String) {}
        override suspend fun createFamily(url: String, familyName: String, deviceName: String) =
            error("引擎测试不触达")
        override suspend fun pair(url: String, pairingCode: String, deviceName: String) =
            error("引擎测试不触达")
        override suspend fun refreshPairingCode() = error("引擎测试不触达")
        override suspend fun devices() = error("引擎测试不触达")

        override suspend fun push(changes: List<PushChange>): PushResponse {
            pushes += changes
            seq += changes.size
            return PushResponse(applied = changes.size, skipped = 0, cursor = seq)
        }

        override suspend fun pull(cursor: Long, limit: Int): PullResponse {
            pullCursors += cursor
            return pullPages.removeFirstOrNull() ?: PullResponse(emptyList(), cursor, hasMore = false)
        }
    }

    /** 内存库：行为对齐 RoomStore（markPushed 双条件 / raiseCursorTo 只升 / applyPulled 走 SyncApply） */
    private class FakeStore : SyncStore {
        val rows = mutableListOf<RecordEntity>()
        var cursorValue = 0L
        var markAllDirtyCalls = 0

        /** 模拟一次在途编辑：markPushed(uuid) 时把该行改成新版本（新 updatedAt + dirty），只触发一次 */
        var inFlightEdit: ((String) -> Unit)? = null

        override suspend fun cursor() = cursorValue
        override suspend fun raiseCursorTo(cursor: Long) {
            if (cursor > cursorValue) cursorValue = cursor
        }
        override suspend fun dirtyOnce(limit: Int) = rows.filter { it.dirty }.sortedBy { it.updatedAt }.take(limit)
        override suspend fun markPushed(uuid: String, updatedAt: Long) {
            inFlightEdit?.let { hook -> inFlightEdit = null; hook(uuid) }
            val i = rows.indexOfFirst { it.uuid == uuid }
            if (i >= 0 && rows[i].updatedAt == updatedAt) rows[i] = rows[i].copy(dirty = false)
        }
        override suspend fun markAllDirty() {
            markAllDirtyCalls++
            for (i in rows.indices) rows[i] = rows[i].copy(dirty = true)
        }
        override suspend fun countDirty() = rows.count { it.dirty }
        override suspend fun applyPulled(changes: List<PulledChange>, nextCursor: Long): Int {
            val planned = SyncApply.plan(changes, { uuid -> rows.firstOrNull { it.uuid == uuid } }, { FALLBACK_BABY })
            planned.forEach { p ->
                val i = rows.indexOfFirst { it.uuid == p.uuid }
                if (i >= 0) rows[i] = p else rows.add(p)
            }
            cursorValue = nextCursor
            return planned.size
        }
    }

    // ---- 构造 ----

    private fun entity(uuid: String, updatedAt: Long = 1L, dirty: Boolean = true, deleted: Boolean = false) =
        RecordEntity(
            uuid = uuid, type = "diaper", payload = """{"note":""}""",
            occurredAt = updatedAt, updatedAt = updatedAt, deleted = deleted,
            author = "dev-1", babyId = "baby-1", dirty = dirty,
        )

    private fun pulled(
        uuid: String,
        updatedAt: Long,
        deleted: Boolean = false,
        payload: String = """{"note":""}""",
        type: String = "diaper",
    ) = PulledChange(
        uuid = uuid, type = type,
        payload = SyncEnvelope.wrap(payload, updatedAt, "baby-x"),
        updatedAt = updatedAt, deleted = deleted, authorDeviceId = "dev-2", seq = 1L,
    )

    // ---- 用例 ----

    @Test
    fun `超过500条分批推完`() = runBlocking {
        val t = FakeTransport()
        val s = FakeStore()
        repeat(1201) { s.rows += entity("u$it") }
        SyncEngine(t, s).round()

        assertEquals(listOf(500, 500, 201), t.pushes.map { it.size })
        assertEquals(0, s.countDirty())
        // 游标抬高到最后一批响应值（不回读自己刚推的数据）
        assertEquals(100L + 1201, s.cursorValue)
    }

    @Test
    fun `无脏数据不推送`() = runBlocking {
        val t = FakeTransport()
        val s = FakeStore().apply { rows += entity("u1", dirty = false) }
        SyncEngine(t, s).round()
        assertTrue(t.pushes.isEmpty())
    }

    @Test
    fun `首绑全量置脏后整库上行`() = runBlocking {
        val t = FakeTransport()
        val s = FakeStore().apply { repeat(3) { rows += entity("u$it", dirty = false) } }
        s.markAllDirty()
        SyncEngine(t, s).round()
        assertEquals(1, s.markAllDirtyCalls)
        assertEquals(listOf(3), t.pushes.map { it.size })
    }

    @Test
    fun `在途编辑不被确认清脏`() = runBlocking {
        val t = FakeTransport()
        val s = FakeStore()
        s.rows += entity("u1", updatedAt = 100L)
        // 推送确认到达瞬间本地又写了一版（updatedAt 200）：旧确认不得清新脏
        s.inFlightEdit = { uuid ->
            val i = s.rows.indexOfFirst { it.uuid == uuid }
            s.rows[i] = s.rows[i].copy(updatedAt = 200L, dirty = true)
        }
        SyncEngine(t, s).round()

        assertEquals(listOf(100L, 200L), t.pushes.flatten().map { it.updatedAt })
        assertEquals(0, s.countDirty())
    }

    @Test
    fun `拉取分页追平且游标落在末页`() = runBlocking {
        val t = FakeTransport()
        val s = FakeStore()
        t.pullPages += PullResponse(listOf(pulled("a", 1)), nextCursor = 10, hasMore = true)
        t.pullPages += PullResponse(emptyList(), nextCursor = 20, hasMore = false)
        SyncEngine(t, s).round()

        assertEquals(listOf(0L, 10L), t.pullCursors)
        assertEquals(20L, s.cursorValue)
    }

    @Test
    fun `本地较新的行不被下行覆盖`() = runBlocking {
        val t = FakeTransport()
        val s = FakeStore().apply { rows += entity("u1", updatedAt = 200L) }
        t.pullPages += PullResponse(listOf(pulled("u1", updatedAt = 100)), nextCursor = 5, hasMore = false)
        SyncEngine(t, s).round()

        // push 先行：本行被推上去；下行 100 < 200 被 LWW 丢弃，行内容仍是本地版
        assertEquals(200L, s.rows.single { it.uuid == "u1" }.updatedAt)
    }

    @Test
    fun `墓碑下行软删本地行`() = runBlocking {
        val t = FakeTransport()
        val s = FakeStore().apply { rows += entity("u1", updatedAt = 100L, dirty = false) }
        t.pullPages += PullResponse(listOf(pulled("u1", updatedAt = 200, deleted = true)), nextCursor = 5, hasMore = false)
        SyncEngine(t, s).round()

        val row = s.rows.single { it.uuid == "u1" }
        assertTrue(row.deleted)
        assertFalse(row.dirty)
    }

    @Test
    fun `下行信封重建 occurredAt 与 babyId`() = runBlocking {
        val t = FakeTransport()
        val s = FakeStore()
        val change = pulled("new-1", updatedAt = 300)
        t.pullPages += PullResponse(listOf(change), nextCursor = 9, hasMore = false)
        SyncEngine(t, s).round()

        val row = s.rows.single { it.uuid == "new-1" }
        assertEquals(300L, row.occurredAt) // 信封值还原为列
        assertEquals("baby-x", row.babyId)
        assertEquals("dev-2", row.author)
        assertFalse(row.dirty)
    }

    private companion object {
        const val FALLBACK_BABY = "baby-1"
    }
}
