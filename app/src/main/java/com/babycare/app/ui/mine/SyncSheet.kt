@file:OptIn(ExperimentalMaterial3Api::class)

package com.babycare.app.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.babycare.app.sync.DeviceInfo
import com.babycare.app.sync.SyncCenter
import com.babycare.app.sync.SyncUiState
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.today.SheetColumn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 云同步弹层（0016 · FR-20）：三态——未配置（填地址）/ 未绑定（创建家庭或配对码加入）/ 已绑定（开关+状态+设备管理）。
 * 动作失败就地展示一行错误，不打断弹层。
 */
@Composable
internal fun SyncSheet(sync: SyncCenter, onDismiss: () -> Unit) {
    val config by sync.config.collectAsState()
    val state by sync.state.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn("云同步") {
            when {
                !config.configured -> UrlSection(sync)
                !config.bound -> BindSection(sync)
                else -> BoundSection(sync, config.enabled, state)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "记录数据经自家服务器在家庭成员设备间同步；关掉开关即回到纯本地，功能完整。",
                style = MaterialTheme.typography.labelSmall,
                color = BabyTheme.colors.text3,
            )
        }
    }
}

// ---- 未配置：填服务器地址 ----

@Composable
private fun UrlSection(sync: SyncCenter) {
    var url by remember { mutableStateOf("http://") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text(
        "填写同步服务器地址（自家部署的 babycare-sync 服务）",
        style = MaterialTheme.typography.bodyMedium,
        color = BabyTheme.colors.text2,
    )
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        singleLine = true,
        label = { Text("服务器地址") },
        supportingText = { Text("如 http://192.168.1.10:8080 或 https://sync.example.com") },
    )
    if (error != null) {
        Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Button(
        enabled = !busy && url.isNotBlank(),
        onClick = {
            busy = true
            scope.launch {
                error = sync.saveServerUrl(url)
                busy = false
            }
        },
    ) { Text(if (busy) "正在连接…" else "测试并保存") }
    Text(
        "内网地址（http://）以不加密方式传输，公网部署请用 https://",
        style = MaterialTheme.typography.labelSmall,
        color = BabyTheme.colors.text3,
    )
}

// ---- 未绑定：创建家庭 / 配对码加入 ----

@Composable
private fun BindSection(sync: SyncCenter) {
    var mode by remember { mutableStateOf(true) } // true = 创建家庭
    var deviceName by remember { mutableStateOf(sync.config.value.deviceName) }
    var familyName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = mode, onClick = { mode = true }, label = { Text("创建家庭") })
        FilterChip(selected = !mode, onClick = { mode = false }, label = { Text("配对码加入") })
    }
    if (mode) {
        OutlinedTextField(
            value = familyName, onValueChange = { familyName = it },
            singleLine = true, label = { Text("家庭名（如：小汤圆家）") },
        )
    } else {
        OutlinedTextField(
            value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) },
            singleLine = true, label = { Text("6 位配对码") },
        )
    }
    OutlinedTextField(
        value = deviceName, onValueChange = { deviceName = it },
        singleLine = true, label = { Text("本机名称（时间轴标注谁记的）") },
    )
    if (error != null) {
        Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Button(
        enabled = !busy && deviceName.isNotBlank() && (if (mode) familyName.isNotBlank() else code.length == 6),
        onClick = {
            busy = true
            scope.launch {
                error = if (mode) sync.createFamily(familyName, deviceName)
                else sync.joinByCode(code, deviceName)
                busy = false
            }
        },
    ) { Text(if (busy) "正在连接…" else if (mode) "创建并绑定" else "加入家庭") }
    Text(
        if (mode) "创建后可把配对码给另一台设备加入（同步页可随时刷新配对码）"
        else "配对码由已绑定的设备在同步页刷新获得",
        style = MaterialTheme.typography.labelSmall,
        color = BabyTheme.colors.text3,
    )
}

// ---- 已绑定：开关 / 状态 / 设备列表 / 配对码 / 断开 ----

@Composable
private fun BoundSection(sync: SyncCenter, enabled: Boolean, state: SyncUiState) {
    val config by sync.config.collectAsState()
    var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var devicesError by remember { mutableStateOf(false) }
    var pairingHint by remember { mutableStateOf<String?>(null) }
    var showUnbind by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(config.bound) {
        devices = runCatching { sync.deviceList() }.getOrDefault(emptyList())
        devicesError = devices.isEmpty()
    }

    ToggleRow(
        title = "同步开关",
        subtitle = if (enabled) "开着：每次记录立即上传，对端约 1 分钟内可见" else "已暂停：回到纯本地，数据保留",
        checked = enabled,
        onChecked = { sync.setEnabled(it) },
    )

    StatusCard(state, enabled, onSyncNow = { sync.kick() })

    Text("设备", style = MaterialTheme.typography.labelMedium, color = BabyTheme.colors.text3)
    if (devicesError) {
        Text("设备列表加载失败，下拉重试或稍后再来", style = MaterialTheme.typography.bodySmall, color = BabyTheme.colors.text3)
    } else {
        devices.forEach { d ->
            Surface(shape = RoundedCornerShape(12.dp), color = BabyTheme.colors.surfaceAlt, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(d.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (d.deviceId == config.serverDeviceId) {
                        Text("本机", style = MaterialTheme.typography.labelSmall, color = BabyTheme.colors.text3)
                    }
                }
            }
        }
    }

    pairingHint?.let {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            scope.launch {
                runCatching { sync.refreshPairingCode() }
                    .onSuccess { pairingHint = "新配对码：${it.code}（请在另一台设备尽快输入）" }
                    .onFailure { pairingHint = "刷新失败：${it.message ?: "未知错误"}" }
            }
        }) { Text("刷新配对码") }
        OutlinedButton(onClick = { showUnbind = true }) { Text("断开同步") }
    }

    if (showUnbind) {
        AlertDialog(
            onDismissRequest = { showUnbind = false },
            title = { Text("断开同步？") },
            text = { Text("本机数据完整保留，回到纯本地使用；其他设备不受影响。重新绑定同一家庭可继续同步。") },
            confirmButton = {
                TextButton(onClick = {
                    sync.unbind()
                    showUnbind = false
                }) { Text("断开") }
            },
            dismissButton = { TextButton(onClick = { showUnbind = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun StatusCard(state: SyncUiState, enabled: Boolean, onSyncNow: () -> Unit) {
    val c = BabyTheme.colors
    Surface(shape = RoundedCornerShape(12.dp), color = c.surfaceAlt, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val line = when {
                    !enabled -> "已暂停"
                    state.running -> "同步中…"
                    state.authExpired -> state.lastError ?: "登录已失效"
                    state.lastError != null -> state.lastError!!
                    state.pendingCount > 0 -> "待上传 ${state.pendingCount} 条"
                    state.lastOkAt != null -> "上次同步 ${fmtDateTime(state.lastOkAt)}"
                    else -> "等待同步"
                }
                Text(
                    line,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (state.lastError != null) MaterialTheme.colorScheme.error else c.text1,
                )
                if (state.lastOkAt != null && state.lastError == null) {
                    Text("游标增量 · LWW 合并 · 每次记录即推", style = MaterialTheme.typography.labelSmall, color = c.text3)
                }
            }
            if (enabled && !state.running) {
                TextButton(onClick = onSyncNow) { Text("立即同步") }
            }
        }
    }
}

private val dtFmt = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private fun fmtDateTime(millis: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(dtFmt)
