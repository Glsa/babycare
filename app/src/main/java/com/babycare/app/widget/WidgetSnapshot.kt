package com.babycare.app.widget

import android.content.Context
import com.babycare.app.BabycareApplication
import com.babycare.app.data.model.SleepPayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.domain.AgeCalc
import com.babycare.app.domain.BabyInfo
import com.babycare.app.domain.DaySummary
import com.babycare.app.domain.FeedIntervalSettings
import com.babycare.app.domain.TimeKit
import com.babycare.app.domain.computeDaySummary
import com.babycare.app.domain.toBabyInfo
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.flow.first

/**
 * 三款 Widget 共用的一次性快照（provideGlance 里取数）。
 * baby=null 表示未建档，界面统一走占位态。
 */
data class WidgetSnapshot(
    val baby: BabyInfo? = null,
    val dayNumber: Int = 0,
    val summary: DaySummary = DaySummary(),
    /** 进行中睡眠的开始时刻；null = 没在睡 */
    val sleepStartAt: Long? = null,
    val lastFeedAt: Long? = null,
    /** 亲喂计时中的开始时刻（倒计时起点取 max(它, lastFeedAt)，与今天页 badge 同口径） */
    val nursingStartAt: Long? = null,
    /** 喂奶间隔设置（0011：Widget 倒计时与 App badge 同源） */
    val interval: FeedIntervalSettings = FeedIntervalSettings(),
    val now: Long = 0L,
) {
    /** 距下次喂奶的起点：计时中亲喂优先于上一条已落库记录 */
    val lastFeedStart: Long?
        get() = listOfNotNull(lastFeedAt, nursingStartAt).maxOrNull()
}

suspend fun loadSnapshot(context: Context): WidgetSnapshot {
    val app = context.applicationContext as BabycareApplication
    val repo = app.repository
    val zone = zone
    val now = System.currentTimeMillis()
    val today = TimeKit.toLocalDate(now, zone)
    val baby = repo.observeBaby().first().firstOrNull()?.toBabyInfo()
        ?: return WidgetSnapshot(now = now)
    val records = repo.dayOnce(baby.uuid, TimeKit.dayStart(today, zone), TimeKit.dayEndExclusive(today, zone))
    val sleepStartAt = repo.ongoingSleepOnce()
        ?.let { decodePayloadOrNull(it.type, it.payload) as? SleepPayload }
        ?.startAt
    val lastFeedAt = repo.observeLastFeed(baby.uuid).first()?.occurredAt
    val nursingStartAt = repo.observeNursingTimer().first()?.startAt
    return WidgetSnapshot(
        baby = baby,
        dayNumber = AgeCalc.dayNumber(today, baby.birthday),
        summary = computeDaySummary(records, now),
        sleepStartAt = sleepStartAt,
        lastFeedAt = lastFeedAt,
        nursingStartAt = nursingStartAt,
        interval = app.feedInterval.value,
        now = now,
    )
}

/** 三款一起刷（refresher / 动作回调用）。按 receiver 类型找当前实例，逐个 update。 */
suspend fun updateAllWidgets(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    manager.getGlanceIds(QuickWidget::class.java).forEach { QuickWidget().update(context, it) }
    manager.getGlanceIds(OverviewWidget::class.java).forEach { OverviewWidget().update(context, it) }
    manager.getGlanceIds(SleepWidget::class.java).forEach { SleepWidget().update(context, it) }
}

private val zone = java.time.ZoneId.systemDefault()
