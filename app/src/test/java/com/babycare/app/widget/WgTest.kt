package com.babycare.app.widget

import com.babycare.app.domain.FeedIntervalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Widget 倒计时文案（0011 增量）：与 App badge 同源 FeedIntervalKit，仅文案更短 */
class WgTest {

    private val zone = ZoneOffset.UTC
    private val s = FeedIntervalSettings()

    private fun at(hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 10, 18, hour, minute).toInstant(zone).toEpochMilli()

    @Test
    fun `没喂过奶不显示`() {
        assertNull(Wg.feedCountdownText(null, at(10), s, zone))
    }

    @Test
    fun `倒计时中`() {
        // 8:00 喂（白天 180 分），9:30 剩 90 分 → 1:30
        assertEquals("距下次 1:30" to false, Wg.feedCountdownText(at(8), at(9, 30), s, zone))
    }

    @Test
    fun `超时红显`() {
        // 8:00 喂 180 分，11:10 已超 10 分
        assertEquals("该喂奶了 · 超 0:10" to true, Wg.feedCountdownText(at(8), at(11, 10), s, zone))
    }
}
