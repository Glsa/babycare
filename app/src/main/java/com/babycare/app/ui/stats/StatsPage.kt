package com.babycare.app.ui.stats

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.babycare.app.BabycareApplication
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.domain.GrowthMetric
import com.babycare.app.domain.StatsKit
import com.babycare.app.ui.theme.BabyTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 时间范围（0015）：全部档 = 从最早记录到今天 */
private enum class Range(val days: Int?, val label: String) {
    WEEK(7, "近 7 天"),
    MONTH(30, "近 30 天"),
    ALL(null, "全部"),
}

/** 统计页（0015 · FR-16）：全屏层，由我的页进入；返回键/左上角返回 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BabycareApplication
    val vm: StatsViewModel = viewModel(factory = viewModelFactory {
        initializer { StatsViewModel(app.repository, app.contentResolver) }
    })
    val state by vm.ui.collectAsState()
    val zone = remember { ZoneId.systemDefault() }
    var range by remember { mutableStateOf(Range.WEEK) }
    var metric by remember { mutableStateOf(GrowthMetric.WEIGHT) }
    val scope = rememberCoroutineScope()

    val today = remember { LocalDate.now(zone) }
    val stats = remember(state.records, range, today) {
        StatsKit.dailyStats(state.records, range.days, zone, today, System.currentTimeMillis())
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = vm.exportTo(uri, stats)
            Toast.makeText(context, if (ok) "已导出 CSV" else "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = { exportLauncher.launch(vm.csvFileName()) }) {
                        Text("⬇️")
                    }
                },
            )
        },
    ) { padding ->
        val labels = stats.map { "${it.date.monthValue}/${it.date.dayOfMonth}" }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Range.entries.forEach { r ->
                        FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label) })
                    }
                }
            }
            item {
                val ml = stats.sumOf { it.formulaMl }
                val min = stats.sumOf { it.nursingMinutes }
                StatsCard("🍼 喂养", "共 $ml ml · 亲喂 $min 分") {
                    BarChart(
                        values1 = stats.map { it.formulaMl.toFloat() },
                        values2 = stats.map { it.nursingMinutes.toFloat() },
                        labels = labels,
                        tint1 = BabyTheme.colors.feed,
                        tint2 = BabyTheme.colors.nurse,
                        emptyText = "还没有喂养记录",
                    )
                    Text("■ 瓶喂 ml　■ 亲喂 分钟", style = MaterialTheme.typography.labelSmall, color = BabyTheme.colors.text3)
                }
            }
            item {
                val pee = stats.sumOf { it.peeCount }
                val poop = stats.sumOf { it.poopCount }
                StatsCard("🧷 尿布", "尿 $pee · 便 $poop") {
                    BarChart(
                        values1 = stats.map { it.peeCount.toFloat() },
                        values2 = stats.map { it.poopCount.toFloat() },
                        labels = labels,
                        tint1 = BabyTheme.colors.diaper,
                        tint2 = BabyTheme.colors.bath,
                        emptyText = "还没有尿布记录",
                    )
                    Text("■ 尿　■ 便（次数）", style = MaterialTheme.typography.labelSmall, color = BabyTheme.colors.text3)
                }
            }
            item {
                val sleepH = stats.sumOf { it.sleepMillis } / 3_600_000.0
                val nightH = stats.sumOf { it.nightMillis } / 3_600_000.0
                val nightPct = if (sleepH > 0) (nightH / sleepH * 100).toInt() else 0
                StatsCard("😴 睡眠", "共 %.1f h · 夜觉 $nightPct%%".format(sleepH)) {
                    BarChart(
                        values1 = stats.map { it.sleepMillis / 3_600_000f },
                        values2 = null,
                        labels = labels,
                        tint1 = BabyTheme.colors.sleep,
                        emptyText = "还没有睡眠记录",
                    )
                    Text("■ 当日总时长（小时，含进行中）", style = MaterialTheme.typography.labelSmall, color = BabyTheme.colors.text3)
                }
            }
            item {
                val points = remember(state.records, metric, state.baby) {
                    state.baby?.let { StatsKit.growthPoints(state.records, it.birthday, metric, zone) } ?: emptyList()
                }
                StatsCard("🌱 生长曲线", "${metric.label}（${metric.unit}）") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GrowthMetric.entries.forEach { m ->
                            FilterChip(selected = metric == m, onClick = { metric = m }, label = { Text(m.label) })
                        }
                    }
                    GrowthChart(points = points, gender = state.baby?.gender, metric = metric)
                    Text(
                        "参考带为 WHO P3–P97 近似值（虚线 P50），儿保评估以标准生长曲线图为准",
                        style = MaterialTheme.typography.labelSmall,
                        color = BabyTheme.colors.text3,
                    )
                }
            }
            item {
                val heat = remember(state.records) {
                    state.records.filter { it.type == RecordTypes.EDU_CHECKIN }
                        .groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
                        .mapValues { (_, v) -> v.size }
                }
                StatsCard("🎓 早教打卡", "近 12 周") {
                    EduHeatMap(counts = heat, weeks = 12, today = today)
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp)) } // 底部留白
        }
    }
}
