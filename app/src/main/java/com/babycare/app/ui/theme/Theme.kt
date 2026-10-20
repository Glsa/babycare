package com.babycare.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 扩展色全集：M3 色板装不下的类型色/软色底从这里取 */
val LocalBabyColors = staticCompositionLocalOf { LightBabyColors }

/** 页面统一取色入口：MaterialTheme.colorScheme（基础角色）+ BabyTheme.colors（扩展 token） */
object BabyTheme {
    val colors: BabyColors
        @Composable get() = LocalBabyColors.current
}

/**
 * 主题：不用动态取色（品牌暖色优先于壁纸取色）；暗色为暖黑（深夜喂奶不刺眼，见 PRD 非功能需求）。
 * M3 只映射有的角色，类型色一律走 LocalBabyColors。
 */
@Composable
fun BabycareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val c = if (darkTheme) DarkBabyColors else LightBabyColors
    // 强调色上的文字/图标色：亮暗两套强调色都偏亮，统一用暖深色保证对比度
    val onAccentDark = Color(0xFF3D3633)
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.primary,
            onPrimary = Color(0xFFFFFFFF),
            secondary = c.feed,
            onSecondary = onAccentDark,
            tertiary = c.sleep,
            onTertiary = onAccentDark,
            background = c.bg,
            onBackground = c.text1,
            surface = c.surface,
            onSurface = c.text1,
            surfaceVariant = c.surfaceAlt,
            onSurfaceVariant = c.text2,
            outlineVariant = c.divider,
            error = c.vaccine,
            onError = Color(0xFFFFFFFF),
        )
    } else {
        lightColorScheme(
            primary = c.primary,
            onPrimary = Color(0xFFFFFFFF),
            secondary = c.feed,
            onSecondary = onAccentDark,
            tertiary = c.sleep,
            onTertiary = onAccentDark,
            background = c.bg,
            onBackground = c.text1,
            surface = c.surface,
            onSurface = c.text1,
            surfaceVariant = c.surfaceAlt,
            onSurfaceVariant = c.text2,
            outlineVariant = c.divider,
            error = c.vaccine,
            onError = Color(0xFFFFFFFF),
        )
    }

    CompositionLocalProvider(LocalBabyColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            content = content
        )
    }
}
