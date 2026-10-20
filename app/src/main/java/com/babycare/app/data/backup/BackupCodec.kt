package com.babycare.app.data.backup

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.RecordJson
import com.babycare.app.data.model.SyncMerge
import kotlinx.serialization.Serializable

/**
 * 备份文件编解码（FR-19 V1 手动导出/导入）。纯 JVM 无 Android 依赖，可单测。
 * 导出 = 全量记录快照（含软删墓碑，删除也能随备份迁移）；导入 = 按更新时间 LWW 合并，
 * 与云同步（0001 后端 / V1.1 客户端）共用 [SyncMerge] 判定，旧备份永不覆盖新数据。
 */
@Serializable
data class BackupDoc(
    val version: Int,
    val exportedAt: Long,
    val records: List<BackupRecord>,
)

/** 与 RecordEntity 一一对应；payload 保留原文 JSON，编解码对各记录类型零感知 */
@Serializable
data class BackupRecord(
    val uuid: String,
    val type: String,
    val payload: String,
    val occurredAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val author: String,
    val babyId: String,
) {
    fun toEntity() = RecordEntity(
        uuid = uuid, type = type, payload = payload, occurredAt = occurredAt,
        updatedAt = updatedAt, deleted = deleted, author = author, babyId = babyId,
    )

    companion object {
        fun fromEntity(e: RecordEntity) = with(e) {
            BackupRecord(uuid, type, payload, occurredAt, updatedAt, deleted, author, babyId)
        }
    }
}

object BackupCodec {

    const val VERSION = 1

    fun encode(entities: List<RecordEntity>, exportedAt: Long): String = RecordJson.encodeToString(
        BackupDoc.serializer(),
        BackupDoc(version = VERSION, exportedAt = exportedAt, records = entities.map(BackupRecord::fromEntity)),
    )

    /** 坏文件 / 空备份返回 null（UI 统一提示「无法识别」），不抛 */
    fun decode(text: String): BackupDoc? = runCatching {
        RecordJson.decodeFromString(BackupDoc.serializer(), text)
    }.getOrNull()?.takeIf { it.records.isNotEmpty() }

    /** 导入计划：待写入明细 + 新增/更新计数（确认框给用户看规模） */
    data class ImportPlan(val newCount: Int, val updateCount: Int, val toApply: List<BackupRecord>)

    fun planImport(incoming: List<BackupRecord>, localUpdatedAt: Map<String, Long>): ImportPlan {
        var newCount = 0
        var updateCount = 0
        val toApply = mutableListOf<BackupRecord>()
        for (r in incoming) {
            val local = localUpdatedAt[r.uuid]
            when {
                local == null -> { newCount++; toApply += r }
                SyncMerge.shouldApply(local, r.updatedAt) -> { updateCount++; toApply += r }
                // 本地更新时间更大/相等：旧数据不覆盖（与同步下行同语义）
            }
        }
        return ImportPlan(newCount, updateCount, toApply)
    }
}
