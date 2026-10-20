package com.babycare.app.domain

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/** 月龄/天数推算。口径（PRD FR-1，Q-1 已确认）：出生当天 = 第 1 天；百天 = 第 100 天 = 生日 + 99 天 */
object AgeCalc {
    /** 第 N 天 */
    fun dayNumber(today: LocalDate, birthday: LocalDate): Int {
        require(!today.isBefore(birthday)) { "今天($today)早于生日($birthday)" }
        return ChronoUnit.DAYS.between(birthday, today).toInt() + 1
    }

    /** 月龄文案："2个月7天" / "1个月" / "15天" */
    fun monthAgeText(birthday: LocalDate, today: LocalDate): String {
        val p = Period.between(birthday, today)
        val months = p.years * 12 + p.months
        return when {
            months == 0 -> "${p.days}天"
            p.days == 0 -> "${months}个月"
            else -> "${months}个月${p.days}天"
        }
    }

    /** 月龄整月数（早教项目池等按月分段的场景用；年×12+月） */
    fun monthAge(birthday: LocalDate, today: LocalDate): Int {
        val p = Period.between(birthday, today)
        return p.years * 12 + p.months
    }

    /** 百天（第 100 天）所在日期 */
    fun hundredthDay(birthday: LocalDate): LocalDate = birthday.plusDays(99)
}
