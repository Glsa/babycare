package com.babycare.app.data.model

/** 记录类型常量：与后端 records.type 同一命名空间（后端只校验格式不枚举，schema 客户端自治） */
object RecordTypes {
    const val BABY_PROFILE = "baby_profile"
    const val FEED_FORMULA = "feed_formula"
    const val FEED_NURSING = "feed_nursing"
    const val DIAPER = "diaper"
    const val BATH = "bath"
    const val SLEEP = "sleep"
    const val GROWTH = "growth"
    const val EDU_CHECKIN = "edu_checkin"
    const val VACCINE_DOSE = "vaccine_dose"
    const val SUPPLEMENT_PLAN = "supplement_plan"
    const val SUPPLEMENT_LOG = "supplement_log"
    const val AGENDA = "agenda"
    const val DAILY_TASK = "daily_task"
    const val DAILY_TASK_LOG = "daily_task_log"

    val ALL: Set<String> = setOf(
        BABY_PROFILE, FEED_FORMULA, FEED_NURSING, DIAPER, BATH, SLEEP, GROWTH,
        EDU_CHECKIN, VACCINE_DOSE, SUPPLEMENT_PLAN, SUPPLEMENT_LOG, AGENDA,
        DAILY_TASK, DAILY_TASK_LOG,
    )
}
