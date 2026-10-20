package com.babycare.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EduKitTest {

    private val today = LocalDate.of(2026, 10, 18)

    @Test
    fun pool_monthBands() {
        assertEquals(6, EduKit.pool(0).size)   // 0~1 月
        assertEquals(5, EduKit.pool(2).size)   // 2~3 月
        assertTrue(EduKit.pool(5).all { it.minMonth <= 5 && it.maxMonth >= 5 })
        assertEquals(4, EduKit.pool(12).size)  // 10~12 月
        // 超库范围 → 空池（成长页整卡隐藏）
        assertTrue(EduKit.pool(13).isEmpty())
    }

    @Test
    fun picks_deterministicAndCountAlternates() {
        val pool = EduKit.pool(0)
        // 同一天永远同结果
        assertEquals(EduKit.picks(pool, today, emptySet()), EduKit.picks(pool, today, emptySet()))
        // 相邻两天条数在 2/3 间轮换
        val sizes = setOf(
            EduKit.picks(pool, today, emptySet()).size,
            EduKit.picks(pool, today.plusDays(1), emptySet()).size,
        )
        assertEquals(setOf(2, 3), sizes)
    }

    @Test
    fun picks_avoidsYesterdayWhenEnough() {
        val pool = EduKit.pool(0)   // 6 项，排除掉昨天 2~3 项后仍够数
        val base = EduKit.picks(pool, today, emptySet())
        val yesterday = base.map { it.title }.toSet()
        val result = EduKit.picks(pool, today, yesterday)
        assertEquals(base.size, result.size)
        assertTrue(result.none { it.title in yesterday })
    }

    @Test
    fun picks_smallPoolAllowsRepeat() {
        val pool = EduKit.pool(0).take(2)
        val yesterday = pool.map { it.title }.toSet()   // 昨天全打过
        val result = EduKit.picks(pool, today, yesterday)
        // 池子只有 2 项：兜底允许与昨天重复，但不会凭空多出条目
        assertEquals(2, result.size)
        assertTrue(result.all { r -> pool.any { it.title == r.title } })
    }
}
