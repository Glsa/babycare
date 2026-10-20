package com.babycare.app.ui.stats

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.repo.BabyRepository
import com.babycare.app.domain.BabyInfo
import com.babycare.app.domain.DayStat
import com.babycare.app.domain.StatsKit
import com.babycare.app.domain.toBabyInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** 统计页状态：宝宝 + 六类记录全量（口径沿用 0008「全量不重订阅、页面内存滤」） */
data class StatsUiState(
    val baby: BabyInfo? = null,
    val records: List<RecordEntity> = emptyList(),
)

/** 统计页 VM（0015 · FR-16） */
class StatsViewModel(
    private val repo: BabyRepository,
    private val resolver: android.content.ContentResolver,
) : ViewModel() {

    val ui: StateFlow<StatsUiState> = combine(
        repo.observeBaby().map { list -> list.firstOrNull()?.toBabyInfo() },
        // 六类记录流合并（combine 直接参数上限 5 → 走 Iterable 重载）
        combine(
            listOf(
                repo.observeByType(RecordTypes.FEED_FORMULA),
                repo.observeByType(RecordTypes.FEED_NURSING),
                repo.observeByType(RecordTypes.DIAPER),
                repo.observeByType(RecordTypes.SLEEP),
                repo.observeByType(RecordTypes.GROWTH),
                repo.observeByType(RecordTypes.EDU_CHECKIN),
            ),
        ) { arrays -> arrays.toList().flatten() },
    ) { baby, records -> StatsUiState(baby = baby, records = records) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun csvFileName(): String =
        "babycare-stats-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".csv"

    /** CSV 写到 SAF 选定位置；表头加 BOM，Excel 打开中文不乱码 */
    suspend fun exportTo(uri: Uri, stats: List<DayStat>): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = "﻿" + StatsKit.toCsv(stats)
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                ?: return@withContext false
            true
        }.getOrDefault(false)
    }
}
