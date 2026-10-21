package com.babycare.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babycare.app.data.entity.RecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: RecordEntity)

    /** 按 occurredAt 区间查未删记录（今日/某日视图），限定宝宝 */
    @Query("SELECT * FROM records WHERE deleted = 0 AND babyId = :babyId AND occurredAt >= :start AND occurredAt < :end ORDER BY occurredAt DESC")
    fun observeRange(babyId: String, start: Long, end: Long): Flow<List<RecordEntity>>

    /** 某宝宝指定类型里最近一条（不限今天，距上次喂奶用） */
    @Query("SELECT * FROM records WHERE deleted = 0 AND babyId = :babyId AND type IN (:types) ORDER BY occurredAt DESC LIMIT 1")
    fun observeLast(babyId: String, types: List<String>): Flow<RecordEntity?>

    /** 按类型查未删记录（宝宝档案、生长列表、疫苗列表等） */
    @Query("SELECT * FROM records WHERE type = :type AND deleted = 0 ORDER BY occurredAt DESC")
    fun observeByType(type: String): Flow<List<RecordEntity>>

    /** 同上的单次版（前台服务重建通知用） */
    @Query("SELECT * FROM records WHERE type = :type AND deleted = 0 ORDER BY occurredAt DESC")
    suspend fun byType(type: String): List<RecordEntity>

    @Query("SELECT * FROM records WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): RecordEntity?

    /** 全量记录含软删墓碑（导出备份用） */
    @Query("SELECT * FROM records")
    suspend fun allOnce(): List<RecordEntity>

    /** 批量写入（导入备份；单参数单事务） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<RecordEntity>)

    /** 清空（我的页危险操作） */
    @Query("DELETE FROM records")
    suspend fun deleteAll()

    // ---- 同步（0016）----

    /** 待上行记录（先老后新，保证编辑链完整上行） */
    @Query("SELECT * FROM records WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    suspend fun dirtyOnce(limit: Int): List<RecordEntity>

    /** 推送确认清脏：updatedAt 双条件——推送在途时的新编辑（updatedAt 已变）保持脏，下轮重推 */
    @Query("UPDATE records SET dirty = 0 WHERE uuid = :uuid AND updatedAt = :updatedAt")
    suspend fun markPushed(uuid: String, updatedAt: Long)

    /** 首次绑定：全量置脏（历史记录全量上行走同一推送路径） */
    @Query("UPDATE records SET dirty = 1")
    suspend fun markAllDirty()

    /** 该类型是否存在任何行（含墓碑）：播种判定用——墓碑在 = 曾播种/曾同步，不再补默认清单 */
    @Query("SELECT EXISTS(SELECT 1 FROM records WHERE type = :type)")
    suspend fun hasAny(type: String): Boolean

    /** 待上行条数（状态展示） */
    @Query("SELECT COUNT(*) FROM records WHERE dirty = 1")
    suspend fun countDirty(): Int

    /** 同步态清空（K8）：全表墓碑化后走推送，对端一并消失；物理删除仅留给未开同步的设备 */
    @Query("UPDATE records SET deleted = 1, dirty = 1, updatedAt = :now")
    suspend fun tombstoneAll(now: Long)

    /** 换挂宝宝（K11 档案收敛）：updatedAt 必须推进——babyId 在服务端只存在于信封里，
     *  时间戳不变会被 LWW 判为同版跳过，对端就收不到换挂 */
    @Query("UPDATE records SET babyId = :to, updatedAt = :at, dirty = 1 WHERE babyId = :from AND deleted = 0")
    suspend fun reassignBaby(from: String, to: String, at: Long)
}
