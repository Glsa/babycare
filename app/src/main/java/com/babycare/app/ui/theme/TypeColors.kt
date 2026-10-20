package com.babycare.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.babycare.app.data.model.RecordTypes

/** 记录类型 → (类型色, soft 底)。新增记录类型时记得补映射，否则落到 text2 灰 */
fun recordColors(type: String, c: BabyColors): Pair<Color, Color> = when (type) {
    RecordTypes.FEED_FORMULA -> c.feed to c.feedSoft
    RecordTypes.FEED_NURSING -> c.nurse to c.nurseSoft
    RecordTypes.DIAPER -> c.diaper to c.diaperSoft
    RecordTypes.SLEEP -> c.sleep to c.sleepSoft
    RecordTypes.BATH -> c.bath to c.bathSoft
    RecordTypes.GROWTH -> c.growth to c.growthSoft
    RecordTypes.EDU_CHECKIN -> c.edu to c.eduSoft
    RecordTypes.VACCINE_DOSE -> c.vaccine to c.vaccineSoft
    RecordTypes.SUPPLEMENT_PLAN, RecordTypes.SUPPLEMENT_LOG -> c.primary to c.primarySoft
    RecordTypes.AGENDA -> c.primary to c.primarySoft
    else -> c.text2 to c.surfaceAlt
}
