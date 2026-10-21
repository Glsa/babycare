package com.babycare.app.sync

import android.app.Application
import android.content.Context
import com.babycare.app.data.db.BabyDatabase
import com.babycare.app.data.model.MetaKeys
import com.babycare.app.data.model.RecordJson
import com.babycare.app.data.repo.BabyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.atomic.AtomicInteger

/** 运行时状态（配置态见 [SyncConfig]）；lastOkAt/lastError 供我的页与同步页展示 */
data class SyncUiState(
    val running: Boolean = false,
    val lastOkAt: Long? = null,
    val lastError: String? = null,
    /** 401 后停自动重试，重绑/解绑才清 */
    val authExpired: Boolean = false,
    /** 待上行条数（每轮结束刷新） */
    val pendingCount: Int = 0,
)

/**
 * 同步中枢（0016 · FR-20）：装配引擎 + 触发调度 + 配对动作 + 状态流。
 * 触发源（D-1/D-2）全部汇入 kick()，单 worker 消费——「每次写即推」不延迟不攒批，
 * 但连续写天然合流成整轮（CONFLATED 通道：跑着时新信号并成一个，跑完立即再跑）。
 * 失败指数退避（D-4 静默重试）；401 停退避待重绑（K10）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncCenter(
    private val app: Application,
    private val repo: BabyRepository,
    db: BabyDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {

    val prefs = SyncPrefs(app.getSharedPreferences("babycare", Context.MODE_PRIVATE))
    val config: StateFlow<SyncConfig> = prefs.config

    private val store: SyncStore = RoomStore(db)
    private val transport: SyncTransport = OkHttpSyncTransport(
        baseUrl = { config.value.baseUrl },
        token = { config.value.token },
    )
    private val engine = SyncEngine(transport, store)

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state

    private val kicks = Channel<Unit>(Channel.CONFLATED)
    private var backoffIndex = 0
    private var nextAllowedAt = 0L

    /** 前台感知（K6：ActivityLifecycleCallbacks，零新依赖） */
    private val startedActivities = AtomicInteger(0)
    private val inForeground = MutableStateFlow(false)

    /** 同步处于激活态（绑定且开关开）：清空数据据此分叉（K8 墓碑 vs 物理删） */
    val syncActive: Boolean get() = config.value.bound && config.value.enabled

    /** 随 Application 常驻启动（照 ReminderCenter 模式） */
    fun start(scope: CoroutineScope) {
        // D-1 每次写即推：仓库是全部写路径唯一入口，changes 信号即触发
        scope.launch { repo.changes.collect { kick() } }
        // D-2 回前台立即拉 + 前台每 60s 周期拉，退后台停
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                if (startedActivities.incrementAndGet() == 1) inForeground.value = true
            }
            override fun onActivityStopped(activity: android.app.Activity) {
                if (startedActivities.decrementAndGet() == 0) inForeground.value = false
            }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityResumed(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
        scope.launch {
            inForeground.flatMapLatest { fg ->
                if (fg) flow {
                    emit(Unit)
                    while (true) {
                        delay(PULL_PERIOD_MS)
                        emit(Unit)
                    }
                } else emptyFlow()
            }.collect { kick() }
        }
        // 绑定/开关打开立即跑一轮（首绑全量上行由此触发）
        scope.launch {
            config.map { it.bound && it.enabled }.collect { active -> if (active) kick() }
        }
        scope.launch { worker() }
        scope.launch { refreshPendingCount() }
        // K12 升级自愈：协议版本落后 → 游标归零，下轮全量重拉（LWW 幂等，只补缺失不覆盖）
        if (prefs.protoVersion() < PROTO_VERSION) {
            scope.launch {
                store.resetCursor()
                prefs.setProtoVersion(PROTO_VERSION)
                kick()
            }
        }
    }

    fun kick() {
        kicks.trySend(Unit)
    }

    // ---- worker：单循环消费 kick，失败退避 ----

    private suspend fun worker() {
        for (ignore in kicks) {
            val cfg = config.value
            if (!cfg.configured || !cfg.bound || !cfg.enabled || _state.value.authExpired) continue
            val wait = nextAllowedAt - now()
            if (wait > 0) delay(wait)
            runRound()
        }
    }

    private suspend fun runRound() {
        _state.value = _state.value.copy(running = true)
        try {
            // K12：首轮先拉后推（见 SyncEngine.round 注释）；首轮失败重试仍是先拉，幂等无害
            engine.round(pullFirst = !config.value.initialSyncDone)
            backoffIndex = 0
            nextAllowedAt = 0L
            if (!config.value.initialSyncDone) prefs.markInitialDone()
            _state.value = _state.value.copy(
                running = false, lastOkAt = now(), lastError = null, pendingCount = store.countDirty(),
            )
        } catch (e: SyncException) {
            if (e.kind == SyncException.Kind.AUTH) {
                // K10：令牌不会自愈，停自动重试，提示重绑
                _state.value = _state.value.copy(running = false, authExpired = true, lastError = e.userText())
            } else {
                enterBackoff(e.userText())
            }
        } catch (e: Exception) {
            enterBackoff("同步出错：${e.message ?: "未知错误"}")
        } finally {
            // K12.1：收敛不依赖本轮成功——push 一直失败时 pull 的数据已落库，
            // 档案必须先并掉（否则界面持续跟空档案），收敛写等下一轮成功再推
            runCatching { converge() }
        }
    }

    /**
     * 轮后对账（K11）：先换挂再打墓碑（输家名下记录改挂赢家，否则记录跟着死档案隐身）；
     * 全部走 repo 写路径 → 发 changes → 自动触发下一轮推送，对端拉到后各自收敛。
     * 规则纯函数在 [SyncConverge]，两端算出同一赢家，不会各删各的。
     */
    private suspend fun converge() {
        val plan = SyncConverge.plan(repo.allOnce())
        if (!plan.hasWork) return
        plan.babyLosers.forEach { repo.reassignBaby(it.uuid, plan.babyWinner!!.uuid) }
        (plan.babyLosers + plan.taskLosers).forEach { repo.softDelete(it) }
    }

    private suspend fun enterBackoff(message: String) {
        val delayMs = BACKOFF_LADDER[backoffIndex.coerceAtMost(BACKOFF_LADDER.lastIndex)]
        backoffIndex++
        nextAllowedAt = now() + delayMs
        _state.value = _state.value.copy(running = false, lastError = message, pendingCount = store.countDirty())
    }

    private suspend fun refreshPendingCount() {
        _state.value = _state.value.copy(pendingCount = store.countDirty())
    }

    // ---- 配对与管理动作（同步页调用；返回 null = 成功，非 null = 人话错误）----

    /** 探活并保存服务器地址 */
    suspend fun saveServerUrl(url: String): String? {
        val normalized = url.trim().trimEnd('/')
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            return "地址需以 http:// 或 https:// 开头"
        }
        return try {
            transport.ping(normalized)
            prefs.setBaseUrl(normalized)
            null
        } catch (e: SyncException) {
            "连不上：${e.userText()}"
        } catch (e: Exception) {
            "连不上：${e.message ?: "未知错误"}"
        }
    }

    suspend fun createFamily(familyName: String, deviceName: String): String? = bind {
        transport.createFamily(config.value.baseUrl, familyName, deviceName)
    }

    suspend fun joinByCode(pairingCode: String, deviceName: String): String? = bind {
        transport.pair(config.value.baseUrl, pairingCode, deviceName)
    }

    private suspend fun bind(call: suspend () -> BindResponse): String? = try {
        val resp = call()
        prefs.bind(resp, deviceNameIfBlank())
        store.resetCursor() // K12：游标归零 + 首轮先拉后推，保证拉全家庭历史（含换家庭重绑）
        store.markAllDirty() // K2：存量记录状态未知视为待推 → 首轮全量上行
        _state.value = _state.value.copy(authExpired = false, lastError = null)
        refreshDeviceNames()
        kick()
        null
    } catch (e: SyncException) {
        pairErrorText(e)
    } catch (e: Exception) {
        "出错了：${e.message ?: "未知错误"}"
    }

    private fun deviceNameIfBlank(): String =
        config.value.deviceName.ifBlank { android.os.Build.MODEL ?: "我的设备" }

    private fun pairErrorText(e: SyncException): String = when (e.code) {
        "pairing_code_expired" -> "配对码已过期，请对方刷新后重试"
        else -> e.userText()
    }

    /** 刷新配对码（绑定态），失败抛 */
    suspend fun refreshPairingCode(): PairingInfo = transport.refreshPairingCode()

    /** 设备列表（同时更新 DEVICE_NAMES 映射供角标用），失败抛 */
    suspend fun deviceList(): List<DeviceInfo> = transport.devices().also { saveDeviceNames(it) }

    private suspend fun refreshDeviceNames() {
        runCatching { deviceList() } // 绑定后马上拉一次列表，失败不阻断（下次打开同步页会再拉）
    }

    private suspend fun saveDeviceNames(devices: List<DeviceInfo>) {
        val map = devices.associate { it.deviceId to it.name }
        repo.metaPut(MetaKeys.DEVICE_NAMES, RecordJson.encodeToString(deviceNamesSerializer, map))
    }

    /** 设备名映射流（记录人角标消费；未绑定为空 Map） */
    fun deviceNamesFlow() = repo.observeMeta(MetaKeys.DEVICE_NAMES)
        .map { json ->
            json?.let { runCatching { RecordJson.decodeFromString(deviceNamesSerializer, it) }.getOrNull() } ?: emptyMap()
        }

    fun setEnabled(on: Boolean) {
        prefs.setEnabled(on)
        if (on) kick()
    }

    /** 解绑回纯本地：数据/游标/脏标记保留，重绑同家庭可续传；再绑新家庭走全量 */
    fun unbind() {
        prefs.unbind()
        _state.value = _state.value.copy(authExpired = false, lastError = null)
    }

    /** 播种门闩（K9）：已绑定未完成首轮时挂起等首轮（解绑/关闭也放行，后续靠 hasAny 兜底） */
    suspend fun awaitInitialSyncIfBound() {
        val c = config.value
        if (!c.bound || !c.enabled || c.initialSyncDone) return
        config.first { !it.bound || !it.enabled || it.initialSyncDone }
    }

    private companion object {
        const val PULL_PERIOD_MS = 60_000L
        /** K12：先拉后推 + 绑定重置游标落地时的协议版本；下个破坏同步语义的修复再 +1 */
        const val PROTO_VERSION = 1
        /** 5s → 30s → 2min → 5min 封顶（D-4 静默重试） */
        val BACKOFF_LADDER = longArrayOf(5_000, 30_000, 120_000, 300_000)
        val deviceNamesSerializer = MapSerializer(String.serializer(), String.serializer())
    }
}
