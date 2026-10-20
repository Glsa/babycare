package com.babycare.app.domain

import java.time.LocalDate

/** 体检自动行程（PRD 附录 C）：42 天 + 3/6/8/12/18/24/30/36 月龄。生成逻辑在 TodayViewModel（uuid 幂等） */
object Checkups {

    const val KIND = "checkup"

    private data class Spec(val key: String, val title: String, val date: (LocalDate) -> LocalDate)

    private val specs = listOf(
        Spec("42d", "42天体检") { it.plusDays(41) },   // 出生当天 = 第 1 天 → 第 42 天 = +41
        Spec("3m", "3月龄体检") { it.plusMonths(3) },
        Spec("6m", "6月龄体检") { it.plusMonths(6) },
        Spec("8m", "8月龄体检") { it.plusMonths(8) },
        Spec("12m", "12月龄体检") { it.plusMonths(12) },
        Spec("18m", "18月龄体检") { it.plusMonths(18) },
        Spec("24m", "24月龄体检") { it.plusMonths(24) },
        Spec("30m", "30月龄体检") { it.plusMonths(30) },
        Spec("36m", "36月龄体检") { it.plusMonths(36) },
    )

    /** 体检节点（纯函数）：确定性 uuid「checkup-{节点}-{babyId}」→ 生成幂等、软删不补回 */
    data class CheckupPlan(val uuid: String, val date: LocalDate, val title: String)

    fun plans(birthday: LocalDate, babyId: String): List<CheckupPlan> =
        specs.map { s -> CheckupPlan("checkup-${s.key}-$babyId", s.date(birthday), s.title) }
}
