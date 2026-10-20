package com.babycare.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.babycare.app.MainActivity
import com.babycare.app.ui.theme.DarkBabyColors
import com.babycare.app.ui.theme.LightBabyColors

/**
 * Widget 色板：亮暗两套直接映射 App 的 Color tokens（`ColorProvider(day, night)` 跟随系统
 * 深色模式，与 App 内 `isSystemInDarkTheme` 同口径）。Glance 不吃 Compose 主题，手动搬一层。
 */
data class WgPalette(
    val bg: ColorProvider,
    val surface: ColorProvider,
    val surfaceAlt: ColorProvider,
    val text1: ColorProvider,
    val text2: ColorProvider,
    val text3: ColorProvider,
    val primary: ColorProvider,
    val primarySoft: ColorProvider,
    val onPrimary: ColorProvider,
    val feed: ColorProvider,
    val diaper: ColorProvider,
    val sleep: ColorProvider,
    val bath: ColorProvider,
    val growth: ColorProvider,
    /** 红色系（vaccine token）：倒计时超时等警示文案 */
    val vaccine: ColorProvider,
)

/** 亮暗各一色 → 跟随系统的 ColorProvider */
private fun dn(day: Color, night: Color): ColorProvider =
    androidx.glance.color.ColorProvider(day, night)

/** 主强调色上的文字：与 App Theme 同判断（onPrimary = 白） */
private val onPrimaryColor = Color(0xFFFFFFFF)

val wgPalette = WgPalette(
    bg = dn(LightBabyColors.bg, DarkBabyColors.bg),
    surface = dn(LightBabyColors.surface, DarkBabyColors.surface),
    surfaceAlt = dn(LightBabyColors.surfaceAlt, DarkBabyColors.surfaceAlt),
    text1 = dn(LightBabyColors.text1, DarkBabyColors.text1),
    text2 = dn(LightBabyColors.text2, DarkBabyColors.text2),
    text3 = dn(LightBabyColors.text3, DarkBabyColors.text3),
    primary = dn(LightBabyColors.primary, DarkBabyColors.primary),
    primarySoft = dn(LightBabyColors.primarySoft, DarkBabyColors.primarySoft),
    onPrimary = dn(onPrimaryColor, onPrimaryColor),
    feed = dn(LightBabyColors.feed, DarkBabyColors.feed),
    diaper = dn(LightBabyColors.diaper, DarkBabyColors.diaper),
    sleep = dn(LightBabyColors.sleep, DarkBabyColors.sleep),
    bath = dn(LightBabyColors.bath, DarkBabyColors.bath),
    growth = dn(LightBabyColors.growth, DarkBabyColors.growth),
    vaccine = dn(LightBabyColors.vaccine, DarkBabyColors.vaccine),
)

/** 深链开 App，可带直达弹层键（见 RecordSheet.fromWidgetKey；bath = 即记不弹层） */
internal fun openAppAction(context: Context, sheet: String? = null): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java).putExtra(EXTRA_SHEET, sheet)
    )

const val EXTRA_SHEET = "sheet"

/** 未建档占位（整卡点击开 App），三款共用 */
@Composable
internal fun NoBabyContent(context: Context) {
    Box(
        GlanceModifier
            .fillMaxSize()
            .background(wgPalette.bg)
            .cornerRadius(24.dp)
            .clickable(openAppAction(context)),
        contentAlignment = Alignment.Center,
    ) {
        Text(Wg.NO_BABY_HINT, style = TextStyle(color = wgPalette.text2, fontSize = 13.sp))
    }
}

/** 彩色圆钮（四钮/六键共用）：emoji 圆底 + 下方小标签 */
@Composable
internal fun CircleButton(emoji: String, label: String, color: ColorProvider, action: Action, size: Dp = 42.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            GlanceModifier
                .size(size)
                .background(color)
                .cornerRadius(size / 2)
                .clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, style = TextStyle(fontSize = 16.sp))
        }
        Text(label, style = TextStyle(color = wgPalette.text3, fontSize = 10.sp))
    }
}

/** 大按钮（睡眠开始/结束）：主色圆角长条 */
@Composable
internal fun BigButton(text: String, action: Action) {
    Box(
        GlanceModifier
            .fillMaxSize()
            .background(wgPalette.primary)
            .cornerRadius(24.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(color = wgPalette.onPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold))
    }
}
