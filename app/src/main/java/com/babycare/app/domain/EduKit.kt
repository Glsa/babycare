package com.babycare.app.domain

import java.time.LocalDate

/** 早教库项目：四类 × 月龄段（0-1/2-3/4-5/6-7/8-9/10-12） */
data class EduItem(
    /** 视觉 / 听觉 / 大动作 / 精细动作 */
    val category: String,
    val title: String,
    val minMonth: Int,
    val maxMonth: Int,
)

/**
 * 早教项目库 + 今日推荐（FR-8）。
 * 库来自 PRD 附录 B 的完整细化（设计阶段定稿，不可变常量——推荐算法依赖顺序稳定）。
 */
object EduKit {

    const val CAT_VISUAL = "视觉"
    const val CAT_AUDIO = "听觉"
    const val CAT_MOTOR = "大动作"
    const val CAT_FINE = "精细动作"

    val items: List<EduItem> = listOf(
        // 0~1 月
        EduItem(CAT_VISUAL, "看人脸", 0, 1),
        EduItem(CAT_VISUAL, "红球追视", 0, 1),
        EduItem(CAT_AUDIO, "和宝宝说话", 0, 1),
        EduItem(CAT_AUDIO, "白噪音安抚", 0, 1),
        EduItem(CAT_MOTOR, "俯卧抬头片刻", 0, 1),
        EduItem(CAT_FINE, "抓握手指", 0, 1),
        // 2~3 月
        EduItem(CAT_VISUAL, "黑白卡追视", 2, 3),
        EduItem(CAT_VISUAL, "彩色卡追视", 2, 3),
        EduItem(CAT_AUDIO, "摇铃寻声", 2, 3),
        EduItem(CAT_MOTOR, "抬头 45°~90°", 2, 3),
        EduItem(CAT_FINE, "拍打够取玩具", 2, 3),
        // 4~5 月
        EduItem(CAT_VISUAL, "照镜子认脸", 4, 5),
        EduItem(CAT_AUDIO, "叫名反应", 4, 5),
        EduItem(CAT_AUDIO, "节奏儿歌", 4, 5),
        EduItem(CAT_MOTOR, "翻身练习", 4, 5),
        EduItem(CAT_FINE, "抓握积木", 4, 5),
        EduItem(CAT_FINE, "双手传递", 4, 5),
        // 6~7 月
        EduItem(CAT_VISUAL, "躲猫猫", 6, 7),
        EduItem(CAT_AUDIO, "拟声发音 ba/ma", 6, 7),
        EduItem(CAT_MOTOR, "独坐练习", 6, 7),
        EduItem(CAT_FINE, "换手、耙抓小物", 6, 7),
        // 8~9 月
        EduItem(CAT_VISUAL, "指认常见物品", 8, 9),
        EduItem(CAT_AUDIO, "模仿发音", 8, 9),
        EduItem(CAT_MOTOR, "爬行", 8, 9),
        EduItem(CAT_FINE, "拇指食指对捏", 8, 9),
        // 10~12 月
        EduItem(CAT_VISUAL, "绘本共读、指认图画", 10, 12),
        EduItem(CAT_AUDIO, "有意识叫爸妈", 10, 12),
        EduItem(CAT_MOTOR, "扶站、扶走", 10, 12),
        EduItem(CAT_FINE, "放物入容器、涂鸦", 10, 12),
    )

    /** 该月龄可选项目（月龄超库范围 → 空池，界面整卡隐藏） */
    fun pool(monthAge: Int): List<EduItem> = items.filter { monthAge in it.minMonth..it.maxMonth }

    /**
     * 今日推荐（Q4）：确定性——同一天永远同结果（epochDay 轮转起点 + 条数按日轮换），
     * 优先排除昨天打过卡的项目；排除后不够条数允许与昨天重复（池子小的兜底）。
     */
    fun picks(pool: List<EduItem>, date: LocalDate, yesterdayTitles: Set<String>): List<EduItem> {
        if (pool.isEmpty()) return emptyList()
        val seed = date.toEpochDay().toInt()
        val count = 2 + (seed % 2)   // 2~3 项按日轮换
        val offset = seed % pool.size
        val rotated = pool.drop(offset) + pool.take(offset)
        val fresh = rotated.filter { it.title !in yesterdayTitles }
        return (if (fresh.size >= count) fresh else rotated).take(count)
    }
}
