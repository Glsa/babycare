package com.babycare.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 演示数据口径：小汤圆 2026-08-10 生，2026-10-17 = 第 69 天 · 2 个月 7 天，百天 11-17 */
class AgeCalcTest {

    private val birthday = LocalDate.of(2026, 8, 10)

    @Test
    fun dayNumber_table() {
        val cases = listOf(
            birthday to 1,                                  // 出生当天 = 第 1 天
            birthday.plusDays(1) to 2,
            LocalDate.of(2026, 10, 17) to 69,               // 演示数据口径
            LocalDate.of(2026, 11, 17) to 100,              // 百天 = 第 100 天
        )
        for ((today, expected) in cases) {
            assertEquals("生日 $birthday · 今天 $today", expected, AgeCalc.dayNumber(today, birthday))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun dayNumber_beforeBirthday_throws() {
        AgeCalc.dayNumber(birthday.minusDays(1), birthday)
    }

    @Test
    fun monthAge_table() {
        assertEquals("15天", AgeCalc.monthAgeText(birthday, birthday.plusDays(15)))
        assertEquals("2个月7天", AgeCalc.monthAgeText(birthday, LocalDate.of(2026, 10, 17)))
        assertEquals("1个月", AgeCalc.monthAgeText(birthday, LocalDate.of(2026, 9, 10)))
        assertEquals("12个月", AgeCalc.monthAgeText(birthday, LocalDate.of(2027, 8, 10))) // 1 周岁整
    }

    @Test
    fun hundredthDay_isDay100() {
        assertEquals(LocalDate.of(2026, 11, 17), AgeCalc.hundredthDay(birthday))
        assertEquals(100, AgeCalc.dayNumber(AgeCalc.hundredthDay(birthday), birthday))
    }
}
