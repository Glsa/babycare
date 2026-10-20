@file:OptIn(ExperimentalMaterial3Api::class)

package com.babycare.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.domain.DailyKit
import com.babycare.app.ui.theme.BabyTheme

/**
 * 每日必做管理（0013）：行内删除（软删，历史打卡保留）+ 底部输入添加。
 * 无重命名：改名 = 删了再加一条。
 */
@Composable
internal fun DailyTaskManageSheet(
    tasks: List<RecordEntity>,
    onAdd: (String) -> Unit,
    onDelete: (RecordEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = BabyTheme.colors
    var input by remember { mutableStateOf("") }

    fun submit() {
        val name = input.trim()
        if (name.isEmpty()) return
        onAdd(name)
        input = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn("每日必做 · 管理") {
            if (tasks.isEmpty()) {
                Text(
                    "清单空了，下面加一条（如 💊 吃维AD）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text3,
                )
            }
            DailyKit.sortedTasks(tasks).forEach { task ->
                val name = DailyKit.taskPayload(task)?.name ?: return@forEach
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.surfaceAlt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onDelete(task) }) {
                            Text("🗑", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                placeholder = { Text("添加任务，如 🧼 洗屁屁") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth()) {
                Text("＋ 添加任务")
            }
            Text(
                "删除只影响以后的勾选，当天已勾的不受影响",
                style = MaterialTheme.typography.labelSmall,
                color = c.text3,
            )
        }
    }
}
