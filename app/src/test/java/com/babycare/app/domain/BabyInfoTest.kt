package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.BabyProfilePayload
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.encodePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BabyInfoTest {

    private fun profile(payloadJson: String, uuid: String = "b1") = RecordEntity(
        uuid = uuid, type = RecordTypes.BABY_PROFILE, payload = payloadJson,
        occurredAt = 0, updatedAt = 0, deleted = false, author = "d", babyId = uuid,
    )

    @Test
    fun parse_ok() {
        val info = profile(encodePayload(BabyProfilePayload("小汤圆", "2026-08-10", "女"))).toBabyInfo()!!
        assertEquals("小汤圆", info.nickname)
        assertEquals(LocalDate.of(2026, 8, 10), info.birthday)
        assertEquals("女", info.gender)
        assertEquals("b1", info.uuid)
    }

    @Test
    fun blankNickname_fallsBack() {
        val info = profile(encodePayload(BabyProfilePayload("", "2026-08-10"))).toBabyInfo()!!
        assertEquals("宝宝", info.nickname)
    }

    @Test
    fun badBirthday_null() {
        // 非法日期格式（旧版本/手改数据）按无档案处理，不崩
        assertNull(profile("""{"nickname":"a","birthday":"2026/08/10"}""").toBabyInfo())
    }

    @Test
    fun badJson_null() {
        assertNull(profile("not-json").toBabyInfo())
    }

    @Test
    fun wrongType_null() {
        val e = RecordEntity("u", RecordTypes.FEED_FORMULA, "{}", 0, 0, false, "a", "b")
        assertNull(e.toBabyInfo())
    }
}
