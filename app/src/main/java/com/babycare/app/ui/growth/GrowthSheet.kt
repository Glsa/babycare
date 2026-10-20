@file:OptIn(ExperimentalMaterial3Api::class)

package com.babycare.app.ui.growth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.GrowthPayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.today.SheetColumn
import com.babycare.app.ui.today.TimePointDialog
import com.babycare.app.ui.today.TimeRow
import kotlin.math.roundToInt

/**
 * 生长录入（FR-7）：三项独立，至少填一项；体重按克输入（对应 0.005kg 精度），
 * 身高/头围按厘米小数。来源在家/体检。
 */
@Composable
fun GrowthSheet(
    editing: RecordEntity?,
    onSave: (weightKg: Double?, heightCm: Double?, headCm: Double?, source: String, occurredAt: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = remember(editing) { editing?.let { decodePayloadOrNull(it.type, it.payload) as? GrowthPayload } }
    // 编辑回填：体重 kg 反算回克（6.4 → 6400）
    var weightGrams by remember {
        mutableStateOf(existing?.weightKg?.let { (it * 1000).roundToInt().takeIf { g -> g > 0 } }?.toString() ?: "")
    }
    var heightText by remember { mutableStateOf(existing?.heightCm?.let { fmtCm(it) } ?: "") }
    var headText by remember { mutableStateOf(existing?.headCm?.let { fmtCm(it) } ?: "") }
    var source by remember { mutableStateOf(existing?.source?.takeIf { it.isNotBlank() } ?: "在家") }
    var time by remember { mutableStateOf(editing?.occurredAt ?: System.currentTimeMillis()) }
    var showTimePicker by remember { mutableStateOf(false) }

    val weightKg = weightGrams.toIntOrNull()?.takeIf { it > 0 }?.let { it / 1000.0 }
    val heightCm = heightText.toDoubleOrNull()?.takeIf { it > 0 }
    val headCm = headText.toDoubleOrNull()?.takeIf { it > 0 }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(if (editing == null) "记一笔 · 生长" else "编辑 · 生长") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightGrams,
                    onValueChange = { if (it.length <= 6) weightGrams = it.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    label = { Text("体重（克）") },
                    placeholder = { Text("如 6400") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it },
                    singleLine = true,
                    label = { Text("身高 cm") },
                    placeholder = { Text("62.5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = headText,
                    onValueChange = { headText = it },
                    singleLine = true,
                    label = { Text("头围 cm") },
                    placeholder = { Text("40.8") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "三项可只填一项；体重按克称多少记多少（6400 克 = 6.4 kg）",
                style = MaterialTheme.typography.labelSmall,
                color = BabyTheme.colors.text3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("在家", "体检").forEach { s ->
                    FilterChip(selected = source == s, onClick = { source = s }, label = { Text(s) })
                }
                Text(
                    "体检量的数据选「体检」",
                    style = MaterialTheme.typography.labelSmall,
                    color = BabyTheme.colors.text3,
                )
            }
            TimeRow(time, onPick = { showTimePicker = true })
            Button(
                onClick = { onSave(weightKg, heightCm, headCm, source, time); onDismiss() },
                enabled = weightKg != null || heightCm != null || headCm != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
        }
    }
    if (showTimePicker) {
        TimePointDialog(
            initial = time,
            baseDate = null,
            onConfirm = { time = it; showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }
}

/** cm 值去掉多余的尾零（62.50 → 62.5，62.0 → 62） */
internal fun fmtCm(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/** 供成长页展示复用（体重 kg 两位小数） */
internal fun fmtKg(v: Double): String = String.format("%.2f", v)
