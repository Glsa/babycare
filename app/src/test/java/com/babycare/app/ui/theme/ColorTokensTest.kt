package com.babycare.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** 抽样断言 token 色值与 Figma/UI设计说明 §2.1 一致（改设计稿忘了同步代码时这里先红） */
class ColorTokensTest {

    @Test
    fun light_tokens_sample() {
        assertEquals(Color(0xFFFAF6F3), LightBabyColors.bg)
        assertEquals(Color(0xFFFFFFFF), LightBabyColors.surface)
        assertEquals(Color(0xFF3D3633), LightBabyColors.text1)
        assertEquals(Color(0xFFE8697A), LightBabyColors.primary)
        assertEquals(Color(0xFF7D9BE8), LightBabyColors.sleep)
        assertEquals(Color(0xFFD9534F), LightBabyColors.vaccine)
    }

    @Test
    fun dark_tokens_sample() {
        assertEquals(Color(0xFF161210), DarkBabyColors.bg)      // 暖黑，深夜不刺眼
        assertEquals(Color(0xFFF0E9E4), DarkBabyColors.text1)
        assertEquals(Color(0xFFE8808F), DarkBabyColors.primary)
        assertEquals(Color(0xFF96ABEC), DarkBabyColors.sleep)
    }

    @Test
    fun nurse_shares_primary() {
        assertEquals(LightBabyColors.primary, LightBabyColors.nurse)
        assertEquals(DarkBabyColors.primary, DarkBabyColors.nurse)
    }
}
