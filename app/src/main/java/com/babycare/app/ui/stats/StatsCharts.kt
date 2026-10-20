package com.babycare.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.babycare.app.domain.GrowthMetric
import com.babycare.app.domain.WhoGrowth
import com.babycare.app.ui.theme.BabyTheme
import java.time.LocalDate
import java.util.Locale

/** 统计页卡片壳（与今天页卡片同规格） */
@Composable
internal fun StatsCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = BabyTheme.colors.text2)
                }
            }
            content()
        }
    }
}

/**
 * 日粒度柱状图（0015）：系列 1 主柱、系列 2 可选并列柱；无数据画空态。
 * x 轴标签自动抽稀（>8 个时约每 5 个取 1）；具体数字看卡头合计行。
 */
@Composable
internal fun BarChart(
    values1: List<Float>,
    values2: List<Float>?,
    labels: List<String>,
    tint1: Color,
    tint2: Color? = null,
    emptyText: String = "还没有记录",
) {
    val text3 = BabyTheme.colors.text3
    val divider = BabyTheme.colors.divider
    val v2max = values2?.max() ?: 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (values1.isEmpty() || (values1.max() <= 0f && v2max <= 0f)) {
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = text3)
            }
        } else {
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                val maxV = maxOf(values1.max(), v2max).coerceAtLeast(1f)
                val gap = 4.dp.toPx()
                val slot = size.width / values1.size
                val barCount = if (values2 == null) 1 else 2
                val barW = ((slot - gap) / barCount).coerceAtMost(10.dp.toPx())
                values1.forEachIndexed { i, v ->
                    val left = i * slot + (slot - barW * barCount - gap * (barCount - 1)) / 2
                    if (v > 0f) {
                        val h = (v / maxV) * size.height
                        drawRoundRect(
                            color = tint1,
                            topLeft = Offset(left, size.height - h),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                    val v2 = values2?.getOrNull(i) ?: 0f
                    if (v2 > 0f && tint2 != null) {
                        val h2 = (v2 / maxV) * size.height
                        drawRoundRect(
                            color = tint2,
                            topLeft = Offset(left + barW + gap, size.height - h2),
                            size = Size(barW, h2),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                }
                drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            // x 轴标签抽稀
            val step = if (labels.size > 8) (labels.size + 4) / 5 else 1
            Row(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, l ->
                    Text(
                        l,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i % step == 0) text3 else Color.Transparent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 生长曲线（0015）：x = 月龄 0–24（超出钳制），WHO P3–P97 参考带 + P50 虚线 + 用户折线。
 * y 轴范围 = 参考带与用户点的并集；刻度标签在 Canvas 外排版（免 TextMeasurer）。
 */
@Composable
internal fun GrowthChart(
    points: List<Pair<Double, Double>>,
    gender: String?,
    metric: GrowthMetric,
) {
    val text3 = BabyTheme.colors.text3
    val growth = BabyTheme.colors.growth
    val growthSoft = BabyTheme.colors.growthSoft
    val primary = BabyTheme.colors.primary
    val divider = BabyTheme.colors.divider

    val refYs = (0..24 step 1).flatMap { m ->
        listOf(
            WhoGrowth.value(gender, metric, 3, m.toDouble()),
            WhoGrowth.value(gender, metric, 97, m.toDouble()),
        )
    }.filterNotNull()
    val ys = refYs + points.map { it.second }
    // 兜底：性别未知且无数据点（还没建档案）→ 空坐标不崩
    val yMin = if (ys.isEmpty()) 0.0 else kotlin.math.floor(ys.min())
    val yMax = if (ys.isEmpty()) 1.0 else kotlin.math.ceil(ys.max())
    val ySpan = (yMax - yMin).coerceAtLeast(1.0)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.height(180.dp)) {
            Column(
                Modifier.size(width = 34.dp, height = 180.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(fmt(yMax), style = MaterialTheme.typography.labelSmall, color = text3)
                Text(fmt(yMin + ySpan / 2), style = MaterialTheme.typography.labelSmall, color = text3)
                Text(fmt(yMin), style = MaterialTheme.typography.labelSmall, color = text3)
            }
            Canvas(Modifier.weight(1f).height(180.dp)) {
                val x: (Double) -> Float = { m -> (m.coerceIn(0.0, 24.0) / 24.0 * size.width).toFloat() }
                val y: (Double) -> Float = { v -> (size.height * (1 - (v - yMin) / ySpan)).toFloat() }

                // 参考带 P3–P97（性别未知 → value 全 null，带自动不画）
                if (gender != null) {
                    val band = Path()
                    var m = 0.0
                    while (m <= 24.0) {
                        val v = WhoGrowth.value(gender, metric, 97, m)
                        if (v != null) {
                            if (m == 0.0) band.moveTo(x(m), y(v)) else band.lineTo(x(m), y(v))
                        }
                        m += 0.5
                    }
                    m = 24.0
                    while (m >= 0.0) {
                        val v = WhoGrowth.value(gender, metric, 3, m)
                        if (v != null) band.lineTo(x(m), y(v))
                        m -= 0.5
                    }
                    band.close()
                    drawPath(band, growthSoft, alpha = 0.45f)

                    // P50 虚线
                    val p50 = Path()
                    var mm = 0.0
                    while (mm <= 24.0) {
                        val v = WhoGrowth.value(gender, metric, 50, mm)
                        if (v != null) {
                            if (mm == 0.0) p50.moveTo(x(mm), y(v)) else p50.lineTo(x(mm), y(v))
                        }
                        mm += 0.5
                    }
                    drawPath(
                        p50, growth,
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                    )
                }

                // 用户折线与点
                if (points.isNotEmpty()) {
                    val line = Path()
                    points.forEachIndexed { i, (month, v) ->
                        if (i == 0) line.moveTo(x(month), y(v)) else line.lineTo(x(month), y(v))
                    }
                    drawPath(line, primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    points.forEach { (month, v) ->
                        drawCircle(primary, radius = 4.dp.toPx(), center = Offset(x(month), y(v)))
                        drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(x(month), y(v)))
                    }
                }

                drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("0", "6", "12", "18", "24").forEach {
                Text(
                    it, style = MaterialTheme.typography.labelSmall, color = text3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 早教打卡热图（0015）：近 N 周 × 7 行，色深 = 当日打卡次数（周一为行首，未来日期留白） */
@Composable
internal fun EduHeatMap(counts: Map<LocalDate, Int>, weeks: Int = 12, today: LocalDate) {
    val edu = BabyTheme.colors.edu
    val surfaceAlt = BabyTheme.colors.surfaceAlt
    val text3 = BabyTheme.colors.text3
    val firstMonday = today.minusDays(((today.dayOfWeek.value + 6) % 7 + (weeks - 1) * 7).toLong())
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0..6) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0 until weeks) {
                    val d = firstMonday.plusDays((col * 7 + row).toLong())
                    val c = counts[d] ?: 0
                    val alpha = when {
                        d.isAfter(today) -> 0f
                        c >= 3 -> 1f
                        c == 2 -> 0.7f
                        c == 1 -> 0.45f
                        else -> 0f
                    }
                    Box(
                        Modifier
                            .size(13.dp)
                            .background(
                                color = if (alpha > 0f) edu.copy(alpha = alpha) else surfaceAlt,
                                shape = RoundedCornerShape(3.dp),
                            ),
                    )
                }
            }
        }
        Text("近 $weeks 周 · 颜色越深打卡越多", style = MaterialTheme.typography.labelSmall, color = text3)
    }
}

private fun fmt(v: Double): String =
    if (kotlin.math.abs(v - kotlin.math.round(v)) < 1e-9) v.toInt().toString()
    else String.format(Locale.US, "%.1f", v)
