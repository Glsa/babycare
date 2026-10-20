package com.babycare.app.widget

import com.babycare.app.domain.DaySummary
import com.babycare.app.domain.FeedIntervalKit
import com.babycare.app.domain.FeedIntervalSettings
import com.babycare.app.domain.TimeKit
import java.time.ZoneId

/** Widget 文案纯函数（JVM 可测）；数据都来自 DaySummary/快照 */
object Wg {
    const val NO_BABY_HINT = "先在 App 里给宝宝建档"

    /** 进行中睡眠时长："1:23:45"（时:分:秒，分钟级刷新下秒位仅装饰） */
    fun hmsText(millis: Long): String {
        val total = (millis.coerceAtLeast(0)) / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }

    fun dayNumberText(n: Int): String = "第 $n 天"

    /**
     * 距下次喂奶（0011 增量·Widget 版）：null = 无喂奶记录不显示；
     * Pair(text, overdue)，超时由 UI 红色（vaccine token）。与 App badge 同源 FeedIntervalKit。
     */
    fun feedCountdownText(
        lastFeedStart: Long?,
        now: Long,
        s: FeedIntervalSettings,
        zone: ZoneId,
    ): Pair<String, Boolean>? {
        if (lastFeedStart == null) return null
        val remain = FeedIntervalKit.remainingMillis(lastFeedStart, now, s, zone)
        return if (remain <= 0) "该喂奶了 · 超 ${FeedIntervalKit.hmText(-remain)}" to true
        else "距下次 ${FeedIntervalKit.hmText(remain)}" to false
    }

    /** 快捷记录 2×2 顶部摘要（奶粉口径）："780 ml · 8 次" / "还没喝奶" */
    fun feedSummaryText(s: DaySummary): String =
        if (s.formulaCount == 0) "还没喝奶" else "${s.formulaMl} ml · ${s.formulaCount} 次"

    /** 概览奶量行："780ml · 奶8 · 亲2"（无亲喂省略亲喂段；全空 → "还没喝奶"） */
    fun overviewFeedText(s: DaySummary): String = when {
        s.formulaCount == 0 && s.nursingCount == 0 -> "还没喝奶"
        s.nursingCount == 0 -> "${s.formulaMl}ml · 奶${s.formulaCount}"
        s.formulaCount == 0 -> "亲喂 ${s.nursingCount} 次"
        else -> "${s.formulaMl}ml · 奶${s.formulaCount} · 亲${s.nursingCount}"
    }

    /** 概览睡眠行："14.2 h · 睡着中" / "14.2 h" / "还没睡"（小时数与 App 概览同源 TimeKit） */
    fun overviewSleepText(s: DaySummary): String = when {
        s.sleepMillis <= 0 && !s.sleepOngoing -> "还没睡"
        s.sleepOngoing -> "${TimeKit.hoursText(s.sleepMillis)} h · 睡着中"
        else -> "${TimeKit.hoursText(s.sleepMillis)} h"
    }

    /** 概览尿便行："尿 6 · 便 2" */
    fun diaperText(s: DaySummary): String = "尿 ${s.peeCount} · 便 ${s.poopCount}"
}
