package com.babycare.app.sync

import com.babycare.app.data.model.RecordJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 传输信封（0016 K5）：后端 records 表没有 occurredAt/babyId 两列（契约预期它们随 payload 走，
 * 0001 §3 示例可证），本地则是查询冗余列。上行把列值注入 payload 的两个保留键，下行抽出重建列。
 * - 下划线前缀与现有 camelCase 字段零冲突；解码 ignoreUnknownKeys，旧版本读新数据无感
 * - 往返对称：本地存的 payload 剥掉信封键，不污染原文
 * 纯函数，无 Android 依赖，单测覆盖（SyncEnvelopeTest）。
 */
object SyncEnvelope {

    private const val KEY_OCCURRED = "_occurred_at_ms"
    private const val KEY_BABY = "_baby_id"

    /** 上行：payload 原文注入列值。坏 JSON 退化为空对象（记录仍可同步，与 decodePayloadOrNull 同防御口径） */
    fun wrap(payloadJson: String, occurredAt: Long, babyId: String): JsonObject {
        val base = runCatching { RecordJson.parseToJsonElement(payloadJson) as? JsonObject }
            .getOrNull() ?: JsonObject(emptyMap())
        return JsonObject(base + mapOf(KEY_OCCURRED to JsonPrimitive(occurredAt), KEY_BABY to JsonPrimitive(babyId)))
    }

    /** 下行解包结果：键缺失时为 null，由调用方兜底（occurredAt→updatedAt；babyId→本地行/活跃宝宝） */
    data class Unwrapped(val occurredAt: Long?, val babyId: String?, val payload: String)

    fun unwrap(payload: JsonObject): Unwrapped {
        val occurred = (payload[KEY_OCCURRED] as? JsonPrimitive)?.longOrNull
        val baby = (payload[KEY_BABY] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val cleaned = payload.toMutableMap().apply { remove(KEY_OCCURRED); remove(KEY_BABY) }
        return Unwrapped(occurred, baby, JsonObject(cleaned).toString())
    }
}
