package com.babycare.app.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babycare.app.domain.AgendaEntry
import com.babycare.app.domain.Checkups
import com.babycare.app.domain.TimeKit
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.today.TypeBadge

/**
 * 行程行：完成圆钮（点按打勾/取消）+ 时间（非今天段带日期文本）+ 标题（完成后置灰划线）+ 体检徽章。
 * 今天页行程卡 / 行程 Tab / 日历未来日共用。
 */
@Composable
internal fun AgendaRow(
    entry: AgendaEntry,
    showDate: String? = null,
    container: Color = MaterialTheme.colorScheme.surface,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = entry.payload
    val c = BabyTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (p.done) c.primary else Color.Transparent)
                    .border(1.5.dp, if (p.done) c.primary else c.text3, CircleShape)
                    .clickable { onToggleDone() },
                contentAlignment = Alignment.Center,
            ) {
                if (p.done) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (showDate != null) {
                        Text(showDate, style = MaterialTheme.typography.labelMedium, color = c.text2)
                    }
                    Text(
                        TimeKit.formatHm(entry.entity.occurredAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.text2,
                    )
                    if (p.kind == Checkups.KIND) {
                        TypeBadge("体检", c.primary, c.primarySoft)
                    }
                }
                Text(
                    p.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (p.done) c.text3 else c.text1,
                    textDecoration = if (p.done) TextDecoration.LineThrough else null,
                )
            }
        }
    }
}

/** 今天页今日行程卡（FR-12）：标题 + 前 3 条 + 汇总，整卡点按跳行程 Tab；当天没行程由调用方不渲染 */
@Composable
internal fun TodayAgendaCard(
    items: List<AgendaEntry>,
    onToggleDone: (AgendaEntry) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BabyTheme.colors
    val pending = items.count { !it.payload.done }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpenAll),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "🗓️ 今日行程",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text("${items.size} 项 · 剩 $pending", style = MaterialTheme.typography.labelSmall, color = c.text2)
            }
            items.take(3).forEach { entry ->
                AgendaRow(
                    entry = entry,
                    container = c.surfaceAlt,
                    onToggleDone = { onToggleDone(entry) },
                    onClick = onOpenAll,
                )
            }
            if (items.size > 3) {
                Text("还有 ${items.size - 3} 项 · 查看全部 ›", style = MaterialTheme.typography.labelMedium, color = c.primary)
            }
        }
    }
}
