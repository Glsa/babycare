@file:OptIn(ExperimentalMaterial3Api::class)

package com.babycare.app.ui.edu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.EduCheckinPayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.today.SheetColumn
import com.babycare.app.ui.today.TimePointDialog
import com.babycare.app.ui.today.TimeRow

/**
 * 早教打卡编辑（FR-8 时长选填）：类目/项目带出不可改（打卡入口在卡片行与成长页）；
 * 这里只改时长与时间。新建打卡走卡片一键，不经此表单。
 */
@Composable
fun EduSheet(
    editing: RecordEntity,
    onSave: (minutes: Int?, occurredAt: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = remember(editing) { decodePayloadOrNull(editing.type, editing.payload) as? EduCheckinPayload }
    var minutesText by remember { mutableStateOf(existing?.minutes?.toString() ?: "") }
    var time by remember { mutableStateOf(editing.occurredAt) }
    var showTimePicker by remember { mutableStateOf(false) }
    val minutes = minutesText.toIntOrNull()?.takeIf { it > 0 }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn("编辑 · 早教打卡") {
            Text(
                "🎨 ${existing?.item ?: "早教"}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = BabyTheme.colors.edu,
            )
            Text(
                existing?.category ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = BabyTheme.colors.text2,
            )
            OutlinedTextField(
                value = minutesText,
                onValueChange = { if (it.length <= 3) minutesText = it.filter { ch -> ch.isDigit() } },
                singleLine = true,
                label = { Text("时长（分钟，选填）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            TimeRow(time, onPick = { showTimePicker = true })
            Button(
                onClick = { onSave(minutes, time); onDismiss() },
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
