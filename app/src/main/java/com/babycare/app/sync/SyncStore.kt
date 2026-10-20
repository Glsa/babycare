package com.babycare.app.sync

import androidx.room.withTransaction
import com.babycare.app.data.db.BabyDatabase
import com.babycare.app.data.entity.MetaEntity
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.MetaKeys
import com.babycare.app.data.model.SyncMerge

/**
 * 引擎视角的窄存储缝（0016 K7）：引擎只认本接口，Room 细节（事务/游标放 meta 表）留在实现里。
 * 单测用内存 fake 直接实现本接口，不需要 Robolectric。
 */
interface SyncStore {
    /** 本地拉取游标（meta SYNC_CURSOR；0 = 从头拉） */
    suspend fun cursor(): Long

    /** push 响应带的家庭最新 seq：直接抬高游标，避免回读自己刚推的数据（只升不降） */
    suspend fun raiseCursorTo(cursor: Long)

    suspend fun dirtyOnce(limit: Int): List<RecordEntity>

    /** 推送确认清脏：(uuid, updatedAt) 双条件——推送在途的新编辑保持脏 */
    suspend fun markPushed(uuid: String, updatedAt: Long)

    /** 首次绑定全量置脏 */
    suspend fun markAllDirty()

    suspend fun countDirty(): Int

    /** 下行应用（LWW + 信封重建 + 游标推进，事务内） */
    suspend fun applyPulled(changes: List<PulledChange>, nextCursor: Long): Int
}

/**
 * 下行应用纯规划（可单测）：LWW 过滤 + 信封解包 + 实体重建（dirty=false——已到服务端状态）。
 * 兜底：信封缺 occurredAt → 用 updatedAt；缺 babyId → 本地同 uuid 行的 babyId → 活跃宝宝 → 空串。
 */
object SyncApply {
    fun plan(
        changes: List<PulledChange>,
        localByUuid: (String) -> RecordEntity?,
        fallbackBabyId: () -> String,
    ): List<RecordEntity> = changes.mapNotNull { c ->
        val local = localByUuid(c.uuid)
        if (local != null && !SyncMerge.shouldApply(local.updatedAt, c.updatedAt)) return@mapNotNull null
        val unwrapped = SyncEnvelope.unwrap(c.payload)
        RecordEntity(
            uuid = c.uuid,
            type = c.type,
            payload = unwrapped.payload,
            occurredAt = unwrapped.occurredAt ?: c.updatedAt,
            updatedAt = c.updatedAt,
            deleted = c.deleted,
            author = c.authorDeviceId,
            babyId = unwrapped.babyId ?: local?.babyId ?: fallbackBabyId(),
            dirty = false,
        )
    }
}

/** Room 实现：批次应用 + 游标推进包一个事务，崩溃不产生「应用了但游标没走」的重复拉取 */
class RoomStore(private val db: BabyDatabase) : SyncStore {

    private val recordDao = db.recordDao()
    private val metaDao = db.metaDao()

    override suspend fun cursor(): Long =
        metaDao.get(MetaKeys.SYNC_CURSOR)?.toLongOrNull() ?: 0L

    override suspend fun raiseCursorTo(cursor: Long) {
        if (cursor > this.cursor()) metaDao.put(MetaEntity(MetaKeys.SYNC_CURSOR, cursor.toString()))
    }

    override suspend fun dirtyOnce(limit: Int): List<RecordEntity> = recordDao.dirtyOnce(limit)

    override suspend fun markPushed(uuid: String, updatedAt: Long) = recordDao.markPushed(uuid, updatedAt)

    override suspend fun markAllDirty() = recordDao.markAllDirty()

    override suspend fun countDirty(): Int = recordDao.countDirty()

    override suspend fun applyPulled(changes: List<PulledChange>, nextCursor: Long): Int {
        var applied = 0
        db.withTransaction {
            // 挂起查询先取齐，再交给纯函数规划（plan 的缝保持非挂起，单测免协程）
            val localMap = changes.associate { c -> c.uuid to recordDao.byUuid(c.uuid) }
            val fallbackBabyId = metaDao.get(MetaKeys.ACTIVE_BABY_ID) ?: ""
            val planned = SyncApply.plan(changes, { uuid -> localMap[uuid] }, { fallbackBabyId })
            if (planned.isNotEmpty()) {
                recordDao.upsertAll(planned)
                applied = planned.size
            }
            metaDao.put(MetaEntity(MetaKeys.SYNC_CURSOR, nextCursor.toString()))
        }
        return applied
    }
}
