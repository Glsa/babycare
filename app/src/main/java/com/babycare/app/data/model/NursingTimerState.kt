package com.babycare.app.data.model

import kotlinx.serialization.Serializable

/**
 * 亲喂计时中的暂态（meta 表 active_nursing 一行，JSON 存储）。
 * 记录表只存「完成事实」，计时过程态不进 records——睡眠例外（sleep 记录 endAt=null 即进行中）。
 *
 * 累计口径：leftMillis/rightMillis 是已结段的总和；当前段 = sideStartAt 起、未暂停时流逝的时间。
 * 暂停实现：pausedAt 记暂停起点，恢复时把 sideStartAt 平移过暂停区间，累计自然不含暂停。
 */
@Serializable
data class NursingTimerState(
    val startAt: Long,
    /** 当前侧（left / right；空串 = 无进行段） */
    val activeSide: String = SIDE_LEFT,
    val sideStartAt: Long = startAt,
    /** 暂停开始时刻；null = 未暂停 */
    val pausedAt: Long? = null,
    val leftMillis: Long = 0,
    val rightMillis: Long = 0,
) {
    companion object {
        const val SIDE_LEFT = "left"
        const val SIDE_RIGHT = "right"
    }
}
