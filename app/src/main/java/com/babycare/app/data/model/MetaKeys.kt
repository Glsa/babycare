package com.babycare.app.data.model

/** meta 表 key 常量 */
object MetaKeys {
    const val ACTIVE_BABY_ID = "active_baby_id"
    const val SYNC_CURSOR = "sync_cursor"

    /** 亲喂计时暂态（JSON of NursingTimerState）；睡眠进行中 = sleep 记录 endAt=null，不走 meta */
    const val ACTIVE_NURSING = "active_nursing"

    /** 每日必做默认清单预置标记（0013）：置位后删光不塞回 */
    const val DAILY_TASKS_SEEDED = "daily_tasks_seeded"

    /** 设备名映射（0016 同步）：`{服务端设备ID: 设备名}` JSON，记录人角标与设备列表用 */
    const val DEVICE_NAMES = "device_names"
}
