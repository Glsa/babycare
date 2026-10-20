package com.babycare.app.timer

import android.content.Context
import android.content.Intent
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.MetaKeys
import com.babycare.app.data.model.NursingTimerState
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.SleepPayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.data.repo.BabyRepository
import com.babycare.app.domain.TimeKit
import com.babycare.app.domain.TimerCalc
import java.time.Instant
import java.time.ZoneId

/**
 * 计时控制中心：睡眠/亲喂计时的全部状态变更唯一入口（UI 与前台服务通知按钮都走这里，
 * 避免两条路径状态漂移）。状态载体见 design.md §1：睡眠 = sleep 记录 endAt=null；亲喂 = meta 单行 JSON。
 * 每次变更后让 TimerService 对齐通知（有计时 → 常驻；无 → 自灭）。
 */
class TimerCenter(
    private val context: Context,
    private val repo: BabyRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) {

    // ---- 查询（服务重建通知用） ----

    suspend fun ongoingSleep(): RecordEntity? = repo.ongoingSleepOnce()

    suspend fun nursingTimer(): NursingTimerState? = repo.nursingTimerOnce()

    // ---- 睡眠 ----

    /** 1 击开始；已有进行中的睡眠则忽略（防双开） */
    suspend fun startSleep() {
        val babyId = activeBabyId() ?: return
        if (ongoingSleep() != null) return
        val t = now()
        repo.add(RecordTypes.SLEEP, SleepPayload(startAt = t, endAt = null, tag = "", note = ""), t, babyId)
        refreshService()
    }

    /** 结束（App 弹层路径：结束时间/标签/备注可调） */
    suspend fun stopSleep(endAt: Long, tag: String, note: String) {
        val e = ongoingSleep() ?: return
        val p = decodePayloadOrNull(e.type, e.payload) as? SleepPayload ?: return
        repo.update(e, p.copy(endAt = endAt, tag = tag, note = note), p.startAt)
        refreshService()
    }

    /** 结束（通知按钮路径：立即结束，按结束小时自动判标签，保留原备注） */
    suspend fun stopSleepNow() {
        val e = ongoingSleep() ?: return
        val p = decodePayloadOrNull(e.type, e.payload) as? SleepPayload ?: return
        val endAt = now()
        stopSleep(endAt, TimeKit.sleepTag(Instant.ofEpochMilli(endAt).atZone(zone).hour), p.note)
    }

    suspend fun cancelSleep() {
        ongoingSleep()?.let { repo.softDelete(it) }
        refreshService()
    }

    // ---- 亲喂 ----

    suspend fun startNursing(side: String) {
        if (nursingTimer() != null) return
        val t = now()
        repo.writeNursingTimer(NursingTimerState(startAt = t, activeSide = side, sideStartAt = t))
        refreshService()
    }

    suspend fun switchNursingSide() {
        val st = nursingTimer() ?: return
        repo.writeNursingTimer(TimerCalc.switchSide(st, now()))
        refreshService()
    }

    suspend fun toggleNursingPause() {
        val st = nursingTimer() ?: return
        repo.writeNursingTimer(TimerCalc.togglePause(st, now()))
        refreshService()
    }

    /** 结束：累计左右分钟 → feed_nursing 记录（occurredAt = startAt），清暂态 */
    suspend fun stopNursing() {
        val st = nursingTimer() ?: return
        val babyId = activeBabyId() ?: return
        val payload = TimerCalc.finalizePayload(st, now(), note = "")
        repo.writeNursingTimer(null)
        repo.add(RecordTypes.FEED_NURSING, payload, st.startAt, babyId)
        refreshService()
    }

    suspend fun cancelNursing() {
        repo.writeNursingTimer(null)
        refreshService()
    }

    // ---- 服务 ----

    /** 状态变了让服务对齐通知；App 冷启动发现有计时未跑服务时也走这里（时间戳恢复） */
    fun refreshService() {
        // widget 等后台路径可能吃 FGS 启动限制（Android 12+）：失败不致命——
        // 状态源在 Room，通知缺失等 App 回前台后由恢复路径补上
        try {
            context.startForegroundService(
                Intent(context, TimerService::class.java).setAction(TimerService.ACTION_REFRESH)
            )
        } catch (_: Exception) {
        }
    }

    private suspend fun activeBabyId(): String? = repo.metaGet(MetaKeys.ACTIVE_BABY_ID)
}
