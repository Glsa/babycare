package com.babycare.app.data.repo

import com.babycare.app.data.dao.MetaDao
import com.babycare.app.data.dao.RecordDao
import com.babycare.app.data.entity.MetaEntity
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.MetaKeys
import com.babycare.app.data.model.NursingTimerState
import com.babycare.app.data.model.RecordJson
import com.babycare.app.data.model.RecordPayload
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.SleepPayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.data.model.encodePayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * 记录仓库：App 内所有记录读写走这里。
 * - uuid 生成一次后不改；每次写 updatedAt 推进（LWW 依据）
 * - 删除一律软删（同步墓碑）
 */
class BabyRepository(
    private val recordDao: RecordDao,
    private val metaDao: MetaDao,
    private val author: String,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** 记录变更信号（Widget 等非 UI 下游用）：丢旧保新，无订阅者不挂起 */
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes

    /** 新增一条记录，返回完整实体（供 UI 即时反馈） */
    suspend fun add(
        type: String,
        payload: RecordPayload,
        occurredAt: Long,
        babyId: String,
        uuid: String = UUID.randomUUID().toString(),
    ): RecordEntity {
        val entity = RecordEntity(
            uuid = uuid,
            type = type,
            payload = encodePayload(payload),
            occurredAt = occurredAt,
            updatedAt = now(),
            deleted = false,
            author = author,
            babyId = babyId,
        )
        recordDao.upsert(entity)
        _changes.tryEmit(Unit)
        return entity
    }

    /** 编辑：改 payload / occurredAt，updatedAt 推进；本地编辑必须重新置脏（上行标记不随 copy 继承） */
    suspend fun update(entity: RecordEntity, payload: RecordPayload, occurredAt: Long = entity.occurredAt) {
        recordDao.upsert(
            entity.copy(payload = encodePayload(payload), occurredAt = occurredAt, updatedAt = now(), dirty = true),
        )
        _changes.tryEmit(Unit)
    }

    suspend fun softDelete(entity: RecordEntity) {
        recordDao.upsert(entity.copy(deleted = true, updatedAt = now(), dirty = true))
        _changes.tryEmit(Unit)
    }

    fun observeDay(babyId: String, start: Long, end: Long): Flow<List<RecordEntity>> =
        recordDao.observeRange(babyId, start, end)

    /** 一次性当日记录（Widget 快照用；复用 observeRange 取首值，不另加 DAO 查询） */
    suspend fun dayOnce(babyId: String, start: Long, end: Long): List<RecordEntity> =
        recordDao.observeRange(babyId, start, end).first()

    /** 距上次喂奶：不限今天（跨天也准） */
    fun observeLastFeed(babyId: String): Flow<RecordEntity?> =
        recordDao.observeLast(babyId, listOf(RecordTypes.FEED_FORMULA, RecordTypes.FEED_NURSING))

    fun observeBaby(): Flow<List<RecordEntity>> = recordDao.observeByType(RecordTypes.BABY_PROFILE)

    /** 行程（量小，全量拉、内存分区） */
    fun observeAgendas(): Flow<List<RecordEntity>> = recordDao.observeByType(RecordTypes.AGENDA)

    /** 按类型观察全量记录（成长页的生长/疫苗、后续补剂等，量小内存过滤） */
    fun observeByType(type: String): Flow<List<RecordEntity>> = recordDao.observeByType(type)

    /** 进行中的睡眠 = endAt=null 的 sleep 记录（occurredAt 倒序取最新一条；V1 单宝宝不区分 babyId） */
    fun observeOngoingSleep(): Flow<RecordEntity?> = recordDao.observeByType(RecordTypes.SLEEP)
        .map { list -> list.firstOrNull { isOngoingSleep(it) } }

    suspend fun ongoingSleepOnce(): RecordEntity? = recordDao.byType(RecordTypes.SLEEP).firstOrNull { isOngoingSleep(it) }

    private fun isOngoingSleep(e: RecordEntity): Boolean =
        (decodePayloadOrNull(e.type, e.payload) as? SleepPayload)?.endAt == null

    // ---- 亲喂计时暂态（meta 单行 JSON） ----

    fun observeNursingTimer(): Flow<NursingTimerState?> = metaDao.observe(MetaKeys.ACTIVE_NURSING)
        .map { it?.let(::decodeNursingTimer) }

    suspend fun nursingTimerOnce(): NursingTimerState? = decodeNursingTimer(metaDao.get(MetaKeys.ACTIVE_NURSING))

    suspend fun writeNursingTimer(state: NursingTimerState?) {
        if (state == null) metaDao.delete(MetaKeys.ACTIVE_NURSING)
        else metaPut(MetaKeys.ACTIVE_NURSING, RecordJson.encodeToString(NursingTimerState.serializer(), state))
    }

    /** 坏 JSON 当无计时（旧版本/手改数据），不崩 */
    private fun decodeNursingTimer(json: String?): NursingTimerState? = json?.let {
        runCatching { RecordJson.decodeFromString(NursingTimerState.serializer(), it) }.getOrNull()
    }

    suspend fun byUuid(uuid: String): RecordEntity? = recordDao.byUuid(uuid)

    /** 该类型是否存在任何行（含墓碑）——播种判定用（0016 K9：墓碑在 = 不补默认清单） */
    suspend fun hasAnyType(type: String): Boolean = recordDao.hasAny(type)

    // 下行合并（LWW 判定 + 事务）在 sync/SyncStore 的 RoomStore，不走本仓库——
    // 下行写入不置脏、不发 changes（避免「拉取→触发推送」回环），与本地写路径语义不同。

    suspend fun metaGet(key: String): String? = metaDao.get(key)

    suspend fun metaPut(key: String, value: String) = metaDao.put(MetaEntity(key, value))

    /** 观察 meta 键变化（0016 设备名映射用） */
    fun observeMeta(key: String): Flow<String?> = metaDao.observe(key)

    // ---- 备份导入导出（FR-19；LWW 判定在 BackupCodec，与同步同一套） ----

    /** 全量记录含软删墓碑（导出快照用） */
    suspend fun allOnce(): List<RecordEntity> = recordDao.allOnce()

    /** 导入落地（BackupCodec.planImport 已过滤，这里只批量写） */
    suspend fun importAll(entities: List<RecordEntity>) {
        if (entities.isEmpty()) return
        recordDao.upsertAll(entities)
        _changes.tryEmit(Unit)
    }

    /** 清空全部数据：记录 + meta；进行中计时先由调用方走 TimerCenter 取消 */
    suspend fun wipeAll() {
        recordDao.deleteAll()
        metaDao.clear()
        _changes.tryEmit(Unit)
    }

    /**
     * 同步态清空（0016 K8）：全表墓碑化置脏，走推送让对端一并消失；不做物理删（墓碑是同步语义的一部分）。
     * meta 不清（游标与设备名映射要继续用），active_nursing 由调用方照常先取消。
     */
    suspend fun tombstoneAll() {
        recordDao.tombstoneAll(now())
        _changes.tryEmit(Unit)
    }

    /** 换挂宝宝（0016 K11 档案收敛）：输家档案名下记录改挂赢家，置脏走推送 */
    suspend fun reassignBaby(fromBabyId: String, toBabyId: String) {
        recordDao.reassignBaby(fromBabyId, toBabyId, now())
        _changes.tryEmit(Unit)
    }
}
