package com.babycare.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 全类型记录单表：镜像后端 0001 的同步模型 + 查询冗余列。
 * - uuid 客户端生成后全生命周期不变（同步主键）
 * - updatedAt 是 LWW 依据；页面展示/汇总一律用 occurredAt，两者别混
 * - payload 为类型化 JSON（data/model/Payloads.kt），加字段/加类型零迁移
 * - 删除走 deleted 软删（同步墓碑），物理删除留给将来墓碑 GC
 */
@Entity(
    tableName = "records",
    indices = [Index("type"), Index("occurredAt"), Index("babyId")],
)
data class RecordEntity(
    @PrimaryKey val uuid: String,
    val type: String,
    val payload: String,
    /** 发生时间 ms（可补录改写；查询与汇总用这列） */
    val occurredAt: Long,
    /** 修改时间 ms（LWW 依据，只给同步用） */
    val updatedAt: Long,
    val deleted: Boolean,
    /** 记录设备 ID（同步后即「谁记的」） */
    val author: String,
    /** 多宝宝预留；V1 界面按单宝宝使用 */
    val babyId: String,
    /** 待上行标记（0016）：本地写=true，推送确认/下行应用=false；迁移默认 1（存量状态未知视为待推） */
    val dirty: Boolean = true,
)
