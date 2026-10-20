@file:OptIn(ExperimentalMaterial3Api::class)

package com.babycare.app.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.SelectableDates
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
import androidx.compose.ui.unit.sp
import com.babycare.app.ui.theme.BabyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 首启引导：昵称 + 生日必填，性别选填（FR-1；出生体重等以后在「我的」补） */
@Composable
fun OnboardingScreen(
    onCreate: (nickname: String, birthday: LocalDate, gender: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nickname by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf<LocalDate?>(null) }
    var gender by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    // DatePicker 用 UTC 毫秒；初始给一个月前（新生儿建档最常见），不允许选未来生日
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.now().minusMonths(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= System.currentTimeMillis()
        },
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(64.dp))
        Text("👶", fontSize = 56.sp)
        Text("欢迎来到宝宝记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "先建宝宝档案——第 N 天、疫苗提醒都靠生日推算",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            singleLine = true,
            label = { Text("宝宝昵称（必填）") },
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BabyTheme.colors.surfaceAlt,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showDatePicker = true },
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "生日（必填）",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val label = birthday?.let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" } ?: "请选择"
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (birthday == null) BabyTheme.colors.text3 else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = gender == "女",
                onClick = { gender = if (gender == "女") "" else "女" },
                label = { Text("女") },
            )
            FilterChip(
                selected = gender == "男",
                onClick = { gender = if (gender == "男") "" else "男" },
                label = { Text("男") },
            )
            Text(
                "选填",
                style = MaterialTheme.typography.labelSmall,
                color = BabyTheme.colors.text3,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { birthday?.let { onCreate(nickname.trim(), it, gender) } },
            enabled = nickname.isNotBlank() && birthday != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("开始记录") }
        Spacer(Modifier.height(32.dp))
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        birthday = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
