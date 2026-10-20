@file:OptIn(ExperimentalMaterial3Api::class)

package com.babycare.app.ui.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.SupplementPlanPayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.domain.TimeKit
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.today.SheetColumn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private val zone: ZoneId = ZoneId.systemDefault()

/** 常用时间点（方案表单 chips） */
private val CommonTimes = listOf("08:00", "10:00", "12:00", "15:00", "18:00", "21:00")

/** 预置补剂（FR-10：VD3 带 400IU 提示文案） */
private val Presets = listOf(
    "VD3" to "400IU",
    "AD" to "",
    "DHA" to "",
    "铁" to "",
    "钙" to "",
    "锌" to "",
)

private fun suppPlans(records: List<RecordEntity>): List<Pair<RecordEntity, SupplementPlanPayload>> =
    records.mapNotNull { r ->
        (decodePayloadOrNull(r.type, r.payload) as? SupplementPlanPayload)?.let { r to it }
    }

/** 方案列表：行内停用开关；停用的灰显置底 */
@Composable
internal fun SupplementPlanListSheet(
    planRecords: List<RecordEntity>,
    onEdit: (Pair<RecordEntity, SupplementPlanPayload>) -> Unit,
    onCreate: () -> Unit,
    onToggleStopped: (RecordEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val plans = suppPlans(planRecords).sortedBy { it.second.stopped }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn("补剂方案") {
            if (plans.isEmpty()) {
                Text(
                    "还没有方案，比如每天一粒 VD3",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BabyTheme.colors.text3,
                )
            }
            plans.forEach { (entity, p) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (p.stopped) BabyTheme.colors.surfaceAlt else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name + if (p.dose.isNotBlank()) " · ${p.dose}" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = if (p.stopped) BabyTheme.colors.text3 else BabyTheme.colors.text1,
                                textDecoration = if (p.stopped) TextDecoration.LineThrough else null,
                            )
                            Text(
                                if (p.timePoints.isEmpty()) "未设时间点" else p.timePoints.sorted().joinToString(" / "),
                                style = MaterialTheme.typography.labelSmall,
                                color = BabyTheme.colors.text3,
                            )
                        }
                        Switch(checked = !p.stopped, onCheckedChange = { onToggleStopped(entity) })
                    }
                }
            }
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("＋ 新建方案") }
        }
    }
}

/** 方案编辑：新建带预置 chips；至少 1 个时间点才能保存；编辑态可删除 */
@Composable
internal fun SupplementPlanEditSheet(
    editing: Pair<RecordEntity, SupplementPlanPayload>?,
    onSave: (name: String, dose: String, timePoints: List<String>, startDate: Long) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val existing = editing?.second
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var dose by remember { mutableStateOf(existing?.dose ?: "") }
    var times by remember { mutableStateOf(existing?.timePoints?.toSet() ?: emptySet()) }
    var startDate by remember {
        mutableStateOf(existing?.startDate?.let { TimeKit.toLocalDate(it, zone) } ?: LocalDate.now(zone))
    }
    var customTime by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun addCustomTime() {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(customTime.trim()) ?: return
        val (h, min) = m.destructured
        val hh = h.toInt(); val mm = min.toInt()
        if (hh in 0..23 && mm in 0..59) {
            times = times + "%02d:%02d".format(hh, mm)
            customTime = ""
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(if (editing == null) "新建补剂方案" else "编辑方案") {
            if (editing == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Presets.take(3).forEach { (n, d) ->
                        FilterChip(
                            selected = name == n,
                            onClick = { name = n; dose = d },
                            label = { Text(if (d.isBlank()) n else "$n·$d") },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Presets.drop(3).forEach { (n, d) ->
                        FilterChip(
                            selected = name == n,
                            onClick = { name = n; dose = d },
                            label = { Text(n) },
                        )
                    }
                    FilterChip(selected = name !in Presets.map { it.first } && name.isNotBlank(), onClick = { name = "" }, label = { Text("自定义") })
                }
                Text(
                    "每天一粒 VD3 400IU 有助钙吸收（预置提示文案）",
                    style = MaterialTheme.typography.labelSmall,
                    color = BabyTheme.colors.text3,
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("名称") },
            )
            OutlinedTextField(
                value = dose,
                onValueChange = { dose = it },
                singleLine = true,
                label = { Text("单次剂量（选填，如 400IU / 1 粒）") },
            )
            Text("每日时间点", style = MaterialTheme.typography.labelMedium, color = BabyTheme.colors.text2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommonTimes.take(4).forEach { t ->
                    FilterChip(
                        selected = t in times,
                        onClick = { times = if (t in times) times - t else times + t },
                        label = { Text(t) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommonTimes.drop(4).forEach { t ->
                    FilterChip(
                        selected = t in times,
                        onClick = { times = if (t in times) times - t else times + t },
                        label = { Text(t) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customTime,
                    onValueChange = { customTime = it },
                    singleLine = true,
                    label = { Text("自定义 HH:mm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { addCustomTime() }) { Text("添加") }
            }
            // 自定义加进来的非常用时间点也以 chip 展示，可再点掉
            (times - CommonTimes.toSet()).sorted().forEach { t ->
                FilterChip(selected = true, onClick = { times = times - t }, label = { Text("$t ✕") })
            }
            DateRow(startDate, onPick = { showDatePicker = true })
            Button(
                onClick = {
                    onSave(name.trim(), dose.trim(), times.sorted(), startDate.atStartOfDay(zone).toInstant().toEpochMilli())
                    onDismiss()
                },
                enabled = name.isNotBlank() && times.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
            if (onDelete != null) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除这个方案", color = BabyTheme.colors.text3)
                }
            }
        }
    }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        startDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state = datePickerState) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这个方案？") },
            text = { Text("已打的卡不会丢，但当日任务会消失") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete?.invoke(); onDismiss() }) {
                    Text("删除", color = BabyTheme.colors.vaccine)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}
