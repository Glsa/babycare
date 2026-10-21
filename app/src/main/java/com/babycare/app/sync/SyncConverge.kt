package com.babycare.app.sync

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.DailyTaskPayload
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.decodePayloadOrNull

/**
 * 分叉收敛规划（0016 K11）：双机各自建档导致的「多份宝宝档案 / 重复每日必做」，
 * 每轮同步后对账收敛。纯函数（规则全端一致 → 不依赖谁先跑），落地动作在 SyncCenter。
 *
 * 场景：B 先被引导建档再配对（不建档进不了我的页），同步后家里两份档案，
 * 界面跟错档案 = A 的记录「看不见」。
 *
 * 规则：
 * - 档案：未删 ≥2 份时保 updatedAt 最旧的（先存在的家庭档案为准），其余打墓碑；
 *   输家名下记录换挂赢家（不然记录跟着死档案一起隐身）
 * - 每日必做：同名任务保最旧（B 绑定前也播种过默认清单）
 * - 平局比 uuid（各端算出同一赢家，不会各删各的）
 */
object SyncConverge {

    data class Plan(
        /** 赢家档案；仅当存在冲突（losers 非空）时非空 */
        val babyWinner: RecordEntity?,
        val babyLosers: List<RecordEntity>,
        val taskLosers: List<RecordEntity>,
    ) {
        val hasWork: Boolean get() = babyLosers.isNotEmpty() || taskLosers.isNotEmpty()
    }

    fun plan(all: List<RecordEntity>): Plan {
        val live = all.filter { !it.deleted }

        // 收敛序：updatedAt 升序，平局 uuid 字典序（全端算出同一赢家）
        val byAge = compareBy<RecordEntity> { it.updatedAt }.thenBy { it.uuid }
        val profiles = live.filter { it.type == RecordTypes.BABY_PROFILE }
        val winner = profiles.minWithOrNull(byAge)
        val babyLosers = if (profiles.size >= 2) {
            profiles.filter { it.uuid != winner?.uuid }
        } else emptyList()

        val taskLosers = live.filter { it.type == RecordTypes.DAILY_TASK }
            .groupBy { taskName(it) }
            .flatMap { (_, group) -> group.sortedWith(byAge).drop(1) }

        return Plan(babyWinner = winner?.takeIf { babyLosers.isNotEmpty() }, babyLosers = babyLosers, taskLosers = taskLosers)
    }

    /** 任务名作归并键；坏 payload 用 uuid 自成一组（不参与去重也不误删） */
    private fun taskName(e: RecordEntity): String =
        (decodePayloadOrNull(e.type, e.payload) as? DailyTaskPayload)?.name ?: e.uuid
}
