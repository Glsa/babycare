package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.decodePayloadOrNull
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** 宝宝档案（展示用）。解析失败（坏数据/生日非法）返回 null，页面按无档案处理 */
data class BabyInfo(
    val uuid: String,
    val nickname: String,
    val birthday: LocalDate,
    val gender: String,
)

fun RecordEntity.toBabyInfo(): BabyInfo? {
    if (type != RecordTypes.BABY_PROFILE) return null
    val p = decodePayloadOrNull(type, payload) as? com.babycare.app.data.model.BabyProfilePayload ?: return null
    val birthday = try {
        LocalDate.parse(p.birthday)
    } catch (_: DateTimeParseException) {
        return null
    }
    return BabyInfo(
        uuid = uuid,
        nickname = p.nickname.ifBlank { "宝宝" },
        birthday = birthday,
        gender = p.gender,
    )
}
