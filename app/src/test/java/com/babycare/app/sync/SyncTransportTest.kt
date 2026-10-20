package com.babycare.app.sync

import com.babycare.app.data.model.RecordJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 传输层单测（0016）：MockWebServer 验请求形状（令牌/路径/查询参数/信封字段）
 * 与状态码→异常归类（401/400/500/拒连）。
 */
class SyncTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var base: String
    private lateinit var transport: OkHttpSyncTransport
    private var token = "tok-123"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        base = server.url("/").toString().trimEnd('/')
        transport = OkHttpSyncTransport(baseUrl = { base }, token = { token })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `push 携带令牌且只发契约五字段`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"applied":1,"skipped":0,"cursor":9}"""))
        val resp = transport.push(
            listOf(
                PushChange(
                    uuid = "u1", type = "diaper",
                    payload = JsonObject(mapOf("note" to JsonPrimitive("wet"))),
                    updatedAt = 123L, deleted = false,
                ),
            ),
        )
        assertEquals(9L, resp.cursor)

        val req = server.takeRequest()
        assertEquals("/v1/sync/push", req.path)
        assertEquals("Bearer tok-123", req.getHeader("Authorization"))
        val change = RecordJson.parseToJsonElement(req.body.readUtf8())
            .jsonObject["changes"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("uuid", "type", "payload", "updated_at", "deleted"), change.keys)
    }

    @Test
    fun `pull 携带游标与上限查询参数`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"changes":[],"next_cursor":15,"has_more":false}"""))
        transport.pull(cursor = 7, limit = 500)
        val path = server.takeRequest().path!!
        assertTrue(path.startsWith("/v1/sync/pull?"))
        assertTrue(path.contains("cursor=7"))
        assertTrue(path.contains("limit=500"))
    }

    @Test
    fun `ping 不带令牌`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        transport.ping(base)
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `401 归类 AUTH`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val e = runCatching { transport.pull(0, 100) }.exceptionOrNull() as SyncException
        assertEquals(SyncException.Kind.AUTH, e.kind)
    }

    @Test
    fun `400 解析错误码归类 CLIENT`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"error":"pairing_code_expired"}"""),
        )
        val e = runCatching { transport.pair(base, "000000", "爸爸的手机") }
            .exceptionOrNull() as SyncException
        assertEquals(SyncException.Kind.CLIENT, e.kind)
        assertEquals("pairing_code_expired", e.code)
    }

    @Test
    fun `500 归类 SERVER`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        val e = runCatching { transport.push(emptyList()) }.exceptionOrNull() as SyncException
        assertEquals(SyncException.Kind.SERVER, e.kind)
    }

    @Test
    fun `连接拒绝归类 NETWORK`() = runBlocking {
        val dead = OkHttpSyncTransport(baseUrl = { "http://127.0.0.1:1" }, token = { null })
        val e = runCatching { dead.ping("http://127.0.0.1:1") }.exceptionOrNull() as SyncException
        assertEquals(SyncException.Kind.NETWORK, e.kind)
    }
}
