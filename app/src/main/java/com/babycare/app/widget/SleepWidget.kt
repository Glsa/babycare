package com.babycare.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.babycare.app.BabycareApplication
import com.babycare.app.domain.TimeKit

/**
 * 睡眠计时 4×2（FR-14）：进行中（😴 + hms 实时时长 + 开始时间 + 距上次喂奶 + 结束大按钮）
 * / 未睡（开始大按钮）。开始/结束都在 widget 后台直接完成（与通知按钮同路径）。
 */
class SleepWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = loadSnapshot(context)
        provideContent {
            if (snap.baby == null) {
                NoBabyContent(context)
                return@provideContent
            }
            val startAt = snap.sleepStartAt
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .background(wgPalette.bg)
                    .cornerRadius(24.dp)
                    .padding(16.dp),
            ) {
                if (startAt == null) {
                    // 未在睡：标题 + 副文案 + 开始大按钮
                    Text("💤 睡眠计时", style = TextStyle(color = wgPalette.text3, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.defaultWeight())
                    Text("哄睡了就点开始", style = TextStyle(color = wgPalette.text2, fontSize = 13.sp))
                    Text(TimeKit.sinceFeedText(snap.lastFeedAt, snap.now), style = TextStyle(color = wgPalette.text3, fontSize = 11.sp))
                    Spacer(GlanceModifier.height(10.dp))
                    Box(GlanceModifier.fillMaxWidth().height(46.dp)) {
                        BigButton("开始睡眠 😴", actionRunCallback<SleepToggleAction>())
                    }
                } else {
                    // 进行中：时长大字 + 开始时刻/距上次喂奶 + 结束大按钮
                    Text("😴 睡眠中", style = TextStyle(color = wgPalette.sleep, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        Wg.hmsText(snap.now - startAt),
                        style = TextStyle(color = wgPalette.text1, fontSize = 30.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "${TimeKit.formatHm(startAt)} 开始 · ${TimeKit.sinceFeedText(snap.lastFeedAt, snap.now)}",
                        style = TextStyle(color = wgPalette.text3, fontSize = 11.sp),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(10.dp))
                    Box(GlanceModifier.fillMaxWidth().height(46.dp)) {
                        BigButton("结束", actionRunCallback<SleepToggleAction>())
                    }
                }
            }
        }
    }
}

/** 睡眠开始/结束（widget 后台直做；结束后时长进入当日汇总） */
class SleepToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val timers = (context.applicationContext as BabycareApplication).timerCenter
        if (timers.ongoingSleep() != null) timers.stopSleepNow() else timers.startSleep()
        updateAllWidgets(context)
    }
}
