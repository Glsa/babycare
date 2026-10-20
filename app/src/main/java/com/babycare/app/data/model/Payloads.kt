package com.babycare.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 12 种记录类型的 payload 定义。
 * 兼容策略：解码忽略未知字段（旧版本读新数据不崩）；默认值也编码（同步往返无损）。
 */
val RecordJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** 类型化 payload 的共同接口，便于 when 分发 */
interface RecordPayload

@Serializable
data class BabyProfilePayload(
    val nickname: String,
    /** ISO 日期（如 2026-08-10），全 App 推算基础 */
    val birthday: String,
    /** 女 / 男 / 留空 */
    val gender: String = "",
    val birthWeightKg: Double? = null,
    val birthHeightCm: Double? = null,
) : RecordPayload

@Serializable
data class FeedFormulaPayload(
    val ml: Int,
    /** 奶种（0012 瓶喂二分）：KIND_FORMULA 配方奶 / KIND_BREAST 吸出母乳装瓶；旧记录无此字段 = 奶粉 */
    val kind: String = KIND_FORMULA,
    val note: String = "",
) : RecordPayload {
    companion object {
        const val KIND_FORMULA = "formula"
        const val KIND_BREAST = "breast"
    }
}

@Serializable
data class FeedNursingPayload(
    val leftMinutes: Int = 0,
    val rightMinutes: Int = 0,
    val note: String = "",
) : RecordPayload

@Serializable
data class DiaperPayload(
    val pee: Boolean,
    val poop: Boolean,
    val note: String = "",
) : RecordPayload

@Serializable
data class BathPayload(
    val note: String = "",
) : RecordPayload

@Serializable
data class SleepPayload(
    val startAt: Long,
    /** null = 进行中（计时器未结束） */
    val endAt: Long? = null,
    /** 夜觉 / 小睡：19:00-07:00 结束的自动判夜觉，可手动改 */
    val tag: String = "",
    val note: String = "",
) : RecordPayload

@Serializable
data class GrowthPayload(
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val headCm: Double? = null,
    /** 在家 / 体检 */
    val source: String = "在家",
) : RecordPayload

@Serializable
data class EduCheckinPayload(
    /** 视觉 / 听觉 / 大动作 / 精细动作 */
    val category: String,
    val item: String,
    val minutes: Int? = null,
) : RecordPayload

@Serializable
data class VaccineDosePayload(
    val vaccine: String,
    /** 第几剂 */
    val dose: Int,
    /** 实际或计划接种日 ms */
    val date: Long,
    val location: String = "",
    val note: String = "",
    /** 对应 Vaccines 计划的稳定标识（如 b1 / dtap3）；空 = 旧数据按疫苗名+剂次兜底 */
    val key: String = "",
    /** done = 已种 / skipped = 不接种（0007） */
    val status: String = "done",
) : RecordPayload

@Serializable
data class SupplementPlanPayload(
    val name: String,
    /** 单次剂量描述，如 400IU */
    val dose: String = "",
    /** 每日时间点，如 ["09:00","21:00"] */
    val timePoints: List<String> = emptyList(),
    val startDate: Long,
    val stopped: Boolean = false,
) : RecordPayload

@Serializable
data class SupplementLogPayload(
    val name: String,
    /** 实际服用 ms */
    val takenAt: Long,
    /** 对应方案时间点（核对漏服用） */
    val planTime: String = "",
) : RecordPayload

@Serializable
data class AgendaPayload(
    val title: String,
    /** vaccine / checkup / custom */
    val kind: String = "custom",
    /** 提前量（天），0 = 当天 */
    val remindDaysBefore: Int = 0,
    val done: Boolean = false,
    val note: String = "",
) : RecordPayload

/** 每日必做任务定义（0013）：name 自带 emoji 前缀（预置），自定义随用户输入 */
@Serializable
data class DailyTaskPayload(
    val name: String,
    val sortOrder: Int = 0,
) : RecordPayload

/** 每日必做当日打卡：dateKey = yyyy-MM-dd；取消 = 软删该条 */
@Serializable
data class DailyTaskLogPayload(
    val taskUuid: String,
    val dateKey: String,
) : RecordPayload

/** 编码：按具体类型走对应序列化器 */
fun encodePayload(payload: RecordPayload): String = when (payload) {
    is BabyProfilePayload -> RecordJson.encodeToString(payload)
    is FeedFormulaPayload -> RecordJson.encodeToString(payload)
    is FeedNursingPayload -> RecordJson.encodeToString(payload)
    is DiaperPayload -> RecordJson.encodeToString(payload)
    is BathPayload -> RecordJson.encodeToString(payload)
    is SleepPayload -> RecordJson.encodeToString(payload)
    is GrowthPayload -> RecordJson.encodeToString(payload)
    is EduCheckinPayload -> RecordJson.encodeToString(payload)
    is VaccineDosePayload -> RecordJson.encodeToString(payload)
    is SupplementPlanPayload -> RecordJson.encodeToString(payload)
    is SupplementLogPayload -> RecordJson.encodeToString(payload)
    is AgendaPayload -> RecordJson.encodeToString(payload)
    is DailyTaskPayload -> RecordJson.encodeToString(payload)
    is DailyTaskLogPayload -> RecordJson.encodeToString(payload)
    else -> throw IllegalArgumentException("未知 payload 类型: ${payload::class}")
}

/** 解码：未知类型抛 IllegalArgumentException */
fun decodePayload(type: String, json: String): RecordPayload = when (type) {
    RecordTypes.BABY_PROFILE -> RecordJson.decodeFromString<BabyProfilePayload>(json)
    RecordTypes.FEED_FORMULA -> RecordJson.decodeFromString<FeedFormulaPayload>(json)
    RecordTypes.FEED_NURSING -> RecordJson.decodeFromString<FeedNursingPayload>(json)
    RecordTypes.DIAPER -> RecordJson.decodeFromString<DiaperPayload>(json)
    RecordTypes.BATH -> RecordJson.decodeFromString<BathPayload>(json)
    RecordTypes.SLEEP -> RecordJson.decodeFromString<SleepPayload>(json)
    RecordTypes.GROWTH -> RecordJson.decodeFromString<GrowthPayload>(json)
    RecordTypes.EDU_CHECKIN -> RecordJson.decodeFromString<EduCheckinPayload>(json)
    RecordTypes.VACCINE_DOSE -> RecordJson.decodeFromString<VaccineDosePayload>(json)
    RecordTypes.SUPPLEMENT_PLAN -> RecordJson.decodeFromString<SupplementPlanPayload>(json)
    RecordTypes.SUPPLEMENT_LOG -> RecordJson.decodeFromString<SupplementLogPayload>(json)
    RecordTypes.AGENDA -> RecordJson.decodeFromString<AgendaPayload>(json)
    RecordTypes.DAILY_TASK -> RecordJson.decodeFromString<DailyTaskPayload>(json)
    RecordTypes.DAILY_TASK_LOG -> RecordJson.decodeFromString<DailyTaskLogPayload>(json)
    else -> throw IllegalArgumentException("未知记录类型: $type")
}

/** 宽松解码：未知类型/坏 JSON 返回 null（新版本设备产生的新类型在旧设备上跳过即可，不崩） */
fun decodePayloadOrNull(type: String, json: String): RecordPayload? = try {
    decodePayload(type, json)
} catch (_: Exception) {
    null
}
