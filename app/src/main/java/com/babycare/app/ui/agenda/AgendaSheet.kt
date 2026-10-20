@file:OptIn(ExperimentalMaterial3Api::class)

package com.babycare.app.ui.agenda

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.AgendaPayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.domain.Checkups
import com.babycare.app.domain.TimeKit
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.today.SheetColumn
import com.babycare.app.ui.today.TimePointDialog
import com.babycare.app.ui.today.TimeRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** 行程新建/编辑（design 0006 §7）：标题 + 类型 + 日期 + 时间 + 提前提醒 + 备注；新建默认明天 10:00 */
@Composable
internal fun AgendaSheet(
    editing: RecordEntity?,
    onSave: (title: String, kind: String, occurredAt: Long, note: String, remindDaysBefore: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = remember(editing) { editing?.let { decodePayloadOrNull(it.type, it.payload) as? AgendaPayload } }
    val zone = remember { ZoneId.systemDefault() }
    val defaultDate = remember { LocalDate.now(zone).plusDays(1) }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var kind by remember { mutableStateOf(existing?.kind?.takeIf { it == Checkups.KIND } ?: "custom") }
    var date by remember { mutableStateOf(editing?.let { TimeKit.toLocalDate(it.occurredAt, zone) } ?: defaultDate) }
    var time by remember {
        mutableStateOf(editing?.occurredAt ?: defaultDate.atTime(10, 0).atZone(zone).toInstant().toEpochMilli())
    }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var remind by remember { mutableStateOf(existing?.remindDaysBefore ?: 0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    // 时间只取时分；落在哪天由日期行决定（TimePointDialog 的 baseDate 也用它固定）
    val t = remember(time) { Instant.ofEpochMilli(time).atZone(zone) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(if (editing == null) "记一笔 · 行程" else "编辑 · 行程") {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("标题（必填，如 3月龄儿保）") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kind == Checkups.KIND,
                    onClick = { kind = Checkups.KIND },
                    label = { Text("体检") },
                )
                FilterChip(
                    selected = kind == "custom",
                    onClick = { kind = "custom" },
                    label = { Text("自定义") },
                )
                Text(
                    "疫苗类型随疫苗功能开放",
                    style = MaterialTheme.typography.labelSmall,
                    color = BabyTheme.colors.text3,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            DateRow(date = date, onPick = { showDatePicker = true })
            TimeRow(time, onPick = { showTimePicker = true })
            // 提前提醒（0014 · FR-13）：默认当天 9:00 通知
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "提前提醒",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(0 to "当天", 1 to "前 1 天", 3 to "前 3 天").forEach { (d, label) ->
                    FilterChip(
                        selected = remind == d,
                        onClick = { remind = d },
                        label = { Text(label) },
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                singleLine = true,
                label = { Text("备注（选填，如带疫苗本）") },
            )
            Button(
                onClick = {
                    val at = date.atTime(t.hour, t.minute).atZone(zone).toInstant().toEpochMilli()
                    onSave(title.trim(), kind, at, note, remind)
                    onDismiss()
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
    if (showTimePicker) {
        TimePointDialog(
            initial = time,
            baseDate = date,
            onConfirm = { time = it; showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }
}

/** 日期行：样式与 TimeRow 一致，值是日期而非时分 */
@Composable
internal fun DateRow(date: LocalDate, onPick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BabyTheme.colors.surfaceAlt,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPick),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "日期",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text(
                "${date.year}年${date.monthValue}月${date.dayOfMonth}日 · ${TimeKit.weekdayText(date)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
