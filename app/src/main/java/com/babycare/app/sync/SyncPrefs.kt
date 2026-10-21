package com.babycare.app.sync

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 同步配置（0016）：设置类数据走 prefs 不进 Room（0010 Q2 先例）。
 * 单一 StateFlow 暴露，写操作同步落盘并推进流。
 */
data class SyncConfig(
    val baseUrl: String = "",
    val token: String? = null,
    val familyId: String? = null,
    val deviceName: String = "",
    /** 服务端视角的本机设备 ID（设备列表里标注「本机」用） */
    val serverDeviceId: String? = null,
    /** 总开关（FR-20）：关 = 回纯本地，数据与脏标记保留，重开续传 */
    val enabled: Boolean = false,
    /** 首轮同步完成标记（K9 播种门闩）：绑定后置 false，首轮成功置 true；解绑清零 */
    val initialSyncDone: Boolean = false,
) {
    val configured: Boolean get() = baseUrl.isNotBlank()
    val bound: Boolean get() = token != null
}

class SyncPrefs(private val prefs: SharedPreferences) {

    private val _config = MutableStateFlow(load())
    val config: StateFlow<SyncConfig> = _config

    private fun load() = SyncConfig(
        baseUrl = prefs.getString(KEY_URL, "").orEmpty(),
        token = prefs.getString(KEY_TOKEN, null),
        familyId = prefs.getString(KEY_FAMILY, null),
        deviceName = prefs.getString(KEY_DEVICE_NAME, "").orEmpty(),
        serverDeviceId = prefs.getString(KEY_SERVER_DEVICE, null),
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        initialSyncDone = prefs.getBoolean(KEY_INITIAL_DONE, false),
    )

    private fun update(next: SyncConfig) {
        prefs.edit()
            .putString(KEY_URL, next.baseUrl)
            .putString(KEY_TOKEN, next.token)
            .putString(KEY_FAMILY, next.familyId)
            .putString(KEY_DEVICE_NAME, next.deviceName)
            .putString(KEY_SERVER_DEVICE, next.serverDeviceId)
            .putBoolean(KEY_ENABLED, next.enabled)
            .putBoolean(KEY_INITIAL_DONE, next.initialSyncDone)
            .apply()
        _config.value = next
    }

    /** 地址先探活再保存（探活在 SyncCenter） */
    fun setBaseUrl(url: String) = update(_config.value.copy(baseUrl = url.trim().trimEnd('/')))

    /** 绑定成功：存 token/家庭，开关打开，首轮标记复位 */
    fun bind(resp: BindResponse, deviceName: String) = update(
        _config.value.copy(
            token = resp.token,
            familyId = resp.familyId,
            deviceName = deviceName.trim(),
            serverDeviceId = resp.deviceId,
            enabled = true,
            initialSyncDone = false,
        ),
    )

    fun setEnabled(on: Boolean) = update(_config.value.copy(enabled = on))

    fun markInitialDone() = update(_config.value.copy(initialSyncDone = true))

    /** 同步协议版本（K12）：落后即一次性重置游标全量重拉；存量已绑设备靠它吃到顺序修复 */
    fun protoVersion(): Int = prefs.getInt(KEY_PROTO_VERSION, 0)

    fun setProtoVersion(v: Int) {
        prefs.edit().putInt(KEY_PROTO_VERSION, v).apply()
    }

    /** 解绑：清令牌与家庭回到未绑定；地址/设备名保留（重绑省输入），本地数据与游标不动 */
    fun unbind() = update(
        _config.value.copy(
            token = null, familyId = null, serverDeviceId = null, enabled = false, initialSyncDone = false,
        ),
    )

    private companion object {
        const val KEY_URL = "sync_base_url"
        const val KEY_TOKEN = "sync_token"
        const val KEY_FAMILY = "sync_family_id"
        const val KEY_DEVICE_NAME = "sync_device_name"
        const val KEY_SERVER_DEVICE = "sync_server_device_id"
        const val KEY_ENABLED = "sync_enabled"
        const val KEY_INITIAL_DONE = "sync_initial_done"
        const val KEY_PROTO_VERSION = "sync_proto_version"
    }
}
