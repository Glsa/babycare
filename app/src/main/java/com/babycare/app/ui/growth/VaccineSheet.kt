@file:OptIn(ExperimentalMaterial3Api::class)

package com.babycare.app.ui.growth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.VaccineDosePayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.domain.TimeKit
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.today.SheetColumn
import com.babycare.app.ui.today.TimePointDialog
import com.babycare.app.ui.today.TimeRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 疫苗剂次录入（FR-9）：名称剂次自动带出不可改；接种日期必填默认今天。
 * plan 路径（未种）：保存已种 / 不接种；编辑路径：保存 / 删除（删除 = 撤销，状态回到待种或未到月龄）。
 */
@Composable
fun VaccineSheet(
    vaccine: String,
    dose: Int,
    editing: RecordEntity?,
    planDate: Long? = null,
    onDone: (date: Long, location: String, note: String) -> Unit,
    onSkip: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val existing = remember(editing) { editing?.let { decodePayloadOrNull(it.type, it.payload) as? VaccineDosePayload } }
    val zone = remember { ZoneId.systemDefault() }
    // 默认现在（标已种通常发生在接种当天）；编辑带原值
    var time by remember { mutableStateOf(existing?.date ?: System.currentTimeMillis()) }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val date = remember(time, zone) { TimeKit.toLocalDate(time, zone) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(if (editing == null) "记录接种" else "编辑接种") {
            Text(
                "$vaccine · 第${dose}剂",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = BabyTheme.colors.vaccine,
            )
            planDate?.let {
                Text(
                    "计划接种日 ${TimeKit.formatMonthDay(TimeKit.toLocalDate(it, zone))}，晚种不影响后续剂次",
                    style = MaterialTheme.typography.labelSmall,
                    color = BabyTheme.colors.text3,
                )
            }
            DateRow(date, onPick = { showDatePicker = true })
            TimeRow(time, onPick = { showTimePicker = true })
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                singleLine = true,
                label = { Text("接种点（选填）") },
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                singleLine = true,
                label = { Text("备注 / 批号（选填）") },
            )
            Button(
                onClick = { onDone(time, location, note); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (editing == null) "记录已种" else "保存") }
            if (onSkip != null) {
                OutlinedButton(onClick = { onSkip?.invoke(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("这剂不接种", color = BabyTheme.colors.text2)
                }
            }
            if (onDelete != null) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除这条接种记录", color = BabyTheme.colors.text3)
                }
            }
        }
    }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val picked = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        val oldTime = Instant.ofEpochMilli(time).atZone(zone)
                        // 换日期保留原时分
                        time = picked.atTime(oldTime.hour, oldTime.minute).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state = datePickerState) }
    }
    if (showTimePicker) {
        TimePointDialog(
            initial = time,
            baseDate = date,
            onConfirm = { time = it; showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条接种记录？") },
            text = { Text("$vaccine 第${dose}剂会回到未接种状态") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete?.invoke(); onDismiss() }) {
                    Text("删除", color = BabyTheme.colors.vaccine)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

/** 日期行：📅 10月18日 周日，点击换日期 */
@Composable
private fun DateRow(date: LocalDate, onPick: () -> Unit) {
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("📅", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${TimeKit.formatMonthDay(date)} ${TimeKit.weekdayText(date)}",
                style = MaterialTheme.typography.bodyMedium,
                color = BabyTheme.colors.text1,
            )
        }
    }
}
