package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.DailyTaskLogPayload
import com.babycare.app.data.model.DailyTaskPayload
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.decodePayloadOrNull

/**
 * 每日必做（0013）：固定任务清单 + 当日勾选。
 * dateKey = LocalDate.toString()（yyyy-MM-dd），跨天自动回到未完成。
 */
object DailyKit {

    /** 预置任务（名字带 emoji 前缀，用户可删；自定义任务随输入） */
    val DEFAULT_TASKS = listOf("💊 吃维AD", "🧼 洗脸", "🤸 排气操", "⬛ 黑白卡", "👂 听力训练")

    /** 某任务今日的打卡记录；null = 今天还没做 */
    fun dayLog(logs: List<RecordEntity>, taskUuid: String, dateKey: String): RecordEntity? =
        logs.firstOrNull { log ->
            (decodePayloadOrNull(log.type, log.payload) as? DailyTaskLogPayload)
                ?.let { it.taskUuid == taskUuid && it.dateKey == dateKey } == true
        }

    /** 按 payload.sortOrder 升序展示（sortBy 稳定，同序按插入序） */
    fun sortedTasks(tasks: List<RecordEntity>): List<RecordEntity> =
        tasks.sortedBy { (decodePayloadOrNull(it.type, it.payload) as? DailyTaskPayload)?.sortOrder ?: 0 }

    /** 下一个任务的 sortOrder（新增排到最后） */
    fun nextSortOrder(tasks: List<RecordEntity>): Int =
        (tasks.maxOfOrNull { (decodePayloadOrNull(it.type, it.payload) as? DailyTaskPayload)?.sortOrder ?: 0 } ?: -1) + 1

    /** 打卡 payload 便捷解（坏数据当 null） */
    fun taskPayload(e: RecordEntity): DailyTaskPayload? =
        if (e.type != RecordTypes.DAILY_TASK) null
        else decodePayloadOrNull(e.type, e.payload) as? DailyTaskPayload
}
