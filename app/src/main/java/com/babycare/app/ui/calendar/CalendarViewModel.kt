package com.babycare.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.repo.BabyRepository
import com.babycare.app.domain.BabyInfo
import com.babycare.app.domain.CalendarKit
import com.babycare.app.domain.DaySummary
import com.babycare.app.domain.TimeKit
import com.babycare.app.domain.computeDaySummary
import com.babycare.app.domain.toBabyInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 日历页状态。selected=null 表示未选天（只看月视图）；未来日 selectedRecords 为空 */
data class CalendarUiState(
    val loaded: Boolean = false,
    val baby: BabyInfo? = null,
    val month: YearMonth = YearMonth.now(),
    /** 当月每天出现的记录类型（色点数据源） */
    val dayTypes: Map<LocalDate, Set<String>> = emptyMap(),
    val selected: LocalDate? = null,
    val selectedRecords: List<RecordEntity> = emptyList(),
    val selectedSummary: DaySummary = DaySummary(),
)

/** 日历页读流：月视图色点 + 选中日明细。写操作复用 TodayViewModel（写方法无「今天」耦合） */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repo: BabyRepository) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    val baby: StateFlow<BabyInfo?> = repo.observeBaby()
        .map { list -> list.firstOrNull()?.toBabyInfo() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val month = MutableStateFlow(YearMonth.now(zone))
    private val selected = MutableStateFlow<LocalDate?>(null)

    val ui: StateFlow<CalendarUiState> = combine(baby, month, selected) { b, m, s -> Triple(b, m, s) }
        .flatMapLatest { (b, m, sel) ->
            if (b == null) {
                flowOf(CalendarUiState(loaded = true))
            } else {
                val monthFlow = repo.observeDay(
                    b.uuid,
                    TimeKit.dayStart(m.atDay(1), zone),
                    TimeKit.dayEndExclusive(m.atEndOfMonth(), zone),
                ).map { CalendarKit.groupDayTypes(it, zone) }
                val dayFlow = if (sel == null) {
                    flowOf(emptyList())
                } else {
                    repo.observeDay(b.uuid, TimeKit.dayStart(sel, zone), TimeKit.dayEndExclusive(sel, zone))
                }
                combine(monthFlow, dayFlow) { types, records ->
                    CalendarUiState(
                        loaded = true, baby = b, month = m, dayTypes = types,
                        selected = sel, selectedRecords = records,
                        selectedSummary = sel?.let { computeDaySummary(records, summaryNow(it)) } ?: DaySummary(),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    /** 历史日传当天 23:59:59.999（无「进行中」概念）；今天传真实时间做静态快照（不跟 30s 心跳，实时卡在首页） */
    private fun summaryNow(date: LocalDate): Long =
        if (date == LocalDate.now(zone)) System.currentTimeMillis()
        else TimeKit.dayEndExclusive(date, zone) - 1

    fun prevMonth() {
        month.value = month.value.minusMonths(1)
        selected.value = null   // 翻页清选中（design §5）
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
        selected.value = null
    }

    fun goToday() {
        val today = LocalDate.now(zone)
        month.value = YearMonth.from(today)
        selected.value = today
    }

    /** 再点同一天取消选中 */
    fun select(date: LocalDate) {
        selected.value = if (selected.value == date) null else date
    }
}
