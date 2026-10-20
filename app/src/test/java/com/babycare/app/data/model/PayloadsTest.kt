package com.babycare.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadsTest {

    /** 覆盖全部 14 种类型的往返序列化（防 when 分支漏接） */
    @Test
    fun roundtrip_allTypes() {
        val samples: List<Pair<String, RecordPayload>> = listOf(
            RecordTypes.BABY_PROFILE to BabyProfilePayload("小汤圆", "2026-08-10", "女", 3.2, 50.0),
            RecordTypes.FEED_FORMULA to FeedFormulaPayload(120),
            RecordTypes.FEED_NURSING to FeedNursingPayload(leftMinutes = 8, rightMinutes = 10),
            RecordTypes.DIAPER to DiaperPayload(pee = true, poop = true),
            RecordTypes.BATH to BathPayload(),
            RecordTypes.SLEEP to SleepPayload(startAt = 1_000L, endAt = null, tag = "夜觉"),
            RecordTypes.GROWTH to GrowthPayload(weightKg = 6.4, heightCm = 62.5, headCm = 40.8),
            RecordTypes.EDU_CHECKIN to EduCheckinPayload("视觉", "黑白卡追视", 5),
            RecordTypes.VACCINE_DOSE to VaccineDosePayload("乙肝疫苗", 1, date = 1_000L),
            RecordTypes.SUPPLEMENT_PLAN to SupplementPlanPayload("维生素D3", "400IU", listOf("09:00"), 1_000L),
            RecordTypes.SUPPLEMENT_LOG to SupplementLogPayload("维生素D3", 2_000L, "09:00"),
            RecordTypes.AGENDA to AgendaPayload("社区体检", "checkup", 0, true, "带疫苗本"),
            RecordTypes.DAILY_TASK to DailyTaskPayload("💊 吃维AD", 0),
            RecordTypes.DAILY_TASK_LOG to DailyTaskLogPayload("t1", "2026-10-18"),
        )
        for ((type, payload) in samples) {
            assertEquals("类型 $type 往返不一致", payload, decodePayload(type, encodePayload(payload)))
        }
    }

    /** 前向兼容：新版本加了字段，旧版本解码不崩、能读的照读 */
    @Test
    fun decode_ignoresUnknownKeys() {
        val json = """{"ml":90,"note":"","futureField":123}"""
        assertEquals(FeedFormulaPayload(ml = 90, note = ""), decodePayload(RecordTypes.FEED_FORMULA, json))
    }

    @Test
    fun decodeOrNull_toleratesUnknownTypeAndBadJson() {
        assertNull(decodePayloadOrNull("new_type_x", "{}"))
        assertNull(decodePayloadOrNull(RecordTypes.FEED_FORMULA, "not-json"))
    }

    /** encodeDefaults=true：默认值也编码，同步侧新旧版本往返无损 */
    @Test
    fun defaults_areEncoded() {
        val json = encodePayload(DiaperPayload(pee = true, poop = false))
        assertTrue(json.contains("\"note\""))
    }
}
