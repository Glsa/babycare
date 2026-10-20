@file:OptIn(ExperimentalMaterial3Api::class)

package com.babycare.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.BathPayload
import com.babycare.app.data.model.DiaperPayload
import com.babycare.app.data.model.FeedFormulaPayload
import com.babycare.app.data.model.FeedNursingPayload
import com.babycare.app.data.model.GrowthPayload
import com.babycare.app.data.model.NursingTimerState
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.SleepPayload
import com.babycare.app.data.model.VaccineDosePayload
import com.babycare.app.data.model.decodePayloadOrNull
import com.babycare.app.domain.TimeKit
import com.babycare.app.domain.TimerCalc
import com.babycare.app.domain.TimelineItemModel
import com.babycare.app.ui.agenda.AgendaSheet
import com.babycare.app.ui.edu.EduSheet
import com.babycare.app.ui.growth.GrowthSheet
import com.babycare.app.ui.growth.VaccineSheet
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.theme.recordColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 当前打开的弹层；编辑 = 同一弹层带 editing 实体回填（单值状态机，开新必关旧） */
sealed interface RecordSheet {
    data class Formula(val editing: RecordEntity?) : RecordSheet
    data class Nursing(val editing: RecordEntity?) : RecordSheet
    data class Diaper(val editing: RecordEntity?) : RecordSheet
    data class Sleep(val editing: RecordEntity?) : RecordSheet
    data class Bath(val editing: RecordEntity) : RecordSheet
    data class Agenda(val editing: RecordEntity?) : RecordSheet
    data class Growth(val editing: RecordEntity?) : RecordSheet
    /** 疫苗只有编辑路径（时间轴/成长页已有记录时）；标已种/不接种从成长页带计划预填直开 VaccineSheet */
    data class Vaccine(val editing: RecordEntity) : RecordSheet
    /** 早教打卡只有编辑路径（改时长/时间）；打卡入口在今日早教卡与成长页 */
    data class Edu(val editing: RecordEntity) : RecordSheet
    data class Detail(val item: TimelineItemModel) : RecordSheet

    // 计时（0004）：计时中状态由 VM 流实时驱动，弹层只做交互壳
    data object NursingStart : RecordSheet
    data object NursingTimer : RecordSheet
    data class StopSleep(val entity: RecordEntity) : RecordSheet

    companion object {
        /** Widget 深链键 → 新建弹层；bath 特殊（开 App 即记不弹层，由 TodayPage 处理） */
        fun fromWidgetKey(key: String): RecordSheet? = when (key) {
            "formula" -> Formula(null)
            "nursing" -> NursingStart
            "diaper" -> Diaper(null)
            "growth" -> Growth(null)
            else -> null
        }
    }
}

/** 可创建的类型才可编辑；其余（早教/补剂等后续 feature 的类型）详情里只读+删除 */
private fun TimelineItemModel.editableSheet(): RecordSheet? = when (entity.type) {
    RecordTypes.FEED_FORMULA -> RecordSheet.Formula(entity)
    RecordTypes.FEED_NURSING -> RecordSheet.Nursing(entity)
    RecordTypes.DIAPER -> RecordSheet.Diaper(entity)
    RecordTypes.SLEEP -> RecordSheet.Sleep(entity)
    RecordTypes.BATH -> RecordSheet.Bath(entity)
    RecordTypes.AGENDA -> RecordSheet.Agenda(entity)
    RecordTypes.GROWTH -> RecordSheet.Growth(entity)
    RecordTypes.VACCINE_DOSE -> RecordSheet.Vaccine(entity)
    RecordTypes.EDU_CHECKIN -> RecordSheet.Edu(entity)
    else -> null
}

@Composable
fun RecordSheetHost(
    sheet: RecordSheet?,
    vm: TodayViewModel,
    onDismiss: () -> Unit,
    onEdit: (RecordSheet) -> Unit,
    defaultTime: Long? = null,
) {
    // 日历补录：默认时间所在日固定进时间选择（design 0005 §4），今天页不传 = 现行为（今天/昨天 chips）
    val baseDate = remember(defaultTime) { defaultTime?.let { TimeKit.toLocalDate(it) } }
    when (sheet) {
        is RecordSheet.Formula -> FormulaSheet(
            editing = sheet.editing,
            defaultTime = defaultTime,
            baseDate = baseDate,
            onSave = { ml, kind, at, note ->
                val e = sheet.editing
                if (e == null) vm.saveFormula(ml, kind, at, note) else vm.updateFormula(e, ml, kind, at, note)
            },
            onDismiss = onDismiss,
        )

        is RecordSheet.Nursing -> NursingSheet(
            editing = sheet.editing,
            defaultTime = defaultTime,
            baseDate = baseDate,
            onSave = { left, right, at, note ->
                val e = sheet.editing
                if (e == null) vm.saveNursing(left, right, at, note) else vm.updateNursing(e, left, right, at, note)
            },
            onDismiss = onDismiss,
        )

        is RecordSheet.Diaper -> DiaperSheet(
            editing = sheet.editing,
            defaultTime = defaultTime,
            baseDate = baseDate,
            onSave = { pee, poop, at ->
                val e = sheet.editing
                if (e == null) vm.saveDiaper(pee, poop, at, "") else vm.updateDiaper(e, pee, poop, at, "")
            },
            onDismiss = onDismiss,
        )

        is RecordSheet.Sleep -> SleepSheet(
            editing = sheet.editing,
            defaultTime = defaultTime,
            baseDate = baseDate,
            onSave = { start, end, tag, note ->
                val e = sheet.editing
                if (e == null) vm.saveSleep(start, end, tag, note) else vm.updateSleep(e, start, end, tag, note)
            },
            onDismiss = onDismiss,
        )

        is RecordSheet.Bath -> BathSheet(
            editing = sheet.editing,
            onSave = { at, note -> vm.updateBath(sheet.editing, at, note) },
            onDismiss = onDismiss,
        )

        is RecordSheet.Agenda -> AgendaSheet(
            editing = sheet.editing,
            onSave = { title, kind, at, note, remind ->
                val e = sheet.editing
                if (e == null) vm.saveAgenda(title, kind, at, note, remind)
                else vm.updateAgenda(e, title, kind, at, note, remind)
            },
            onDismiss = onDismiss,
        )

        is RecordSheet.Growth -> GrowthSheet(
            editing = sheet.editing,
            onSave = { weightKg, heightCm, headCm, source, at ->
                val e = sheet.editing
                if (e == null) vm.saveGrowth(weightKg, heightCm, headCm, source, at)
                else vm.updateGrowth(e, weightKg, heightCm, headCm, source, at)
            },
            onDismiss = onDismiss,
        )

        is RecordSheet.Vaccine -> {
            // 名称剂次从 payload 带出（不可改）；删除 = 撤销接种/不接种
            val p = remember(sheet.editing) {
                decodePayloadOrNull(sheet.editing.type, sheet.editing.payload) as? VaccineDosePayload
            }
            if (p != null) VaccineSheet(
                vaccine = p.vaccine,
                dose = p.dose,
                editing = sheet.editing,
                onDone = { date, location, note -> vm.updateVaccineDose(sheet.editing, date, location, note) },
                onSkip = null,
                onDelete = { vm.delete(sheet.editing) },
                onDismiss = onDismiss,
            )
        }

        is RecordSheet.Edu -> EduSheet(
            editing = sheet.editing,
            onSave = { minutes, at -> vm.updateEduCheckin(sheet.editing, minutes, at) },
            onDismiss = onDismiss,
        )

        is RecordSheet.Detail -> DetailSheet(
            item = sheet.item,
            onDelete = {
                vm.delete(sheet.item.entity)
                onDismiss()
            },
            onEdit = { sheet.item.editableSheet()?.let(onEdit) },
            onDismiss = onDismiss,
        )

        is RecordSheet.NursingStart -> NursingStartSheet(
            onStart = { side -> vm.startNursing(side); onDismiss() },
            onManual = { onEdit(RecordSheet.Nursing(null)) },
            onDismiss = onDismiss,
        )

        is RecordSheet.NursingTimer -> NursingTimerSheet(vm = vm, onDismiss = onDismiss)

        is RecordSheet.StopSleep -> StopSleepSheet(
            entity = sheet.entity,
            onSave = { endAt, tag, note -> vm.stopSleep(endAt, tag, note); onDismiss() },
            onCancel = { vm.cancelSleep(); onDismiss() },
            onDismiss = onDismiss,
        )

        null -> Unit
    }
}

// ---- 瓶喂（奶粉 / 母乳，0012） ----

@Composable
private fun FormulaSheet(
    editing: RecordEntity?,
    defaultTime: Long? = null,
    baseDate: LocalDate? = null,
    onSave: (ml: Int, kind: String, occurredAt: Long, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = remember(editing) { editing?.let { decodePayloadOrNull(it.type, it.payload) as? FeedFormulaPayload } }
    var ml by remember { mutableIntStateOf(existing?.ml ?: 150) }
    var kind by remember { mutableStateOf(existing?.kind ?: FeedFormulaPayload.KIND_FORMULA) }
    var time by remember { mutableStateOf(editing?.occurredAt ?: defaultTime ?: System.currentTimeMillis()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(if (editing == null) "记一笔 · 瓶喂" else "编辑 · 瓶喂") {
            // 奶种：旧记录无 kind 回填为奶粉
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kind == FeedFormulaPayload.KIND_FORMULA,
                    onClick = { kind = FeedFormulaPayload.KIND_FORMULA },
                    label = { Text("🍼 奶粉") },
                )
                FilterChip(
                    selected = kind == FeedFormulaPayload.KIND_BREAST,
                    onClick = { kind = FeedFormulaPayload.KIND_BREAST },
                    label = { Text("🥛 母乳") },
                )
            }
            Text(
                "$ml ml",
                style = MaterialTheme.typography.headlineLarge,
                color = BabyTheme.colors.feed,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90, 120, 150, 180).forEach { v ->
                    FilterChip(
                        selected = ml == v,
                        onClick = { ml = v },
                        label = { Text("$v") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-30, -10, 10, 30).forEach { d ->
                    OutlinedButton(
                        onClick = { ml = (ml + d).coerceIn(10, 500) },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (d > 0) "+$d" else "$d") }
                }
            }
            TimeRow(time, onPick = { showTimePicker = true })
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                singleLine = true,
                label = { Text("备注（选填）") },
            )
            Button(
                onClick = { onSave(ml, kind, time, note); onDismiss() },
                enabled = ml > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
        }
    }
    if (showTimePicker) {
        TimePointDialog(
            initial = time,
            baseDate = baseDate,
            onConfirm = { time = it; showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }
}

// ---- 尿布 ----

@Composable
private fun DiaperSheet(
    editing: RecordEntity?,
    defaultTime: Long? = null,
    baseDate: LocalDate? = null,
    onSave: (pee: Boolean, poop: Boolean, at: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var time by remember { mutableStateOf(editing?.occurredAt ?: defaultTime ?: System.currentTimeMillis()) }
    var showTimePicker by remember { mutableStateOf(false) }
    val c = BabyTheme.colors

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(if (editing == null) "记一笔 · 换尿布" else "编辑 · 换尿布") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiaperOption("小便", "💦", c.diaper, c.diaperSoft, Modifier.weight(1f)) { onSave(true, false, time); onDismiss() }
                DiaperOption("大便", "💩", c.diaper, c.diaperSoft, Modifier.weight(1f)) { onSave(false, true, time); onDismiss() }
                DiaperOption("都有", "💧", c.diaper, c.diaperSoft, Modifier.weight(1f)) { onSave(true, true, time); onDismiss() }
            }
            TimeRow(time, onPick = { showTimePicker = true })
            Text(
                if (editing == null) "选类型即保存" else "选类型保存修改",
                style = MaterialTheme.typography.labelSmall,
                color = BabyTheme.colors.text3,
            )
        }
    }
    if (showTimePicker) {
        TimePointDialog(
            initial = time,
            baseDate = baseDate,
            onConfirm = { time = it; showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun DiaperOption(
    label: String,
    emoji: String,
    color: Color,
    soft: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(soft)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontWeight = FontWeight.Medium)
    }
}

// ---- 亲喂（手动补录；计时器走 NursingStart / NursingTimer） ----

@Composable
private fun NursingSheet(
    editing: RecordEntity?,
    defaultTime: Long? = null,
    baseDate: LocalDate? = null,
    onSave: (leftMinutes: Int, rightMinutes: Int, occurredAt: Long, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = remember(editing) { editing?.let { decodePayloadOrNull(it.type, it.payload) as? FeedNursingPayload } }
    var left by remember { mutableIntStateOf(existing?.leftMinutes ?: 0) }
    var right by remember { mutableIntStateOf(existing?.rightMinutes ?: 0) }
    var time by remember { mutableStateOf(editing?.occurredAt ?: defaultTime ?: System.currentTimeMillis()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(if (editing == null) "记一笔 · 亲喂" else "编辑 · 亲喂") {
            MinuteStepper("左侧", left, { left = it })
            MinuteStepper("右侧", right, { right = it })
            TimeRow(time, onPick = { showTimePicker = true })
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                singleLine = true,
                label = { Text("备注（选填）") },
            )
            Button(
                onClick = { onSave(left, right, time, note); onDismiss() },
                enabled = left + right > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
        }
    }
    if (showTimePicker) {
        TimePointDialog(
            initial = time,
            baseDate = baseDate,
            onConfirm = { time = it; showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }
}

// ---- 睡眠（手动补录；计时器开始/结束走今天页卡片与 StopSleepSheet） ----

@Composable
private fun SleepSheet(
    editing: RecordEntity?,
    defaultTime: Long? = null,
    baseDate: LocalDate? = null,
    onSave: (startAt: Long, endAt: Long, tag: String, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = remember(editing) { editing?.let { decodePayloadOrNull(it.type, it.payload) as? SleepPayload } }
    val zone = remember { ZoneId.systemDefault() }
    var endAt by remember { mutableStateOf(existing?.endAt ?: defaultTime ?: System.currentTimeMillis()) }
    var minutes by remember {
        mutableIntStateOf(
            existing?.let { p -> ((p.endAt ?: System.currentTimeMillis()) - p.startAt) / 60_000 }?.toInt() ?: 90
        )
    }
    var tag by remember {
        mutableStateOf(
            existing?.tag?.takeIf { it.isNotBlank() }
                ?: TimeKit.sleepTag(Instant.ofEpochMilli(defaultTime ?: System.currentTimeMillis()).atZone(zone).hour)
        )
    }
    var customMinutes by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var showTimePicker by remember { mutableStateOf(false) }
    val startAt = endAt - minutes * 60_000L

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(if (editing == null) "记一笔 · 睡眠" else "编辑 · 睡眠") {
            Text(
                "${TimeKit.formatHm(startAt)}  →  ${TimeKit.formatHm(endAt)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BabyTheme.colors.sleep,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 45, 60, 90, 120, 180).forEach { v ->
                    FilterChip(
                        selected = minutes == v,
                        onClick = { minutes = v; customMinutes = "" },
                        label = { Text(if (v >= 60) "${v / 60}h${if (v % 60 != 0) "半" else ""}" else "${v}分") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            OutlinedTextField(
                value = customMinutes,
                onValueChange = {
                    customMinutes = it
                    it.toIntOrNull()?.let { m -> if (m in 1..24 * 60) minutes = m }
                },
                singleLine = true,
                label = { Text("自定义分钟数") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("夜觉", "小睡").forEach { t ->
                    FilterChip(
                        selected = tag == t,
                        onClick = { tag = t },
                        label = { Text(t) },
                    )
                }
                Text(
                    "19:00–07:00 结束的记夜觉",
                    style = MaterialTheme.typography.labelSmall,
                    color = BabyTheme.colors.text3,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            TimeRow(endAt, onPick = { showTimePicker = true })
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                singleLine = true,
                label = { Text("备注（选填）") },
            )
            Button(
                onClick = { onSave(startAt, endAt, tag, note); onDismiss() },
                enabled = minutes > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
        }
    }
    if (showTimePicker) {
        TimePointDialog(
            initial = endAt,
            baseDate = baseDate,
            onConfirm = { endAt = it; showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }
}

// ---- 亲喂计时（0004） ----

/** 选边开始（1 击开始路径）；手动补录入口保留 */
@Composable
private fun NursingStartSheet(
    onStart: (side: String) -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = BabyTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn("亲喂计时") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiaperOption("左侧开始", "👈", c.nurse, c.nurseSoft, Modifier.weight(1f)) { onStart(NursingTimerState.SIDE_LEFT) }
                DiaperOption("右侧开始", "👉", c.nurse, c.nurseSoft, Modifier.weight(1f)) { onStart(NursingTimerState.SIDE_RIGHT) }
            }
            Text(
                "选边即开始计时，之后可换边 / 暂停 / 结束",
                style = MaterialTheme.typography.labelSmall,
                color = BabyTheme.colors.text3,
            )
            TextButton(onClick = onManual) { Text("手动补录（直接填分钟数）") }
        }
    }
}

/** 亲喂计时中（帧 03）：左右实时大字 + 暂停/换边/结束三钮 */
@Composable
private fun NursingTimerSheet(vm: TodayViewModel, onDismiss: () -> Unit) {
    val state by vm.ui.collectAsState()
    val st = state.nursingTimer
    if (st == null) {
        // 已被别处结束（通知按钮/另一张卡），弹层自动收起
        LaunchedEffect(st) { onDismiss() }
        return
    }
    val (leftMs, rightMs) = TimerCalc.currentMillis(st, state.nowMillis)
    val paused = st.pausedAt != null
    var confirmCancel by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn("亲喂计时中") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimerMinuteCell("左侧", leftMs, Modifier.weight(1f))
                TimerMinuteCell("右侧", rightMs, Modifier.weight(1f))
            }
            Text(
                "${TimeKit.formatHm(st.startAt)} 开始 · ${
                    if (st.activeSide == NursingTimerState.SIDE_LEFT) "正在左侧" else "正在右侧"
                }${if (paused) " · 暂停中" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = BabyTheme.colors.text2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.switchNursingSide() }, modifier = Modifier.weight(1f)) { Text("换边") }
                OutlinedButton(onClick = { vm.toggleNursingPause() }, modifier = Modifier.weight(1f)) {
                    Text(if (paused) "继续" else "暂停")
                }
            }
            Button(
                onClick = { vm.stopNursing(); onDismiss() },
                enabled = leftMs + rightMs > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("结束并保存") }
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
                TextButton(onClick = { confirmCancel = false; vm.cancelNursing(); onDismiss() }) {
                    Text("取消亲喂", color = BabyTheme.colors.vaccine)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("保留") } },
        )
    }
}

@Composable
private fun TimerMinuteCell(label: String, millis: Long, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BabyTheme.colors.nurseSoft)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = BabyTheme.colors.text2)
        Spacer(Modifier.height(4.dp))
        Text(
            "${TimerCalc.minutesRounded(millis)} 分",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = BabyTheme.colors.nurse,
        )
    }
}

// ---- 结束睡眠（计时器路径） ----

@Composable
private fun StopSleepSheet(
    entity: RecordEntity,
    onSave: (endAt: Long, tag: String, note: String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = remember(entity) { decodePayloadOrNull(entity.type, entity.payload) as? SleepPayload }
    val startAt = existing?.startAt ?: entity.occurredAt
    val zone = remember { ZoneId.systemDefault() }
    var endAt by remember { mutableStateOf(System.currentTimeMillis()) }
    // 标签默认按结束小时自动判；用户手动改过就不再跟着结束时间变
    var tagTouched by remember { mutableStateOf(false) }
    var tag by remember {
        mutableStateOf(TimeKit.sleepTag(Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).hour))
    }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn("结束睡眠") {
            Text(
                "${TimeKit.formatHm(startAt)} 开始  →  ${TimeKit.formatHm(endAt)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BabyTheme.colors.sleep,
            )
            Text(
                "已睡 ${TimeKit.durationText((endAt - startAt).coerceAtLeast(0))}",
                style = MaterialTheme.typography.labelMedium,
                color = BabyTheme.colors.text2,
            )
            TimeRow(endAt, onPick = { showTimePicker = true })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("夜觉", "小睡").forEach { t ->
                    FilterChip(
                        selected = tag == t,
                        onClick = { tag = t; tagTouched = true },
                        label = { Text(t) },
                    )
                }
                Text(
                    "19:00–07:00 结束的记夜觉",
                    style = MaterialTheme.typography.labelSmall,
                    color = BabyTheme.colors.text3,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                singleLine = true,
                label = { Text("备注（选填）") },
            )
            Button(
                onClick = { onSave(endAt, tag, note) },
                enabled = endAt > startAt,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
            TextButton(onClick = { confirmCancel = true }) {
                Text("取消这次睡眠", color = BabyTheme.colors.text3)
            }
        }
    }
    if (showTimePicker) {
        TimePointDialog(
            initial = endAt,
            onConfirm = {
                endAt = it
                if (!tagTouched) tag = TimeKit.sleepTag(Instant.ofEpochMilli(it).atZone(zone).hour)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
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

// ---- 洗澡（编辑时间/备注用；新建走首页一键） ----

@Composable
private fun BathSheet(
    editing: RecordEntity,
    onSave: (occurredAt: Long, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = remember(editing) { decodePayloadOrNull(editing.type, editing.payload) as? BathPayload }
    var time by remember { mutableStateOf(editing.occurredAt) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn("编辑 · 洗澡") {
            TimeRow(time, onPick = { showTimePicker = true })
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                singleLine = true,
                label = { Text("备注（选填，如湿疹用药）") },
            )
            Button(
                onClick = { onSave(time, note); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
        }
    }
    if (showTimePicker) {
        TimePointDialog(
            initial = time,
            baseDate = null,   // 洗澡只有编辑入口，日期取记录值
            onConfirm = { time = it; showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }
}

// ---- 详情 / 删除 ----

@Composable
private fun DetailSheet(
    item: TimelineItemModel,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val (color, soft) = recordColors(item.entity.type, BabyTheme.colors)
    val editable = item.editableSheet() != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn("记录详情") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(soft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.emoji, fontSize = 24.sp)
                }
                Column {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        TimeKit.formatHm(item.entity.occurredAt),
                        color = color,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (item.subtitle.isNotBlank()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f),
                ) { Text("删除", color = BabyTheme.colors.vaccine) }
                Button(
                    onClick = onEdit,
                    enabled = editable,
                    modifier = Modifier.weight(1f),
                ) { Text(if (editable) "编辑" else "暂不支持编辑") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条记录？") },
            text = { Text(item.title) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除", color = BabyTheme.colors.vaccine) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

// ---- 自定义时间选择（TimePicker + 今天/昨天） ----

/**
 * baseDate = null：现行为，今天/昨天 chips（夜奶补录最常用）
 * baseDate 非 null：固定该日期（日历补录，改时间不会误落到今天），chips 换成日期文本
 */
@Composable
internal fun TimePointDialog(initial: Long, baseDate: LocalDate? = null, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val initialTime = remember(initial) { Instant.ofEpochMilli(initial).atZone(zone) }
    val pickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )
    // 0 = 今天，-1 = 昨天（夜奶补录最常见）
    var dayOffset by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("补录时间") },
        text = {
            Column {
                if (baseDate == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = dayOffset == 0, onClick = { dayOffset = 0 }, label = { Text("今天") })
                        FilterChip(selected = dayOffset == -1, onClick = { dayOffset = -1 }, label = { Text("昨天") })
                    }
                } else {
                    Text(
                        TimeKit.formatMonthDay(baseDate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val date = baseDate ?: LocalDate.now(zone).plusDays(dayOffset.toLong())
                onConfirm(date.atTime(pickerState.hour, pickerState.minute).atZone(zone).toInstant().toEpochMilli())
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
