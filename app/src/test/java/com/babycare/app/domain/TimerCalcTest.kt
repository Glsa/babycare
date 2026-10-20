package com.babycare.app.domain

import com.babycare.app.data.model.NursingTimerState
import com.babycare.app.data.model.RecordJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerCalcTest {

    private val t0 = 1_000_000_000_000L   // 计时起点
    private val min = 60_000L

    private fun state(side: String = NursingTimerState.SIDE_LEFT) =
        NursingTimerState(startAt = t0, activeSide = side, sideStartAt = t0)

    @Test
    fun accumulate_singleSide() {
        // 左侧喂 10 分钟：当前段直接累计
        val (l, r) = TimerCalc.currentMillis(state(), t0 + 10 * min)
        assertEquals(10 * min, l)
        assertEquals(0L, r)
    }

    @Test
    fun accumulate_switchSplitsSides() {
        // 左 10 分 → 换边 → 右 8 分：左结 10，右在途 8
        val afterSwitch = TimerCalc.switchSide(state(), t0 + 10 * min)
        assertEquals(NursingTimerState.SIDE_RIGHT, afterSwitch.activeSide)
        assertEquals(10 * min, afterSwitch.leftMillis)
        val (l, r) = TimerCalc.currentMillis(afterSwitch, t0 + 18 * min)
        assertEquals(10 * min, l)
        assertEquals(8 * min, r)
    }

    @Test
    fun pause_notAccumulating_andResumeShiftsSegment() {
        // 左 5 分 → 暂停 10 分 → 继续 → 又过 3 分：总计 8 分（暂停剔除）
        val paused = TimerCalc.togglePause(state(), t0 + 5 * min)
        assertEquals(t0 + 5 * min, paused.pausedAt)
        // 暂停中不累计
        val (lp, _) = TimerCalc.currentMillis(paused, t0 + 30 * min)
        assertEquals(0L, lp)
        val resumed = TimerCalc.togglePause(paused, t0 + 15 * min)   // 暂停了 10 分钟
        assertNull(resumed.pausedAt)
        val (l, _) = TimerCalc.currentMillis(resumed, t0 + 18 * min) // 恢复后又 3 分钟
        assertEquals(8 * min, l)
    }

    @Test
    fun finalize_roundsMinutes() {
        // 左 5 分 30 秒、右 8 分 20 秒 → 四舍五入 6 / 8
        val st = TimerCalc.switchSide(state(), t0 + (5 * min + 30_000))
        val payload = TimerCalc.finalizePayload(st, t0 + 13 * min + 50_000, note = "")
        assertEquals(6, payload.leftMinutes)
        assertEquals(8, payload.rightMinutes)
    }

    @Test
    fun minutesRounded_table() {
        assertEquals(0, TimerCalc.minutesRounded(29_999))
        assertEquals(1, TimerCalc.minutesRounded(30_000))
        assertEquals(2, TimerCalc.minutesRounded(90_000))
        assertEquals(0, TimerCalc.minutesRounded(-5_000))   // 时钟异常按 0
    }

    @Test
    fun stateJson_roundtrip() {
        val st = TimerCalc.switchSide(
            TimerCalc.togglePause(state(), t0 + min).let { TimerCalc.togglePause(it, t0 + 2 * min) },
            t0 + 3 * min,
        )
        val json = RecordJson.encodeToString(NursingTimerState.serializer(), st)
        assertEquals(st, RecordJson.decodeFromString(NursingTimerState.serializer(), json))
        // 手动补录典型值也往返一遍
        val plain = NursingTimerState(startAt = t0)
        val j2 = RecordJson.encodeToString(NursingTimerState.serializer(), plain)
        assertEquals(plain, RecordJson.decodeFromString(NursingTimerState.serializer(), j2))
    }

    @Test
    fun switchSide_whilePaused_keepsZeroAndFlips() {
        // 暂停中换边：不累计，翻侧
        val paused = TimerCalc.togglePause(state(), t0 + 3 * min)
        val switched = TimerCalc.switchSide(paused, t0 + 10 * min)
        assertTrue(switched.pausedAt != null)
        assertEquals(NursingTimerState.SIDE_RIGHT, switched.activeSide)
        assertEquals(0L, switched.leftMillis)
    }
}
