package com.babycare.app.sync

import com.babycare.app.data.model.RecordJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 同步传输层（0016）：7 个接口对接 0001 后端契约 §3。
 * 接口 [SyncTransport] 供引擎/单测注入；实现 [OkHttpSyncTransport] 是唯一碰网络的地方。
 * 字段名用 @SerialName 钉死蛇形命名，golden JSON 测试（SyncApiDtoTest）保证与后端逐字段一致。
 */

// ---- DTO（与契约 §3 一一对应）----

@Serializable
data class BindResponse(
    @SerialName("family_id") val familyId: String,
    @SerialName("device_id") val deviceId: String,
    val token: String,
    @SerialName("pairing_code") val pairingCode: String? = null,
    @SerialName("code_expires_at") val codeExpiresAt: Long? = null,
)

@Serializable
private data class CreateFamilyRequest(@SerialName("family_name") val familyName: String, @SerialName("device_name") val deviceName: String)

@Serializable
private data class PairRequest(@SerialName("pairing_code") val pairingCode: String, @SerialName("device_name") val deviceName: String)

@Serializable
data class PairingInfo(@SerialName("pairing_code") val code: String, @SerialName("code_expires_at") val expiresAt: Long)

@Serializable
data class DeviceInfo(
    @SerialName("device_id") val deviceId: String,
    val name: String,
    @SerialName("last_seen_at") val lastSeenAt: Long,
)

@Serializable
private data class DevicesResponse(val devices: List<DeviceInfo>)

/** 上行变更；author 由服务端从令牌判定，请求不带 */
@Serializable
data class PushChange(
    val uuid: String,
    val type: String,
    val payload: JsonObject,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
)

@Serializable
private data class PushRequest(val changes: List<PushChange>)

@Serializable
data class PushResponse(val applied: Int, val skipped: Int, val cursor: Long)

/** 下行变更（含墓碑 deleted=true，App 据此软删本地行） */
@Serializable
data class PulledChange(
    val uuid: String,
    val type: String,
    val payload: JsonObject,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
    @SerialName("author_device_id") val authorDeviceId: String,
    val seq: Long,
)

@Serializable
data class PullResponse(
    val changes: List<PulledChange>,
    @SerialName("next_cursor") val nextCursor: Long,
    @SerialName("has_more") val hasMore: Boolean,
)

// ---- 错误归一化 ----

/** kind 驱动重试策略：NETWORK/SERVER 退避重试、AUTH 停退避待重绑、CLIENT 视配置动作 */
class SyncException(val kind: Kind, val code: String? = null, cause: Throwable? = null) : Exception(cause) {
    enum class Kind { NETWORK, AUTH, CLIENT, SERVER }

    fun userText(): String = when (kind) {
        Kind.NETWORK -> "网络不通，稍后自动重试"
        Kind.AUTH -> "登录已失效，请重新配对"
        Kind.CLIENT -> "请求被拒绝（${code ?: "bad_request"}）"
        Kind.SERVER -> "服务器开小差了，稍后自动重试"
    }
}

// ---- 传输接口 ----

interface SyncTransport {
    /** 探活（配置页用）：不可达抛 SyncException */
    suspend fun ping(url: String)

    suspend fun createFamily(url: String, familyName: String, deviceName: String): BindResponse

    suspend fun pair(url: String, pairingCode: String, deviceName: String): BindResponse

    suspend fun refreshPairingCode(): PairingInfo

    suspend fun devices(): List<DeviceInfo>

    suspend fun push(changes: List<PushChange>): PushResponse

    suspend fun pull(cursor: Long, limit: Int): PullResponse
}

// ---- OkHttp 实现 ----

class OkHttpSyncTransport(
    private val baseUrl: () -> String,
    private val token: () -> String?,
) : SyncTransport {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        // 家庭自建后端，轻量短请求；超时收紧让「每写即推」失败快速进退避
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun ping(url: String) {
        call<Unit>("GET", url, "/v1/ping", auth = false, body = null) { }
    }

    override suspend fun createFamily(url: String, familyName: String, deviceName: String): BindResponse =
        call("POST", url, "/v1/family", auth = false, body = RecordJson.encodeToString(CreateFamilyRequest.serializer(), CreateFamilyRequest(familyName, deviceName))) {
            RecordJson.decodeFromString(BindResponse.serializer(), it)
        }

    override suspend fun pair(url: String, pairingCode: String, deviceName: String): BindResponse =
        call("POST", url, "/v1/pair", auth = false, body = RecordJson.encodeToString(PairRequest.serializer(), PairRequest(pairingCode, deviceName))) {
            RecordJson.decodeFromString(BindResponse.serializer(), it)
        }

    override suspend fun refreshPairingCode(): PairingInfo =
        call("POST", baseUrl(), "/v1/pairing-code", auth = true, body = "") {
            RecordJson.decodeFromString(PairingInfo.serializer(), it)
        }

    override suspend fun devices(): List<DeviceInfo> =
        call("GET", baseUrl(), "/v1/devices", auth = true, body = null) {
            RecordJson.decodeFromString(DevicesResponse.serializer(), it).devices
        }

    override suspend fun push(changes: List<PushChange>): PushResponse =
        call("POST", baseUrl(), "/v1/sync/push", auth = true, body = RecordJson.encodeToString(PushRequest.serializer(), PushRequest(changes))) {
            RecordJson.decodeFromString(PushResponse.serializer(), it)
        }

    override suspend fun pull(cursor: Long, limit: Int): PullResponse =
        call("GET", baseUrl(), "/v1/sync/pull?cursor=$cursor&limit=$limit", auth = true, body = null) {
            RecordJson.decodeFromString(PullResponse.serializer(), it)
        }

    /** 统一请求骨架：IO 执行、状态码→异常归类、错误体 {"error": code} 解析 */
    private suspend fun <T> call(
        method: String,
        base: String,
        pathAndQuery: String,
        auth: Boolean,
        body: String?,
        parse: (String) -> T,
    ): T = withContext(Dispatchers.IO) {
        val url = base.trimEnd('/') + pathAndQuery
        val request = Request.Builder()
            .url(url)
            .apply { if (auth) token()?.let { header("Authorization", "Bearer $it") } }
            .method(method, body?.toRequestBody(jsonMedia))
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw SyncException(SyncException.Kind.NETWORK, cause = e)
        }
        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            when {
                resp.isSuccessful -> runCatching { parse(text) }
                    .getOrElse { throw SyncException(SyncException.Kind.SERVER, "bad_response", it) }
                resp.code == 401 -> throw SyncException(SyncException.Kind.AUTH)
                resp.code in 400..499 -> throw SyncException(SyncException.Kind.CLIENT, errorCodeOf(text))
                else -> throw SyncException(SyncException.Kind.SERVER, errorCodeOf(text))
            }
        }
    }

    private fun errorCodeOf(text: String): String? = runCatching {
        RecordJson.parseToJsonElement(text).jsonObject["error"]?.toString()?.trim('"')
    }.getOrNull()
}
