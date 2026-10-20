package com.babycare.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * 快捷记录 2×2（FR-14）：第 N 天 + 今日奶量摘要 + 四大圆钮。
 * 睡眠钮 = 后台直接开始/结束计时（与睡眠款大按钮同路径）；其余三钮深链开 App 弹层。
 */
class QuickWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = loadSnapshot(context)
        // 距下次喂奶（0011 增量）：null = 还没喂过奶，不占行
        val countdown = Wg.feedCountdownText(snap.lastFeedStart, snap.now, snap.interval, java.time.ZoneId.systemDefault())
        provideContent {
            if (snap.baby == null) {
                NoBabyContent(context)
                return@provideContent
            }
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .background(wgPalette.bg)
                    .cornerRadius(24.dp)
                    .padding(14.dp),
            ) {
                Text(
                    Wg.dayNumberText(snap.dayNumber),
                    style = TextStyle(color = wgPalette.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    Wg.feedSummaryText(snap.summary),
                    style = TextStyle(color = wgPalette.text1, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                if (countdown != null) {
                    Spacer(GlanceModifier.height(1.dp))
                    Text(
                        countdown.first,
                        style = TextStyle(
                            color = if (countdown.second) wgPalette.vaccine else wgPalette.text2,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height(8.dp))
                // 四钮 2×2：占满剩余高度
                Row(
                    GlanceModifier.fillMaxWidth().defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleButton("🍼", "瓶喂", wgPalette.feed, openAppAction(context, "formula"))
                    Spacer(GlanceModifier.width(14.dp))
                    CircleButton("🤱", "亲喂", wgPalette.primary, openAppAction(context, "nursing"))
                }
                Spacer(GlanceModifier.height(6.dp))
                Row(
                    GlanceModifier.fillMaxWidth().defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleButton("💧", "尿布", wgPalette.diaper, openAppAction(context, "diaper"))
                    Spacer(GlanceModifier.width(14.dp))
                    CircleButton(if (snap.sleepStartAt != null) "😴" else "💤", if (snap.sleepStartAt != null) "结束" else "睡眠",
                        wgPalette.sleep, actionRunCallback<SleepToggleAction>())
                }
            }
        }
    }
}
