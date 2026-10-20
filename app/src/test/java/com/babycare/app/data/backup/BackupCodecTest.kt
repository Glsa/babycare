package com.babycare.app.data.backup

import com.babycare.app.data.entity.RecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 备份编解码与 LWW 导入合并（FR-19）：导入语义必须与云同步（SyncMerge）完全一致 */
class BackupCodecTest {

    private fun entity(
        uuid: String = "u1",
        type: String = "feed_formula",
        updatedAt: Long = 100L,
        deleted: Boolean = false,
    ) = RecordEntity(
        uuid = uuid, type = type, payload = """{"ml":120}""", occurredAt = 50L,
        updatedAt = updatedAt, deleted = deleted, author = "dev-1", babyId = "baby-1",
    )

    @Test
    fun `round trip 全字段一致（含 payload 原文）`() {
        val list = listOf(
            entity(), entity(uuid = "u2", deleted = true),
            entity(uuid = "u3", type = "sleep", updatedAt = 999L),
        )
        val doc = BackupCodec.decode(BackupCodec.encode(list, exportedAt = 123L))!!
        assertEquals(123L, doc.exportedAt)
        assertEquals(BackupCodec.VERSION, doc.version)
        assertEquals(3, doc.records.size)
        list.zip(doc.records).forEach { (e, r) ->
            assertEquals(e.uuid, r.uuid)
            assertEquals(e.type, r.type)
            assertEquals(e.payload, r.payload)
            assertEquals(e.occurredAt, r.occurredAt)
            assertEquals(e.updatedAt, r.updatedAt)
            assertEquals(e.deleted, r.deleted)
            assertEquals(e.author, r.author)
            assertEquals(e.babyId, r.babyId)
            assertEquals(e.payload, r.toEntity().payload) // 落地还原无损
        }
    }

    @Test
    fun `软删墓碑保留在导出里`() {
        val doc = BackupCodec.decode(BackupCodec.encode(listOf(entity(deleted = true)), 1L))!!
        assertTrue(doc.records.single().deleted)
    }

    @Test
    fun `LWW 覆盖 - 备份更新时间更大才写入`() {
        val plan = BackupCodec.planImport(
            incoming = listOf(entity(updatedAt = 200L).let { BackupRecord.fromEntity(it) }),
            localUpdatedAt = mapOf("u1" to 100L),
        )
        assertEquals(1, plan.updateCount)
        assertEquals(0, plan.newCount)
        assertEquals(1, plan.toApply.size)
    }

    @Test
    fun `LWW 拒绝 - 旧备份不覆盖新数据（相等也拒）`() {
        val rec = BackupRecord.fromEntity(entity(updatedAt = 100L))
        val planOld = BackupCodec.planImport(listOf(rec), mapOf("u1" to 200L))
        val planEqual = BackupCodec.planImport(listOf(rec), mapOf("u1" to 100L))
        assertTrue(planOld.toApply.isEmpty())
        assertTrue(planEqual.toApply.isEmpty())
        assertEquals(0, planOld.updateCount)
    }

    @Test
    fun `本地不存在的 uuid 计入新增`() {
        val plan = BackupCodec.planImport(
            incoming = listOf(
                BackupRecord.fromEntity(entity(uuid = "new-1")),
                BackupRecord.fromEntity(entity(uuid = "u1")),
            ),
            localUpdatedAt = mapOf("u1" to 100L),
        )
        assertEquals(1, plan.newCount)
        assertEquals(0, plan.updateCount)
        // u1 更新时间相等被 LWW 拒绝，只有 new-1 待写入
        assertEquals(1, plan.toApply.size)
        assertEquals("new-1", plan.toApply.single().uuid)
    }

    @Test
    fun `空本地库全部为新增`() {
        val plan = BackupCodec.planImport(
            incoming = (1..3).map { BackupRecord.fromEntity(entity(uuid = "u$it")) },
            localUpdatedAt = emptyMap(),
        )
        assertEquals(3, plan.newCount)
        assertEquals(3, plan.toApply.size)
    }

    @Test
    fun `坏文件返回 null 不抛（截断 非JSON 空记录）`() {
        val good = BackupCodec.encode(listOf(entity()), 1L)
        assertNull(BackupCodec.decode(good.substring(0, good.length / 2)))
        assertNull(BackupCodec.decode("这不是JSON"))
        assertNull(BackupCodec.decode("""{"version":1,"exportedAt":0,"records":[]}"""))
    }
}
