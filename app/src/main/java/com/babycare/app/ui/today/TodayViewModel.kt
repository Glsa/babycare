package com.babycare.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.AgendaPayload
import com.babycare.app.data.model.BabyProfilePayload
import com.babycare.app.data.model.BathPayload
import com.babycare.app.data.model.DailyTaskLogPayload
import com.babycare.app.data.model.DailyTaskPayload
import com.babycare.app.data.model.DiaperPayload
import com.babycare.app.data.model.EduCheckinPayload
import com.babycare.app.data.model.FeedFormulaPayload
import com.babycare.app.data.model.FeedNursingPayload
import com.babycare.app.data.model.GrowthPayload
import com.babycare.app.data.model.MetaKeys
import com.babycare.app.data.model.NursingTimerState
import com.babycare.app.data.model.RecordPayload
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.SleepPayload
import com.babycare.app.data.model.SupplementLogPayload
import com.babycare.app.data.model.SupplementPlanPayload
import com.babycare.app.data.model.VaccineDosePayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.data.repo.BabyRepository
import com.babycare.app.domain.BabyInfo
import com.babycare.app.domain.Checkups
import com.babycare.app.domain.DailyKit
import com.babycare.app.domain.DaySummary
import com.babycare.app.domain.TimeKit
import com.babycare.app.domain.Vaccines
import com.babycare.app.domain.computeDaySummary
import com.babycare.app.domain.toBabyInfo
import com.babycare.app.sync.SyncCenter
import com.babycare.app.timer.TimerCenter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** 今天页状态。loaded=false 表示 Room 首次加载中（避免误闪首启引导） */
data class TodayUiState(
    val loaded: Boolean = false,
    val baby: BabyInfo? = null,
    val date: LocalDate = LocalDate.now(),
    val records: List<RecordEntity> = emptyList(),
    val summary: DaySummary = DaySummary(),
    val lastFeed: RecordEntity? = null,
    /** 进行中的睡眠（endAt=null 的 sleep 记录；不限今天，跨天夜觉也在） */
    val ongoingSleep: RecordEntity? = null,
    /** 亲喂计时暂态（meta）；null = 没在计时 */
    val nursingTimer: NursingTimerState? = null,
    val nowMillis: Long = 0L,
    /** 补剂方案 / 疫苗记录 / 早教打卡全量（0008 回填口径；不随日期重订阅，页面内存滤） */
    val supplementPlans: List<RecordEntity> = emptyList(),
    val vaccineRecords: List<RecordEntity> = emptyList(),
    val eduCheckins: List<RecordEntity> = emptyList(),
    /** 每日必做：任务定义与打卡全量（0013；dateKey 内存滤，跨天自动复位） */
    val dailyTasks: List<RecordEntity> = emptyList(),
    val dailyLogs: List<RecordEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repo: BabyRepository,
    private val timers: TimerCenter,
    sync: SyncCenter,
    val myDeviceId: String,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 记录人角标（0016 D-3）：服务端设备 ID → 设备名；未绑定为空 Map */
    val authorNames: StateFlow<Map<String, String>> = sync.deviceNamesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 本机记录返回 null（不标注），对端记录返回设备名（未知回落「其他设备」） */
    fun authorLabel(author: String): String? =
        if (author == myDeviceId) null else authorNames.value[author] ?: "其他设备"

    /** 30 秒心跳：驱动「进行中计时」「距上次喂奶」刷新 */
    private val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val baby: StateFlow<BabyInfo?> = repo.observeBaby()
        .map { list -> list.firstOrNull()?.toBabyInfo() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // 时间戳恢复：App 冷启动发现有计时在跑（服务可能已被杀）→ 拉起前台服务重建通知
        viewModelScope.launch {
            combine(repo.observeOngoingSleep(), repo.observeNursingTimer()) { s, n -> s != null || n != null }
                .distinctUntilChanged()
                .filter { it }
                .collect { timers.refreshService() }
        }
        // 每日必做预置（0013）：无标记且无任务时插默认清单一次；删光不塞回。
        // 0016 双保险（K9）：同步绑定后先等首轮完成（封「档案已到任务未到」竞态）；
        // 判定改 hasAny（含墓碑）——对端删光的清单，墓碑到了也不重新播种
        viewModelScope.launch {
            baby.filterNotNull().collect { b ->
                if (repo.metaGet(MetaKeys.DAILY_TASKS_SEEDED) != null) return@collect
                sync.awaitInitialSyncIfBound()
                if (!repo.hasAnyType(RecordTypes.DAILY_TASK)) {
                    DailyKit.DEFAULT_TASKS.forEachIndexed { i, name ->
                        repo.add(
                            type = RecordTypes.DAILY_TASK,
                            payload = DailyTaskPayload(name = name, sortOrder = i),
                            occurredAt = System.currentTimeMillis(),
                            babyId = b.uuid,
                        )
                    }
                }
                repo.metaPut(MetaKeys.DAILY_TASKS_SEEDED, "1")
            }
        }
        // 体检自动行程（附录 C）：每个宝宝确保生成一次；确定性 uuid → 幂等，手动软删的不补回
        viewModelScope.launch {
            baby.map { b -> b?.let { it.uuid to it.birthday } }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { (id, birthday) ->
                    Checkups.plans(birthday, id).forEach { p ->
                        if (repo.byUuid(p.uuid) == null) {
                            repo.add(
                                type = RecordTypes.AGENDA,
                                payload = AgendaPayload(title = p.title, kind = Checkups.KIND),
                                occurredAt = p.date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
                                babyId = id,
                                uuid = p.uuid,
                            )
                        }
                    }
                }
        }
    }

    /** 当日状态流（不含量表三流；date 变化才重订阅当日查询） */
    private val dayFlow = combine(
        baby,
        // 只在跨天时重订阅当日查询（LocalDate 作 key 去重）
        now.map { TimeKit.toLocalDate(it, zone) }.distinctUntilChanged(),
    ) { b, d -> b to d }
        .flatMapLatest { (b, date) ->
            if (b == null) {
                flowOf(TodayUiState(loaded = true))
            } else {
                combine(
                    repo.observeDay(b.uuid, TimeKit.dayStart(date, zone), TimeKit.dayEndExclusive(date, zone)),
                    repo.observeLastFeed(b.uuid),
                    repo.observeOngoingSleep(),
                    repo.observeNursingTimer(),
                ) { records, lastFeed, ongoingSleep, nursingTimer ->
                    TodayUiState(
                        loaded = true, baby = b, date = date, records = records,
                        lastFeed = lastFeed, ongoingSleep = ongoingSleep, nursingTimer = nursingTimer,
                    )
                }
            }
        }

    val ui: StateFlow<TodayUiState> = combine(
        dayFlow,
        // 0008 回填口径：补剂方案/疫苗/早教打卡全量（量小），不随日期重订阅
        repo.observeByType(RecordTypes.SUPPLEMENT_PLAN),
        repo.observeByType(RecordTypes.VACCINE_DOSE),
        repo.observeByType(RecordTypes.EDU_CHECKIN),
        // 0013 每日必做两流先并成一个再进（combine 直接参数上限 5）
        combine(
            repo.observeByType(RecordTypes.DAILY_TASK),
            repo.observeByType(RecordTypes.DAILY_TASK_LOG),
        ) { tasks, logs -> tasks to logs },
    ) { s, plans, vaccines, edus, daily ->
        s.copy(
            supplementPlans = plans, vaccineRecords = vaccines, eduCheckins = edus,
            dailyTasks = daily.first, dailyLogs = daily.second,
        )
    }
        .combine(now) { s, n -> s.copy(summary = computeDaySummary(s.records, n), nowMillis = n) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    // ---- 计时动作（全部走 TimerCenter 单一入口） ----

    fun startSleep() = launch { timers.startSleep() }

    fun stopSleep(endAt: Long, tag: String, note: String) = launch {
        timers.stopSleep(endAt, tag, note)
    }

    fun cancelSleep() = launch { timers.cancelSleep() }

    fun startNursing(side: String) = launch { timers.startNursing(side) }

    fun switchNursingSide() = launch { timers.switchNursingSide() }

    fun toggleNursingPause() = launch { timers.toggleNursingPause() }

    fun stopNursing() = launch { timers.stopNursing() }

    fun cancelNursing() = launch { timers.cancelNursing() }

    // ---- 记录动作 ----

    fun saveFormula(ml: Int, kind: String, occurredAt: Long, note: String) =
        add(RecordTypes.FEED_FORMULA, FeedFormulaPayload(ml, kind, note), occurredAt)

    fun saveDiaper(pee: Boolean, poop: Boolean, occurredAt: Long, note: String) =
        add(RecordTypes.DIAPER, DiaperPayload(pee, poop, note), occurredAt)

    fun saveBath(occurredAt: Long, note: String) =
        add(RecordTypes.BATH, BathPayload(note), occurredAt)

    fun saveNursing(leftMinutes: Int, rightMinutes: Int, occurredAt: Long, note: String) =
        add(RecordTypes.FEED_NURSING, FeedNursingPayload(leftMinutes, rightMinutes, note), occurredAt)

    fun saveSleep(startAt: Long, endAt: Long, tag: String, note: String) =
        add(RecordTypes.SLEEP, SleepPayload(startAt = startAt, endAt = endAt, tag = tag, note = note), startAt)

    fun updateFormula(e: RecordEntity, ml: Int, kind: String, occurredAt: Long, note: String) =
        update(e, FeedFormulaPayload(ml, kind, note), occurredAt)

    fun updateDiaper(e: RecordEntity, pee: Boolean, poop: Boolean, occurredAt: Long, note: String) =
        update(e, DiaperPayload(pee, poop, note), occurredAt)

    fun updateBath(e: RecordEntity, occurredAt: Long, note: String) =
        update(e, BathPayload(note), occurredAt)

    fun updateNursing(e: RecordEntity, leftMinutes: Int, rightMinutes: Int, occurredAt: Long, note: String) =
        update(e, FeedNursingPayload(leftMinutes, rightMinutes, note), occurredAt)

    fun updateSleep(e: RecordEntity, startAt: Long, endAt: Long, tag: String, note: String) =
        update(e, SleepPayload(startAt, endAt, tag, note), startAt)

    fun saveAgenda(title: String, kind: String, occurredAt: Long, note: String, remindDaysBefore: Int = 0) =
        add(RecordTypes.AGENDA, AgendaPayload(title = title, kind = kind, remindDaysBefore = remindDaysBefore, note = note), occurredAt)

    /** 编辑不改完成态（完成走 toggleAgendaDone），done 从原 payload 带过来 */
    fun updateAgenda(e: RecordEntity, title: String, kind: String, occurredAt: Long, note: String, remindDaysBefore: Int) {
        val done = (decodePayloadOrNull(e.type, e.payload) as? AgendaPayload)?.done ?: false
        update(e, AgendaPayload(title = title, kind = kind, remindDaysBefore = remindDaysBefore, done = done, note = note), occurredAt)
    }

    fun toggleAgendaDone(e: RecordEntity) {
        val p = decodePayloadOrNull(e.type, e.payload) as? AgendaPayload ?: return
        update(e, p.copy(done = !p.done))
    }

    // ---- 生长（0007）：三项可只填部分，来源在家/体检 ----

    fun saveGrowth(weightKg: Double?, heightCm: Double?, headCm: Double?, source: String, occurredAt: Long) =
        add(RecordTypes.GROWTH, GrowthPayload(weightKg, heightCm, headCm, source), occurredAt)

    fun updateGrowth(
        e: RecordEntity,
        weightKg: Double?,
        heightCm: Double?,
        headCm: Double?,
        source: String,
        occurredAt: Long,
    ) = update(e, GrowthPayload(weightKg, heightCm, headCm, source), occurredAt)

    // ---- 疫苗（0007）：计划在 Vaccines 推导，这里只落用户操作产生的记录 ----

    /** 标已种：日期 = 实际接种日，occurredAt 同值（时间统一 10:00，与体检口径一致） */
    fun saveVaccineDose(vaccine: String, dose: Int, key: String, date: Long, location: String, note: String) =
        add(
            RecordTypes.VACCINE_DOSE,
            VaccineDosePayload(vaccine, dose, date, location, note, key, Vaccines.STATUS_DONE),
            date,
        )

    /** 不接种：日期取计划日，仅作状态载体 */
    fun markVaccineSkipped(vaccine: String, dose: Int, key: String, planDate: Long) =
        add(
            RecordTypes.VACCINE_DOSE,
            VaccineDosePayload(vaccine, dose, planDate, note = "", key = key, status = Vaccines.STATUS_SKIPPED),
            planDate,
        )

    /** 编辑接种信息；key/status 与疫苗名剂次从原 payload 带回 */
    fun updateVaccineDose(e: RecordEntity, date: Long, location: String, note: String) {
        val p = decodePayloadOrNull(e.type, e.payload) as? VaccineDosePayload ?: return
        update(e, p.copy(date = date, location = location, note = note), date)
    }

    // ---- 补剂（0008）：方案 = SUPPLEMENT_PLAN 记录，打卡 = SUPPLEMENT_LOG 记录 ----

    fun saveSupplementPlan(name: String, dose: String, timePoints: List<String>, startDate: Long) =
        add(RecordTypes.SUPPLEMENT_PLAN, SupplementPlanPayload(name, dose, timePoints, startDate), startDate)

    /** 编辑不改停用态（停用走 toggleSupplementStopped），stopped 从原 payload 带回 */
    fun updateSupplementPlan(e: RecordEntity, name: String, dose: String, timePoints: List<String>, startDate: Long) {
        val p = decodePayloadOrNull(e.type, e.payload) as? SupplementPlanPayload ?: return
        update(e, p.copy(name = name, dose = dose, timePoints = timePoints, startDate = startDate), startDate)
    }

    fun toggleSupplementStopped(e: RecordEntity) {
        val p = decodePayloadOrNull(e.type, e.payload) as? SupplementPlanPayload ?: return
        update(e, p.copy(stopped = !p.stopped))
    }

    /** 补剂打卡切换：existing 由页面从当日记录里查好传入（名字+时间点+今天） */
    fun toggleSupplementLog(existing: RecordEntity?, name: String, planTime: String) {
        if (existing != null) {
            viewModelScope.launch { repo.softDelete(existing) }
            return
        }
        val now = System.currentTimeMillis()
        add(RecordTypes.SUPPLEMENT_LOG, SupplementLogPayload(name = name, takenAt = now, planTime = planTime), now)
    }

    // ---- 早教（0008）：推荐在 EduKit 纯函数，这里只落打卡记录 ----

    /** 打卡切换：existing = 今天该项目已有的打卡记录（页面查好传入） */
    fun toggleEduCheckin(existing: RecordEntity?, category: String, item: String) {
        if (existing != null) {
            viewModelScope.launch { repo.softDelete(existing) }
            return
        }
        add(RecordTypes.EDU_CHECKIN, EduCheckinPayload(category = category, item = item), System.currentTimeMillis())
    }

    // ---- 每日必做（0013） ----

    /** 勾选/取消：有今日打卡则软删，无则新建（与补剂/早教同一 toggle 模式） */
    fun toggleDailyTask(existing: RecordEntity?, task: RecordEntity) {
        if (existing != null) {
            viewModelScope.launch { repo.softDelete(existing) }
            return
        }
        val dateKey = ui.value.date.toString()
        add(
            RecordTypes.DAILY_TASK_LOG,
            DailyTaskLogPayload(taskUuid = task.uuid, dateKey = dateKey),
            System.currentTimeMillis(),
        )
    }

    fun addDailyTask(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val tasks = ui.value.dailyTasks
        add(RecordTypes.DAILY_TASK, DailyTaskPayload(name = trimmed, sortOrder = DailyKit.nextSortOrder(tasks)), System.currentTimeMillis())
    }

    fun deleteDailyTask(task: RecordEntity) {
        viewModelScope.launch { repo.softDelete(task) }
    }

    fun saveEduCheckin(category: String, item: String, minutes: Int?, occurredAt: Long) =
        add(RecordTypes.EDU_CHECKIN, EduCheckinPayload(category, item, minutes), occurredAt)

    /** 编辑只改时长/时间；类目项目从原 payload 带回 */
    fun updateEduCheckin(e: RecordEntity, minutes: Int?, occurredAt: Long) {
        val p = decodePayloadOrNull(e.type, e.payload) as? EduCheckinPayload ?: return
        update(e, p.copy(minutes = minutes), occurredAt)
    }

    fun delete(entity: RecordEntity) {
        viewModelScope.launch {
            repo.softDelete(entity)
            // 删的是进行中的睡眠 = 视同取消计时，让通知同步消失
            if (entity.type == RecordTypes.SLEEP) timers.refreshService()
        }
    }

    fun createBaby(nickname: String, birthday: LocalDate, gender: String) {
        viewModelScope.launch {
            // 档案记录的 babyId 就是自己的 uuid（自指），meta 记 active 供后续多宝宝用
            val uuid = UUID.randomUUID().toString()
            repo.add(
                type = RecordTypes.BABY_PROFILE,
                payload = BabyProfilePayload(nickname = nickname, birthday = birthday.toString(), gender = gender),
                occurredAt = TimeKit.dayStart(birthday),
                babyId = uuid,
                uuid = uuid,
            )
            repo.metaPut(MetaKeys.ACTIVE_BABY_ID, uuid)
        }
    }

    // ---- 私有 ----

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun add(type: String, payload: RecordPayload, occurredAt: Long) {
        val babyId = baby.value?.uuid ?: return
        viewModelScope.launch { repo.add(type, payload, occurredAt, babyId) }
    }

    private fun update(entity: RecordEntity, payload: RecordPayload, occurredAt: Long = entity.occurredAt) {
        viewModelScope.launch { repo.update(entity, payload, occurredAt) }
    }
}
