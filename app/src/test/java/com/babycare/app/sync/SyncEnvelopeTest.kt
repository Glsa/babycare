package com.babycare.app.sync

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 传输信封单测（0016 K5）：往返对称 / 缺键兜底 / 坏 JSON 防御 / 不污染原文 */
class SyncEnvelopeTest {

    @Test
    fun `wrap 注入两个保留键`() {
        val wrapped = SyncEnvelope.wrap("""{"note":"x"}""", occurredAt = 123L, babyId = "b1")
        assertEquals(JsonPrimitive(123L), wrapped["_occurred_at_ms"])
        assertEquals(JsonPrimitive("b1"), wrapped["_baby_id"])
        assertEquals(JsonPrimitive("x"), wrapped["note"])
    }

    @Test
    fun `往返对称：wrap 后 unwrap 还原原文`() {
        val original = """{"note":"饭后","amount":30}"""
        val unwrapped = SyncEnvelope.unwrap(SyncEnvelope.wrap(original, 456L, "b2"))
        assertEquals(456L, unwrapped.occurredAt)
        assertEquals("b2", unwrapped.babyId)
        assertEquals(original, unwrapped.payload)
    }

    @Test
    fun `unwrap 剥掉信封键不留在本地 payload`() {
        val unwrapped = SyncEnvelope.unwrap(SyncEnvelope.wrap("{}", 1L, "b"))
        assertTrue(!unwrapped.payload.contains("_occurred_at_ms"))
        assertTrue(!unwrapped.payload.contains("_baby_id"))
    }

    @Test
    fun `缺键时给 null 由调用方兜底`() {
        val unwrapped = SyncEnvelope.unwrap(
            kotlinx.serialization.json.JsonObject(mapOf("note" to JsonPrimitive("plain"))),
        )
        assertNull(unwrapped.occurredAt)
        assertNull(unwrapped.babyId)
        assertEquals("""{"note":"plain"}""", unwrapped.payload)
    }

    @Test
    fun `坏 JSON 退化为空对象仍可同步`() {
        val wrapped = SyncEnvelope.wrap("not a {json", 9L, "b9")
        assertEquals(JsonPrimitive(9L), wrapped["_occurred_at_ms"])
        val unwrapped = SyncEnvelope.unwrap(wrapped)
        assertEquals("{}", unwrapped.payload)
    }

    @Test
    fun `保留键下划线前缀与 camelCase 字段零冲突`() {
        // 现有 payload 全是 camelCase，下划线开头键不会被业务编解码读到（ignoreUnknownKeys）
        val wrapped = SyncEnvelope.wrap("""{"bathNote":"水温"}""", 1L, "b")
        assertEquals("""{"bathNote":"水温","_occurred_at_ms":1,"_baby_id":"b"}""", wrapped.toString())
    }
}
