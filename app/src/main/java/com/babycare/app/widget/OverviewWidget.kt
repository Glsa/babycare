package com.babycare.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
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
import androidx.glance.unit.ColorProvider

/**
 * 今日概览 4×2（FR-14；尺寸按 RequireSpec Q1 拍板取横放）：左列当日汇总，
 * 右列 6 快捷键（+洗澡、生长）。睡眠键后台直做，洗澡键开 App 即记。
 */
class OverviewWidget : GlanceAppWidget() {

    private data class Key(val emoji: String, val label: String, val color: ColorProvider, val action: Action)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = loadSnapshot(context)
        // 距下次喂奶（0011 增量）：null = 还没喂过奶，不占行
        val countdown = Wg.feedCountdownText(snap.lastFeedStart, snap.now, snap.interval, java.time.ZoneId.systemDefault())
        provideContent {
            if (snap.baby == null) {
                NoBabyContent(context)
                return@provideContent
            }
            Row(
                GlanceModifier
                    .fillMaxSize()
                    .background(wgPalette.bg)
                    .cornerRadius(24.dp)
                    .padding(14.dp),
            ) {
                // 左列：当日汇总
                Column(GlanceModifier.defaultWeight().fillMaxSize().padding(end = 12.dp)) {
                    Text(
                        Wg.dayNumberText(snap.dayNumber),
                        style = TextStyle(color = wgPalette.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    SummaryRow("🍼", Wg.overviewFeedText(snap.summary))
                    if (countdown != null) {
                        Spacer(GlanceModifier.height(6.dp))
                        SummaryRow("⏱", countdown.first, tint = if (countdown.second) wgPalette.vaccine else wgPalette.text1)
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    SummaryRow("😴", Wg.overviewSleepText(snap.summary))
                    Spacer(GlanceModifier.height(6.dp))
                    SummaryRow("💧", Wg.diaperText(snap.summary))
                }
                Spacer(GlanceModifier.width(10.dp))
                // 右列：6 快捷键 2×3（行 modifier 在 Column 作用域里拼好传入，行内才能均分高度）
                Column(GlanceModifier.defaultWeight().fillMaxSize()) {
                    val rowMod = GlanceModifier.fillMaxWidth().defaultWeight()
                    KeyRow(
                        rowMod,
                        Key("🍼", "瓶喂", wgPalette.feed, openAppAction(context, "formula")),
                        Key("🤱", "亲喂", wgPalette.primary, openAppAction(context, "nursing")),
                    )
                    KeyRow(
                        rowMod,
                        Key("💧", "尿布", wgPalette.diaper, openAppAction(context, "diaper")),
                        Key(
                            if (snap.sleepStartAt != null) "😴" else "💤",
                            if (snap.sleepStartAt != null) "结束" else "睡眠",
                            wgPalette.sleep, actionRunCallback<SleepToggleAction>(),
                        ),
                    )
                    KeyRow(
                        rowMod,
                        Key("🛁", "洗澡", wgPalette.bath, openAppAction(context, "bath")),
                        Key("📏", "生长", wgPalette.growth, openAppAction(context, "growth")),
                    )
                }
            }
        }
    }

    @Composable
    private fun SummaryRow(emoji: String, text: String, tint: ColorProvider = wgPalette.text1) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = TextStyle(fontSize = 14.sp))
            Spacer(GlanceModifier.width(6.dp))
            Text(text, style = TextStyle(color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }

    @Composable
    private fun KeyRow(modifier: GlanceModifier, a: Key, b: Key) {
        Row(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(a.emoji, a.label, a.color, a.action, size = 34.dp)
            Spacer(GlanceModifier.width(18.dp))
            CircleButton(b.emoji, b.label, b.color, b.action, size = 34.dp)
        }
    }
}
