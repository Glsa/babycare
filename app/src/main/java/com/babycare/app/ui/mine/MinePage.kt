@file:OptIn(ExperimentalMaterial3Api::class)

package com.babycare.app.ui.mine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babycare.app.BabycareApplication
import com.babycare.app.data.backup.BackupCodec
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.BabyProfilePayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.domain.AgeCalc
import com.babycare.app.domain.FeedIntervalKit
import com.babycare.app.domain.FeedIntervalSettings
import com.babycare.app.domain.toBabyInfo
import com.babycare.app.ui.onboarding.OnboardingScreen
import com.babycare.app.ui.theme.BabyTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** 我的页（帧 07）：档案卡 / 提醒+深色 / 功能卡（导出导入+灰置）/ 危险卡 */
@Composable
fun MinePage(
    vm: MineViewModel,
    snackbar: SnackbarHostState,
    onOpenStats: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsState()
    if (ui.loading) {
        Box(modifier)
        return
    }
    val baby = ui.baby
    if (baby == null) {
        OnboardingScreen(
            onCreate = { nickname, birthday, gender -> vm.createBaby(nickname, birthday, gender) },
            modifier = modifier,
        )
        return
    }

    val app = LocalContext.current.applicationContext as BabycareApplication
    val darkOverride by app.darkOverride.collectAsState()
    val interval by app.feedInterval.collectAsState()
    val scope = rememberCoroutineScope()

    var showEdit by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<BackupCodec.ImportPlan?>(null) }
    var showWipe by remember { mutableStateOf(false) }
    var showInterval by remember { mutableStateOf(false) }
    var showReminders by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    val reminders by app.reminderSettings.collectAsState()
    val syncConfig by app.syncCenter.config.collectAsState()

    // SAF：导出建 json 文件；导入接受 json / octet-stream（部分文件管理器标后者）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = vm.exportTo(uri)
            snackbar.showSnackbar(if (ok) "已导出备份" else "导出失败，请重试")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val plan = vm.readBackup(uri)
            when {
                plan == null -> snackbar.showSnackbar("无法识别该文件")
                plan.toApply.isEmpty() -> snackbar.showSnackbar("备份里没有需要导入的数据")
                else -> pendingImport = plan
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text("我的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        BabyCard(baby, onEdit = { showEdit = true })

        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column {
                SettingRow(
                    emoji = "🔔", title = "提醒设置",
                    subtitle = if (reminders.master) "已开 ${reminders.enabledCount()}/5 项" else "总开关已关",
                    onClick = { showReminders = true },
                )
                SettingRow(
                    emoji = "🌙", title = "深色模式",
                    subtitle = if (darkOverride) "已开启" else "跟随系统",
                    onClick = { app.setDarkOverride(!darkOverride) },
                    trailing = { Switch(checked = darkOverride, onCheckedChange = { app.setDarkOverride(it) }) },
                )
                SettingRow(
                    emoji = "🍼", title = "喂奶间隔",
                    subtitle = "白天 ${FeedIntervalKit.hoursText(interval.dayMin)} · 夜晚 ${FeedIntervalKit.hoursText(interval.nightMin)}" +
                        "（${interval.dayStartHour}点起 / ${interval.nightStartHour}点起）",
                    onClick = { showInterval = true },
                )            }
        }

        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column {
                SettingRow(
                    emoji = "📊", title = "统计与生长曲线", subtitle = "奶量/睡眠趋势 · WHO 生长参考",
                    onClick = onOpenStats,
                )
                SettingRow(
                    emoji = "☁️", title = "云同步",
                    subtitle = when {
                        !syncConfig.configured -> "爸妈共记 · 需自家部署服务器"
                        !syncConfig.bound -> "已配置服务器，待创建或加入家庭"
                        syncConfig.enabled -> "已开启 · 每次记录自动上传"
                        else -> "已暂停 · 数据仅在本机"
                    },
                    onClick = { showSync = true },
                )
                SettingRow(
                    emoji = "💾", title = "导出数据", subtitle = "全部记录存为 JSON 备份",
                    onClick = { exportLauncher.launch(vm.backupFileName()) },
                )
                SettingRow(
                    emoji = "📥", title = "导入数据", subtitle = "从备份文件合并恢复",
                    onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
                )
            }
        }

        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            SettingRow(
                emoji = "🗑️", title = "清空所有数据", subtitle = "不可恢复，建议先导出备份",
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { showWipe = true },
            )
        }

        Text(
            if (syncConfig.bound && syncConfig.enabled) "🔒 记录在本机保存，并同步到你家服务器"
            else "🔒 所有数据仅保存在本机，可随时导出备份",
            style = MaterialTheme.typography.labelSmall,
            color = BabyTheme.colors.text3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            textAlign = TextAlign.Center,
        )
    }

    if (showEdit) {
        ProfileEditDialog(
            baby = baby,
            onDismiss = { showEdit = false },
            onSave = { nickname, birthday, gender, weight, height ->
                vm.saveProfile(baby, nickname, birthday, gender, weight, height)
                showEdit = false
            },
        )
    }

    if (showInterval) {
        FeedIntervalDialog(
            current = interval,
            onDismiss = { showInterval = false },
            onSave = {
                app.setFeedInterval(it)
                showInterval = false
            },
        )
    }

    if (showReminders) {
        RemindersSheet(
            settings = reminders,
            onChange = { app.setReminderSettings(it) },
            onDismiss = { showReminders = false },
        )
    }

    if (showSync) {
        SyncSheet(app.syncCenter, onDismiss = { showSync = false })
    }

    pendingImport?.let { plan ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入备份") },
            text = {
                Text("将新增 ${plan.newCount} 条、更新 ${plan.updateCount} 条记录。按更新时间合并，较新的本地记录不会被覆盖。")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = vm.applyImport(plan)
                        snackbar.showSnackbar(if (ok) "导入完成" else "导入失败，请重试")
                    }
                    pendingImport = null
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("取消") }
            },
        )
    }

    if (showWipe) {
        AlertDialog(
            onDismissRequest = { showWipe = false },
            title = { Text("清空所有数据？") },
            text = {
                Text(
                    if (syncConfig.bound && syncConfig.enabled)
                        "所有记录与档案将从本机删除，并同步清空家庭内所有设备，无法恢复。建议先导出一份备份。"
                    else "所有记录与档案将被删除，且无法恢复。建议先导出一份备份。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.wipeAll()
                    showWipe = false
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showWipe = false
                        exportLauncher.launch(vm.backupFileName())
                    }) { Text("先导出") }
                    TextButton(onClick = { showWipe = false }) { Text("取消") }
                }
            },
        )
    }
}

/** 档案卡：头像 + 昵称 + 生日·性别·第 N 天 + 编辑入口 */
@Composable
private fun BabyCard(baby: RecordEntity, onEdit: () -> Unit) {
    val info = remember(baby.uuid, baby.updatedAt) { baby.toBabyInfo() }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(BabyTheme.colors.surfaceAlt),
                contentAlignment = Alignment.Center,
            ) { Text("👶", fontSize = 28.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    info?.nickname ?: "宝宝",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                val line = info?.let {
                    listOfNotNull(
                        it.birthday.toString(),
                        it.gender.ifBlank { null },
                        "第 ${AgeCalc.dayNumber(LocalDate.now(), it.birthday)} 天",
                    ).joinToString(" · ")
                } ?: "档案数据异常，点编辑修复"
                Text(line, style = MaterialTheme.typography.bodySmall, color = BabyTheme.colors.text3)
            }
            TextButton(onClick = onEdit) { Text("编辑 ›") }
        }
    }
}

/** 统一设置行：emoji + 标题/副标题 + 尾部控件 */
@Composable
private fun SettingRow(
    emoji: String,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = BabyTheme.colors.text3)
            }
        }
        trailing()
    }
}

/** 档案编辑弹层：昵称/生日必填，性别/出生体重/身长选填（FR-1 全字段） */
@Composable
private fun ProfileEditDialog(
    baby: RecordEntity,
    onDismiss: () -> Unit,
    onSave: (nickname: String, birthday: LocalDate, gender: String, weightKg: Double?, heightCm: Double?) -> Unit,
) {
    val payload = remember(baby.uuid, baby.updatedAt) {
        (decodePayloadOrNull(baby.type, baby.payload) as? BabyProfilePayload)
    }
    var nickname by remember { mutableStateOf(payload?.nickname ?: "") }
    var birthday by remember { mutableStateOf(payload?.birthday?.let { runCatching { LocalDate.parse(it) }.getOrNull() }) }
    var gender by remember { mutableStateOf(payload?.gender ?: "") }
    var weightText by remember { mutableStateOf(payload?.birthWeightKg?.let { fmtNum(it) } ?: "") }
    var heightText by remember { mutableStateOf(payload?.birthHeightCm?.let { fmtNum(it) } ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = (birthday ?: LocalDate.now().minusMonths(1))
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= System.currentTimeMillis()
        },
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = nickname.isNotBlank() && birthday != null,
                onClick = {
                    val b = birthday ?: return@TextButton
                    onSave(
                        nickname.trim(), b, gender,
                        weightText.trim().toDoubleOrNull(),
                        heightText.trim().toDoubleOrNull(),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("编辑档案") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = nickname, onValueChange = { nickname = it },
                    singleLine = true, label = { Text("昵称（必填）") },
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
                            "生日（必填）", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val label = birthday?.let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" } ?: "请选择"
                        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = gender == "女", onClick = { gender = if (gender == "女") "" else "女" }, label = { Text("女") })
                    FilterChip(selected = gender == "男", onClick = { gender = if (gender == "男") "" else "男" }, label = { Text("男") })
                }
                OutlinedTextField(
                    value = weightText, onValueChange = { weightText = it },
                    singleLine = true, label = { Text("出生体重 kg（选填）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = heightText, onValueChange = { heightText = it },
                    singleLine = true, label = { Text("出生身长 cm（选填）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
    )

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

/** 数值展示去掉多余的尾零（3.0 → 3） */
private fun fmtNum(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/** 喂奶间隔编辑弹层：白天/夜晚间隔（小时）+ 昼夜起点（整点）。间隔按喂奶开始时刻的时段取 */
@Composable
private fun FeedIntervalDialog(
    current: FeedIntervalSettings,
    onDismiss: () -> Unit,
    onSave: (FeedIntervalSettings) -> Unit,
) {
    var dayH by remember { mutableStateOf(fmtNum(current.dayMin / 60.0)) }
    var nightH by remember { mutableStateOf(fmtNum(current.nightMin / 60.0)) }
    var dayStart by remember { mutableStateOf(current.dayStartHour.toString()) }
    var nightStart by remember { mutableStateOf(current.nightStartHour.toString()) }

    val day = dayH.toDoubleOrNull()?.takeIf { it in 0.25..24.0 }
    val night = nightH.toDoubleOrNull()?.takeIf { it in 0.25..24.0 }
    val ds = dayStart.toIntOrNull()?.takeIf { it in 0..23 }
    val ns = nightStart.toIntOrNull()?.takeIf { it in 0..23 }
    val valid = day != null && night != null && ds != null && ns != null && ds != ns

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("喂奶间隔") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "从开始喂奶起算，到点提示下一次。间隔按这顿奶发生在白天还是夜晚取。",
                    style = MaterialTheme.typography.bodySmall,
                    color = BabyTheme.colors.text3,
                )
                OutlinedTextField(
                    value = dayH, onValueChange = { dayH = it },
                    singleLine = true, label = { Text("白天间隔（小时）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = nightH, onValueChange = { nightH = it },
                    singleLine = true, label = { Text("夜晚间隔（小时）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dayStart, onValueChange = { dayStart = it },
                        singleLine = true, label = { Text("白天从几点") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = nightStart, onValueChange = { nightStart = it },
                        singleLine = true, label = { Text("夜晚从几点") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        FeedIntervalSettings(
                            dayMin = (day!! * 60).roundToInt(),
                            nightMin = (night!! * 60).roundToInt(),
                            dayStartHour = ds!!,
                            nightStartHour = ns!!,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
