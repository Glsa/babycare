package com.babycare.app.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.babycare.app.BabycareApplication
import com.babycare.app.MainActivity
import com.babycare.app.R
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.NursingTimerState
import com.babycare.app.data.model.SleepPayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.domain.TimeKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * 计时前台服务：自身不持有状态（状态在 Room/meta），只负责常驻通知与通知按钮动作分发。
 * START_STICKY：进程被杀后系统重建（intent=null → 当 REFRESH 处理）→ 读状态重建通知，
 * 即 PRD「前台服务 + 开始时间戳恢复」。
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val zone: ZoneId by lazy { ZoneId.systemDefault() }
    private lateinit var center: TimerCenter

    override fun onCreate() {
        super.onCreate()
        center = (application as BabycareApplication).timerCenter
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "计时", NotificationManager.IMPORTANCE_LOW).apply {
                description = "亲喂/睡眠进行中的常驻通知"
                setShowBadge(false)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_REFRESH
        scope.launch {
            when (action) {
                ACTION_START_SLEEP -> center.startSleep()
                ACTION_STOP_SLEEP -> center.stopSleepNow()
                ACTION_CANCEL_SLEEP -> center.cancelSleep()
                ACTION_NURSING_LEFT -> center.startNursing(NursingTimerState.SIDE_LEFT)
                ACTION_NURSING_RIGHT -> center.startNursing(NursingTimerState.SIDE_RIGHT)
                ACTION_SWITCH_SIDE -> center.switchNursingSide()
                ACTION_TOGGLE_PAUSE -> center.toggleNursingPause()
                ACTION_STOP_NURSING -> center.stopNursing()
                ACTION_CANCEL_NURSING -> center.cancelNursing()
            }
            syncNotification()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** 有计时 → 常驻通知；无 → 先补一个空前台满足 startForegroundService 契约再自灭 */
    private suspend fun syncNotification() {
        val sleep = center.ongoingSleep()
        val nursing = center.nursingTimer()
        if (sleep == null && nursing == null) {
            startForeground(NOTIF_ID, baseBuilder().build())
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        startForeground(NOTIF_ID, buildNotification(sleep, nursing))
    }

    private fun buildNotification(sleep: RecordEntity?, nursing: NursingTimerState?): Notification {
        val sleepStart = sleep?.let { (decodePayloadOrNull(it.type, it.payload) as? SleepPayload)?.startAt }
        val lines = buildList {
            sleepStart?.let { add("😴 睡眠 · ${TimeKit.formatHm(it, zone)} 开始") }
            nursing?.let {
                val side = if (it.activeSide == NursingTimerState.SIDE_LEFT) "左侧" else "右侧"
                val paused = if (it.pausedAt != null) "（暂停中）" else ""
                add("🤱 亲喂 · $side$paused")
            }
        }
        val earliest = minOf(
            sleepStart ?: Long.MAX_VALUE,
            nursing?.startAt ?: Long.MAX_VALUE,
        )
        val builder = baseBuilder()
            .setContentTitle("计时中")
            .setContentText(lines.joinToString(" ｜ "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setWhen(earliest)              // 配合 chronometer，系统自动走秒
            .setUsesChronometer(true)
            .setContentIntent(activityPendingIntent())
        // 动作上限 3 个：双计时并存时 亲喂换边/结束亲喂/结束睡眠
        nursing?.let {
            builder.addAction(0, "换边", servicePendingIntent(ACTION_SWITCH_SIDE, 1))
            builder.addAction(0, "结束亲喂", servicePendingIntent(ACTION_STOP_NURSING, 2))
        }
        sleep?.let { builder.addAction(0, "结束睡眠", servicePendingIntent(ACTION_STOP_SLEEP, 3)) }
        return builder.build()
    }

    private fun baseBuilder() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_timer)
        .setOnlyAlertOnce(true)
        .setOngoing(true)

    private fun activityPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode, Intent(this, TimerService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_REFRESH = "com.babycare.app.timer.REFRESH"
        const val ACTION_START_SLEEP = "com.babycare.app.timer.START_SLEEP"
        const val ACTION_STOP_SLEEP = "com.babycare.app.timer.STOP_SLEEP"
        const val ACTION_CANCEL_SLEEP = "com.babycare.app.timer.CANCEL_SLEEP"
        const val ACTION_NURSING_LEFT = "com.babycare.app.timer.NURSING_LEFT"
        const val ACTION_NURSING_RIGHT = "com.babycare.app.timer.NURSING_RIGHT"
        const val ACTION_SWITCH_SIDE = "com.babycare.app.timer.SWITCH_SIDE"
        const val ACTION_TOGGLE_PAUSE = "com.babycare.app.timer.TOGGLE_PAUSE"
        const val ACTION_STOP_NURSING = "com.babycare.app.timer.STOP_NURSING"
        const val ACTION_CANCEL_NURSING = "com.babycare.app.timer.CANCEL_NURSING"

        private const val CHANNEL_ID = "timers"
        private const val NOTIF_ID = 1001
    }
}
