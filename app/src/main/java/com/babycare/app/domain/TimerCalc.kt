package com.babycare.app.domain

import com.babycare.app.data.model.FeedNursingPayload
import com.babycare.app.data.model.NursingTimerState

/**
 * 亲喂计时的纯函数集（换边/暂停/累计/落定），与 Android 无关、可表驱动测试。
 * 全部不改入参，返回新状态。
 */
object TimerCalc {

    /** 把当前段累计进对应侧（暂停中不累计）；跨段时刻（换边/结束）调用 */
    fun accumulate(st: NursingTimerState, now: Long): NursingTimerState {
        if (st.pausedAt != null) return st
        val seg = (now - st.sideStartAt).coerceAtLeast(0)
        return if (st.activeSide == NursingTimerState.SIDE_LEFT) {
            st.copy(leftMillis = st.leftMillis + seg)
        } else {
            st.copy(rightMillis = st.rightMillis + seg)
        }
    }

    /** 换边：结掉当前段，翻到另一侧重新起表。暂停中允许换边（不累计，恢复后从恢复时刻起算） */
    fun switchSide(st: NursingTimerState, now: Long): NursingTimerState {
        val acc = accumulate(st, now)
        val other = if (acc.activeSide == NursingTimerState.SIDE_LEFT) NursingTimerState.SIDE_RIGHT else NursingTimerState.SIDE_LEFT
        return acc.copy(activeSide = other, sideStartAt = now)
    }

    /** 暂停/继续切换：继续时把 sideStartAt 平移过暂停区间，当前段时长自动剔除暂停 */
    fun togglePause(st: NursingTimerState, now: Long): NursingTimerState =
        if (st.pausedAt == null) {
            st.copy(pausedAt = now)
        } else {
            st.copy(pausedAt = null, sideStartAt = st.sideStartAt + (now - st.pausedAt).coerceAtLeast(0))
        }

    /** 当前展示用的左右毫秒（累计 + 未暂停的当前段） */
    fun currentMillis(st: NursingTimerState, now: Long): Pair<Long, Long> {
        val acc = accumulate(st, now)
        return acc.leftMillis to acc.rightMillis
    }

    /** 毫秒 → 分钟，四舍五入（+30s / 60s） */
    fun minutesRounded(millis: Long): Int = ((millis + 30_000) / 60_000).toInt().coerceAtLeast(0)

    /** 结束：累计出左右分钟，生成亲喂记录 payload；occurredAt 用 st.startAt（由调用方决定） */
    fun finalizePayload(st: NursingTimerState, now: Long, note: String): FeedNursingPayload {
        val acc = accumulate(st, now)
        return FeedNursingPayload(
            leftMinutes = minutesRounded(acc.leftMillis),
            rightMinutes = minutesRounded(acc.rightMillis),
            note = note,
        )
    }
}
