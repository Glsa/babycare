package com.babycare.app.sync

import com.babycare.app.data.model.RecordJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DTO golden 测试（0016）：样例取自 0001 契约 §3，逐字段钉死蛇形命名，
 * 后端改字段名时这里先红。
 */
class SyncApiDtoTest {

    @Test
    fun `绑定响应`() {
        val resp = RecordJson.decodeFromString(
            BindResponse.serializer(),
            """{"family_id":"f1","device_id":"d1","token":"tok","pairing_code":"123456","code_expires_at":1729000000}""",
        )
        assertEquals("f1", resp.familyId)
        assertEquals("d1", resp.deviceId)
        assertEquals("tok", resp.token)
        assertEquals("123456", resp.pairingCode)
        assertEquals(1729000000L, resp.codeExpiresAt)
    }

    @Test
    fun `配对码信息`() {
        val info = RecordJson.decodeFromString(
            PairingInfo.serializer(),
            """{"pairing_code":"654321","code_expires_at":42}""",
        )
        assertEquals("654321", info.code)
        assertEquals(42L, info.expiresAt)
    }

    @Test
    fun `设备列表`() {
        // DevicesResponse 为文件私有类，此处按契约样例解 devices 数组，钉死 DeviceInfo 字段名
        val sample = """{"devices":[{"device_id":"d1","name":"爸爸的手机","last_seen_at":7},
            {"device_id":"d2","name":"妈妈的平板","last_seen_at":9}]}"""
        val arr = RecordJson.parseToJsonElement(sample).jsonObject["devices"]!!.jsonArray
        val devices = arr.map { RecordJson.decodeFromJsonElement(DeviceInfo.serializer(), it) }
        assertEquals(listOf("d1", "d2"), devices.map { it.deviceId })
        assertEquals("爸爸的手机", devices.first().name)
        assertEquals(7L, devices.first().lastSeenAt)
    }

    @Test
    fun `上行变更只带契约五字段（不带 author）`() {
        val json = RecordJson.encodeToString(
            PushChange.serializer(),
            PushChange(
                uuid = "u1", type = "diaper",
                payload = JsonObject(mapOf("note" to JsonPrimitive("wet"))),
                updatedAt = 123L, deleted = false,
            ),
        )
        val keys = RecordJson.parseToJsonElement(json).jsonObject.keys
        assertEquals(setOf("uuid", "type", "payload", "updated_at", "deleted"), keys)
    }

    @Test
    fun `推响应`() {
        val resp = RecordJson.decodeFromString(
            PushResponse.serializer(),
            """{"applied":3,"skipped":1,"cursor":42}""",
        )
        assertEquals(3, resp.applied)
        assertEquals(1, resp.skipped)
        assertEquals(42L, resp.cursor)
    }

    @Test
    fun `下行变更含 author 与 seq`() {
        val resp = RecordJson.decodeFromString(
            PullResponse.serializer(),
            """{"changes":[{"uuid":"u1","type":"sleep","payload":{"startAt":1},"updated_at":99,
               "deleted":false,"author_device_id":"d2","seq":7}],
               "next_cursor":7,"has_more":true}""".replace("\n", " "),
        )
        val c = resp.changes.single()
        assertEquals("u1", c.uuid)
        assertEquals(99L, c.updatedAt)
        assertEquals("d2", c.authorDeviceId)
        assertEquals(7L, c.seq)
        assertEquals(7L, resp.nextCursor)
        assertEquals(true, resp.hasMore)
    }
}
