@file:OptIn(ExperimentalMaterial3Api::class)

package com.babycare.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babycare.app.domain.DaySummary
import com.babycare.app.domain.TimeKit
import com.babycare.app.domain.TimelineItemModel
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.theme.recordColors

/** 小胶囊（soft 底 + 类型色字），第 N 天徽章 / 距上次喂奶共用 */
@Composable
internal fun TypeBadge(text: String, color: Color, soft: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(soft)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

/** 今日概要卡：四格（奶量/睡眠/尿便/洗澡），口径见 computeDaySummary */
@Composable
internal fun SummaryCard(summary: DaySummary, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCell(
                    modifier = Modifier.weight(1f),
                    emoji = "🍼",
                    value = "${summary.formulaMl} ml",
                    line1 = "瓶喂 ${summary.formulaCount} 次",
                    line2 = if (summary.nursingCount > 0) "亲喂 ${summary.nursingCount} 次 · ${summary.nursingMinutes} 分" else null,
                )
                SummaryCell(
                    modifier = Modifier.weight(1f),
                    emoji = "😴",
                    value = "${TimeKit.hoursText(summary.sleepMillis)} h",
                    line1 = "睡眠",
                    line2 = if (summary.sleepOngoing) "进行中…" else null,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCell(
                    modifier = Modifier.weight(1f),
                    emoji = "💧",
                    value = "尿${summary.peeCount} 便${summary.poopCount}",
                    line1 = "尿布",
                    line2 = null,
                )
                SummaryCell(
                    modifier = Modifier.weight(1f),
                    emoji = "🛁",
                    value = if (summary.bathDone) "已洗" else "未洗",
                    line1 = "洗澡",
                    line2 = null,
                )
            }
        }
    }
}

@Composable
private fun SummaryCell(
    modifier: Modifier,
    emoji: String,
    value: String,
    line1: String,
    line2: String?,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BabyTheme.colors.surfaceAlt)
            .padding(12.dp),
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(line1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (line2 != null) {
            Text(line2, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 快捷记录五钮：首页常驻、单手拇指可达（UI设计说明 §3.1）；计时中按钮文案联动 */
@Composable
internal fun QuickActions(
    onFormula: () -> Unit,
    onNursing: () -> Unit,
    onDiaper: () -> Unit,
    onSleep: () -> Unit,
    onBath: () -> Unit,
    nursingLabel: String = "亲喂",
    sleepLabel: String = "睡眠",
    modifier: Modifier = Modifier,
) {
    val c = BabyTheme.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickActionButton("🍼", "瓶喂", c.feed, c.feedSoft, Modifier.weight(1f), onFormula)
        QuickActionButton("🤱", nursingLabel, c.nurse, c.nurseSoft, Modifier.weight(1f), onNursing)
        QuickActionButton("💧", "尿布", c.diaper, c.diaperSoft, Modifier.weight(1f), onDiaper)
        QuickActionButton("😴", sleepLabel, c.sleep, c.sleepSoft, Modifier.weight(1f), onSleep)
        QuickActionButton("🛁", "洗澡", c.bath, c.bathSoft, Modifier.weight(1f), onBath)
    }
}

@Composable
private fun QuickActionButton(
    emoji: String,
    label: String,
    color: Color,
    soft: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(soft),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** 时间轴条目：类型色圆点 emoji + 标题 + 时间；authorLabel 返回非 null 时标题旁显示「谁记的」角标（0016）。
 *  用 lambda 让 State 读取发生在本组合作用域内——设备名映射更新时只重组单行 */
@Composable
internal fun TimelineRow(
    item: TimelineItemModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    authorLabel: (() -> String?)? = null,
) {
    val label = authorLabel?.invoke()
    val (_, soft) = recordColors(item.entity.type, BabyTheme.colors)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(soft),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.emoji, fontSize = 17.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                    if (label != null) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BabyTheme.colors.surfaceAlt,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = BabyTheme.colors.text3,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (item.subtitle.isNotBlank()) {
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                TimeKit.formatHm(item.entity.occurredAt),
                style = MaterialTheme.typography.labelMedium,
                color = BabyTheme.colors.text2,
            )
        }
    }
}

/** 弹层内容骨架：标题 + 垂直排布，底部留出手势区 */
@Composable
internal fun SheetColumn(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        content()
    }
}

/** 弹层里的时间行：点开自定义时间选择 */
@Composable
internal fun TimeRow(timeMillis: Long, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BabyTheme.colors.surfaceAlt,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPick),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("时间", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(TimeKit.formatHm(timeMillis), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

/** 分钟步进器（亲喂左右分钟数用） */
@Composable
internal fun MinuteStepper(label: String, minutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { onChange((minutes - 5).coerceAtLeast(0)) }) { Text("−5") }
        TextButton(onClick = { onChange((minutes - 1).coerceAtLeast(0)) }) { Text("−1") }
        Text(
            "$minutes 分",
            Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        TextButton(onClick = { onChange((minutes + 1).coerceAtMost(180)) }) { Text("+1") }
        TextButton(onClick = { onChange((minutes + 5).coerceAtMost(180)) }) { Text("+5") }
    }
}
