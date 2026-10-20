package com.babycare.app

import android.app.Application
import android.content.Context
import com.babycare.app.data.db.BabyDatabase
import com.babycare.app.data.model.RecordJson
import com.babycare.app.data.repo.BabyRepository
import com.babycare.app.domain.FeedIntervalSettings
import com.babycare.app.domain.ReminderSettings
import com.babycare.app.remind.ReminderCenter
import com.babycare.app.sync.SyncCenter
import com.babycare.app.timer.TimerCenter
import com.babycare.app.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.SupervisorJob
import java.util.UUID

/** 进程级装配：数据库/仓库单例 + 稳定设备 ID（同步时的 author） */
class BabycareApplication : Application() {

    /** Widget 深链弹层键（MainActivity 收 intent 后置位，TodayPage 消费后清空） */
    val pendingSheet = MutableStateFlow<String?>(null)

    /** 提醒通知点击直达的 Tab 下标（MainActivity 置位，BabycareApp 消费后清空） */
    val pendingTab = MutableStateFlow<Int?>(null)

    /**
     * 深色覆盖开关（我的页）：false = 跟随系统（默认），true = 强制深色。
     * 持久化走 prefs（设置不是业务数据，不进 Room）。
     * 注意 by lazy：属性初始化器在 attachBaseContext 之前跑，那时 Context 还不可用（mBase 为 null），
     * 读 prefs 必须推迟到首次访问。
     */
    val darkOverride: MutableStateFlow<Boolean> by lazy { MutableStateFlow(prefs().getBoolean(KEY_DARK, false)) }

    fun setDarkOverride(on: Boolean) {
        darkOverride.value = on
        prefs().edit().putBoolean(KEY_DARK, on).apply()
    }

    /** 喂奶间隔设置（0011）：prefs 单 key 存 JSON，坏值回落默认；同样必须 lazy（见上） */
    val feedInterval: MutableStateFlow<FeedIntervalSettings> by lazy {
        MutableStateFlow(decodeFeedInterval(prefs().getString(KEY_FEED_INTERVAL, null)))
    }

    fun setFeedInterval(s: FeedIntervalSettings) {
        feedInterval.value = s
        prefs().edit()
            .putString(KEY_FEED_INTERVAL, RecordJson.encodeToString(FeedIntervalSettings.serializer(), s))
            .apply()
    }

    private fun decodeFeedInterval(json: String?): FeedIntervalSettings =
        json?.let { runCatching { RecordJson.decodeFromString(FeedIntervalSettings.serializer(), it) }.getOrNull() }
            ?: FeedIntervalSettings()

    /** 提醒开关（0014）：同喂奶间隔模式，prefs 单 key JSON 坏值回落默认 */
    val reminderSettings: MutableStateFlow<ReminderSettings> by lazy {
        MutableStateFlow(
            prefs().getString(KEY_REMINDERS, null)
                ?.let { runCatching { RecordJson.decodeFromString(ReminderSettings.serializer(), it) }.getOrNull() }
                ?: ReminderSettings(),
        )
    }

    fun setReminderSettings(s: ReminderSettings) {
        reminderSettings.value = s
        prefs().edit().putString(KEY_REMINDERS, RecordJson.encodeToString(ReminderSettings.serializer(), s)).apply()
    }

    private fun prefs() = getSharedPreferences("babycare", Context.MODE_PRIVATE)

    /** 进程级作用域：Widget 刷新等与 UI 生命周期无关的常驻任务 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: BabyDatabase by lazy { BabyDatabase.build(this) }

    /** 稳定设备 ID：记录的 author（0016 起也是「记录人角标」的本机判定依据） */
    val deviceId: String by lazy { loadOrCreateDeviceId() }

    val repository: BabyRepository by lazy {
        BabyRepository(database.recordDao(), database.metaDao(), author = deviceId)
    }

    /** 计时控制中心（睡眠/亲喂计时状态变更唯一入口，见 timer/ 包） */
    val timerCenter: TimerCenter by lazy { TimerCenter(this, repository) }

    /** 提醒调度中心（0014 · FR-13，单闹钟链 + 通知发送） */
    val reminderCenter: ReminderCenter by lazy { ReminderCenter(this, repository) }

    /** 云同步中枢（0016 · FR-20：每写即推 + 游标增量拉取） */
    val syncCenter: SyncCenter by lazy { SyncCenter(this, repository, database) }

    override fun onCreate() {
        super.onCreate()
        // Widget 三款刷新（changes 流 + 兜底闹钟），随进程常驻
        WidgetRefresher(this, repository).start(appScope)
        // 提醒调度（FR-13），随进程常驻
        reminderCenter.start(appScope)
        // 云同步（FR-20），随进程常驻；未绑定时 kick 全部空转
        syncCenter.start(appScope)
    }

    private fun loadOrCreateDeviceId(): String {
        val prefs = getSharedPreferences("babycare", Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DARK = "theme_dark_override"
        const val KEY_FEED_INTERVAL = "feed_interval"
        const val KEY_REMINDERS = "reminder_settings"
    }
}
