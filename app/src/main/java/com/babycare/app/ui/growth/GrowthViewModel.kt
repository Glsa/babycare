package com.babycare.app.ui.growth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.repo.BabyRepository
import com.babycare.app.domain.BabyInfo
import com.babycare.app.domain.toBabyInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 成长页读流：生长 + 疫苗 + 早教打卡记录 + 宝宝档案；剂量视图由页面按生日推导（写走 TodayViewModel） */
data class GrowthUiState(
    val loaded: Boolean = false,
    val baby: BabyInfo? = null,
    val growthRecords: List<RecordEntity> = emptyList(),
    val vaccineRecords: List<RecordEntity> = emptyList(),
    val eduCheckins: List<RecordEntity> = emptyList(),
)

class GrowthViewModel(repo: BabyRepository) : ViewModel() {
    val ui: StateFlow<GrowthUiState> = combine(
        repo.observeBaby().map { it.firstOrNull()?.toBabyInfo() },
        repo.observeByType(RecordTypes.GROWTH),
        repo.observeByType(RecordTypes.VACCINE_DOSE),
        repo.observeByType(RecordTypes.EDU_CHECKIN),
    ) { baby, growth, vaccine, edus ->
        GrowthUiState(
            loaded = true, baby = baby,
            growthRecords = growth, vaccineRecords = vaccine, eduCheckins = edus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrowthUiState())
}
