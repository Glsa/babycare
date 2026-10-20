package com.babycare.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babycare.app.data.model.NursingTimerState
import com.babycare.app.domain.TimeKit
import com.babycare.app.domain.TimerCalc
import com.babycare.app.ui.theme.BabyTheme

/** 进行中的睡眠卡：置顶常驻（FR-6），已睡时长随 30s 心跳刷新；取消需确认 */
@Composable
internal fun SleepOngoingCard(
    startAt: Long,
    nowMillis: Long,
    onOpenStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmCancel by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BabyTheme.colors.sleepSoft,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("😴", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "已睡 ${TimeKit.durationText((nowMillis - startAt).coerceAtLeast(0))}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BabyTheme.colors.sleep,
                    )
                    Text(
                        "${TimeKit.formatHm(startAt)} 开始",
                        style = MaterialTheme.typography.labelMedium,
                        color = BabyTheme.colors.text2,
                    )
                }
                Button(
                    onClick = onOpenStop,
                    colors = ButtonDefaults.buttonColors(containerColor = BabyTheme.colors.sleep),
                ) { Text("结束") }
            }
            TextButton(onClick = { confirmCancel = true }) {
                Text("取消这次睡眠", color = BabyTheme.colors.text3)
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("取消这次睡眠？") },
            text = { Text("这条进行中的记录会被删除") },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; onCancel() }) {
                    Text("取消睡眠", color = BabyTheme.colors.vaccine)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("保留") } },
        )
    }
}

/** 进行中的亲喂卡：左右分钟实时（累计 + 当前段）、换边/暂停/结束一击直达 */
@Composable
internal fun NursingOngoingCard(
    state: NursingTimerState,
    nowMillis: Long,
    onSwitchSide: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmCancel by remember { mutableStateOf(false) }
    val (leftMs, rightMs) = TimerCalc.currentMillis(state, nowMillis)
    val paused = state.pausedAt != null
    val activeSideText = if (state.activeSide == NursingTimerState.SIDE_LEFT) "正在左侧" else "正在右侧"

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BabyTheme.colors.nurseSoft,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤱", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "左 ${TimerCalc.minutesRounded(leftMs)} 分 · 右 ${TimerCalc.minutesRounded(rightMs)} 分",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BabyTheme.colors.nurse,
                    )
                    Text(
                        "${TimeKit.formatHm(state.startAt)} 开始 · $activeSideText${if (paused) " · 暂停中" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = BabyTheme.colors.text2,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSwitchSide, modifier = Modifier.weight(1f)) { Text("换边") }
                OutlinedButton(onClick = onTogglePause, modifier = Modifier.weight(1f)) {
                    Text(if (paused) "继续" else "暂停")
                }
                Button(
                    onClick = onStop,
                    enabled = leftMs + rightMs > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = BabyTheme.colors.nurse),
                    modifier = Modifier.weight(1f),
                ) { Text("结束") }
            }
            TextButton(onClick = { confirmCancel = true }) {
                Text("取消这次亲喂", color = BabyTheme.colors.text3)
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("取消这次亲喂？") },
            text = { Text("不会生成记录") },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; onCancel() }) {
                    Text("取消亲喂", color = BabyTheme.colors.vaccine)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("保留") } },
        )
    }
}
