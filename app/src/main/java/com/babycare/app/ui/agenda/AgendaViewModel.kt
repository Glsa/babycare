package com.babycare.app.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.repo.BabyRepository
import com.babycare.app.domain.AgendaBoard
import com.babycare.app.domain.AgendaKit
import com.babycare.app.domain.BabyInfo
import com.babycare.app.domain.toBabyInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/** 行程页读流（0008 扩展）：行程分区 + 补剂方案/log + 疫苗/早教记录；写走 TodayViewModel */
data class AgendaUiState(
    val loaded: Boolean = false,
    val baby: BabyInfo? = null,
    val board: AgendaBoard = AgendaBoard(),
    val planRecords: List<RecordEntity> = emptyList(),
    val supplementLogs: List<RecordEntity> = emptyList(),
    val vaccineRecords: List<RecordEntity> = emptyList(),
    val eduCheckins: List<RecordEntity> = emptyList(),
)

/** today 在每次数据变化时定格（跨午夜不重算，与日历页同口径） */
class AgendaViewModel(repo: BabyRepository) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    val ui: StateFlow<AgendaUiState> = combine(
        repo.observeBaby().map { it.firstOrNull()?.toBabyInfo() },
        repo.observeAgendas(),
        // 补剂三流打包后并进外层（combine 类型化重载只到 5 路）
        combine(
            repo.observeByType(RecordTypes.SUPPLEMENT_PLAN),
            repo.observeByType(RecordTypes.SUPPLEMENT_LOG),
            repo.observeByType(RecordTypes.VACCINE_DOSE),
        ) { plans, logs, vaccines -> Triple(plans, logs, vaccines) },
        repo.observeByType(RecordTypes.EDU_CHECKIN),
    ) { baby, agendas, extras, edus ->
        val today = LocalDate.now(zone)
        AgendaUiState(
            loaded = true, baby = baby,
            board = AgendaKit.groupAgendas(agendas, today, zone),
            planRecords = extras.first,
            supplementLogs = extras.second,
            vaccineRecords = extras.third,
            eduCheckins = edus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUiState())
}
