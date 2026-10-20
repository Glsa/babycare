package com.babycare.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CheckupsTest {

    @Test
    fun plans_42dIsBirthdayPlus41() {
        // 出生当天 = 第 1 天 → 第 42 天 = 生日 + 41 天
        val plans = Checkups.plans(LocalDate.of(2026, 8, 10), "b1")
        val d42 = plans.first { it.title == "42天体检" }
        assertEquals(LocalDate.of(2026, 9, 20), d42.date)
        assertEquals("checkup-42d-b1", d42.uuid)
    }

    @Test
    fun plans_nineNodes_deterministicUuids_monthlyDates() {
        val birthday = LocalDate.of(2026, 8, 10)
        val plans = Checkups.plans(birthday, "b1")
        assertEquals(9, plans.size)
        assertEquals(LocalDate.of(2026, 11, 10), plans.first { it.title == "3月龄体检" }.date)
        assertEquals(LocalDate.of(2029, 8, 10), plans.first { it.title == "36月龄体检" }.date)
        // uuid 确定且不重复 → 生成幂等
        assertEquals(plans.size, plans.map { it.uuid }.toSet().size)
        assertTrue(plans.all { it.uuid.startsWith("checkup-") && it.uuid.endsWith("-b1") })
    }
}
