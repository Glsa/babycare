package com.babycare.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.babycare.app.data.repo.BabyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Widget 刷新器（唯一刷新入口，Application.onCreate 挂上）：
 * - repo.changes → 立即刷三款（App 进程内任何记录/计时变更，含 TimerCenter 全走 repo）
 * - AlarmManager 兜底三档：睡眠进行中 60 秒（时长跳动）/ 喂奶倒计时在显 5 分钟
 *   （0011 增量：喂过奶就有倒计时行，分钟级文字不能一停半小时）/ 其余 30 分钟
 *   （跨天摘要重置、进程被杀后校准；不精确闹钟，Doze 下允许拖后）
 */
class WidgetRefresher(private val context: Context, private val repo: BabyRepository) {

    fun start(scope: CoroutineScope) {
        scope.launch { refreshOnce() }   // 冷启动先排上兜底闹钟 + 补刷一次
        scope.launch {
            repo.changes.collect {
                refreshOnce()
            }
        }
    }

    private suspend fun refreshOnce() {
        runCatching { updateAllWidgets(context) }
        rescheduleAlarm()
    }

    private suspend fun rescheduleAlarm() {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmPendingIntent()
        val interval = alarmIntervalMs()
        am.cancel(pi)
        am.setRepeating(AlarmManager.RTC, System.currentTimeMillis() + interval, interval, pi)
    }

    /** 三档周期：睡眠 > 倒计时 > 空闲（未建档/还没喂过奶） */
    private suspend fun alarmIntervalMs(): Long {
        if (repo.ongoingSleepOnce() != null) return INTERVAL_SLEEPING_MS
        val baby = repo.observeBaby().first().firstOrNull() ?: return INTERVAL_IDLE_MS
        return if (repo.observeLastFeed(baby.uuid).first() != null) INTERVAL_COUNTDOWN_MS else INTERVAL_IDLE_MS
    }

    private fun alarmPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQ_CODE,
        Intent(context, WidgetAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQ_CODE = 4_209
        const val INTERVAL_SLEEPING_MS = 60_000L
        const val INTERVAL_COUNTDOWN_MS = 5 * 60_000L
        const val INTERVAL_IDLE_MS = 30 * 60_000L
    }
}
