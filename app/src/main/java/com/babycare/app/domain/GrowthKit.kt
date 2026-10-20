package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.GrowthPayload
import com.babycare.app.data.model.decodePayloadOrNull

/** 三项各自的最新一条（值非空才算） */
data class GrowthLatest(
    val weight: Pair<RecordEntity, GrowthPayload>? = null,
    val height: Pair<RecordEntity, GrowthPayload>? = null,
    val head: Pair<RecordEntity, GrowthPayload>? = null,
)

/** 生长记录的查看口径（FR-7）：最新值 + 倒序历史，都是纯内存计算 */
object GrowthKit {

    /** 各项独立取 occurredAt 最新且值非空的一条 */
    fun latest(records: List<RecordEntity>): GrowthLatest {
        val decoded = decodeAll(records)
        fun pick(selector: (GrowthPayload) -> Double?) =
            decoded.filter { selector(it.second) != null }.maxByOrNull { it.first.occurredAt }
        return GrowthLatest(
            weight = pick { it.weightKg },
            height = pick { it.heightCm },
            head = pick { it.headCm },
        )
    }

    /** 历史倒序（新的在前） */
    fun history(records: List<RecordEntity>): List<Pair<RecordEntity, GrowthPayload>> =
        decodeAll(records).sortedByDescending { it.first.occurredAt }

    private fun decodeAll(records: List<RecordEntity>): List<Pair<RecordEntity, GrowthPayload>> =
        records.mapNotNull { r ->
            decodePayloadOrNull(r.type, r.payload)?.let { it as? GrowthPayload }?.let { r to it }
        }
}
