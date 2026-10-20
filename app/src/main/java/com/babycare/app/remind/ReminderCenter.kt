package com.babycare.app.remind

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.babycare.app.BabycareApplication
import com.babycare.app.MainActivity
import com.babycare.app.R
import com.babycare.app.data.repo.BabyRepository
import com.babycare.app.domain.ReminderPlanner
import com.babycare.app.domain.ReminderSettings
import com.babycare.app.domain.ReminderSpec
import com.babycare.app.domain.toBabyInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * 提醒调度中心（0014 · FR-13）：单闹钟链——任何时刻只挂「最近一条」的闹钟，
 * 到点把到期提醒发掉、再排下一个。无闹钟可排时取消闹钟。
 *
 * 重排时机：App 启动、repo.changes、提醒开关变更、闹钟触发后、
 * Widget 兜底 tick（跨天/进程被杀校准）、开机（ReminderReceiver/boot）。
 */
class ReminderCenter(private val context: Context, private val repo: BabyRepository) {

    private val app get() = context.applicationContext as BabycareApplication
    private val zone = ZoneId.systemDefault()

    fun start(scope: CoroutineScope) {
        scope.launch { reschedule() }
        // 记录/计时变化（含每次喂奶 → 喂奶提醒起点更新）
        scope.launch { repo.changes.collect { reschedule() } }
        // 开关变化（含总开关关闭 → 取消闹钟）
        scope.launch {
            app.reminderSettings.collect { reschedule() }
        }
    }

    /** 立即异步重排（receiver 里用，goAsync 作用域内等待完成） */
    suspend fun reschedule() {
        runCatching {
            val now = System.currentTimeMillis()
            val settings = app.reminderSettings.value
            val specs = if (!settings.master) emptyList() else planAll(now)
            // 到期的先发掉（含喂奶超时这条「现在就该发」的）
            val due = ReminderPlanner.due(specs, now)
            val feedKeys = due.filter { it.dedupeKey.startsWith(KEY_FEED_PREFIX) }.map { it.dedupeKey }
            due.forEach { notifyOne(it) }
            // 喂奶提醒的旧通知：换起点后取消上一条，避免两条「该喂奶了」叠着
            cancelStaleFeedNotification(feedKeys)
            // 再挂最近一条未来的
            val am = context.getSystemService(AlarmManager::class.java)
            val next = specs.firstOrNull { it.at > now + 60_000 }
            if (am != null && next != null) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, alarmPendingIntent())
            } else if (am != null) {
                am.cancel(alarmPendingIntent())
            }
        }
    }

    /** 汇总输入 → 纯函数规划 */
    private suspend fun planAll(now: Long): List<ReminderSpec> {
        val baby = repo.observeBaby().first().firstOrNull()?.toBabyInfo() ?: return emptyList()
        val lastFeedStart = listOfNotNull(
            repo.observeLastFeed(baby.uuid).first()?.occurredAt,
            repo.observeNursingTimer().first()?.startAt,
        ).maxOrNull()
        return ReminderPlanner.plan(
            com.babycare.app.domain.ReminderInput(
                birthday = baby.birthday,
                vaccineRecords = repo.observeByType(com.babycare.app.data.model.RecordTypes.VACCINE_DOSE).first(),
                supplementPlans = repo.observeByType(com.babycare.app.data.model.RecordTypes.SUPPLEMENT_PLAN).first(),
                agendas = repo.observeByType(com.babycare.app.data.model.RecordTypes.AGENDA).first(),
                feedInterval = app.feedInterval.value,
                lastFeedStart = lastFeedStart,
            ),
            app.reminderSettings.value,
            now,
            zone,
        )
    }

    // ---- 通知 ----

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "提醒", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    private fun notifyOne(spec: ReminderSpec) {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel()
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val pi = PendingIntent.getActivity(
            context,
            spec.tab,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_TAB, spec.tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(spec.dedupeKey.hashCode(), n)
    }

    /** 上一次喂奶提醒的通知 ID：起点变了（新喂奶）就取消旧条 */
    private fun cancelStaleFeedNotification(currentKeys: List<String>) {
        val current = currentKeys.firstOrNull() ?: return
        val prefs = context.getSharedPreferences("babycare", Context.MODE_PRIVATE)
        val prev = prefs.getString(KEY_LAST_FEED_NOTIF, null)
        if (prev != null && prev != current) {
            context.getSystemService(NotificationManager::class.java)?.cancel(prev.hashCode())
        }
        if (prev != current) prefs.edit().putString(KEY_LAST_FEED_NOTIF, current).apply()
    }

    private fun alarmPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQ_CODE,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val EXTRA_TAB = "tab"
        private const val CHANNEL_ID = "reminders"
        private const val REQ_CODE = 4_211
        private const val KEY_FEED_PREFIX = "feed:"
        private const val KEY_LAST_FEED_NOTIF = "last_feed_notif_key"
    }
}
