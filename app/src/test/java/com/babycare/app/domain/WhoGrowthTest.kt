package com.babycare.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WHO 生长参考（0015）：插值/钳制/性别与指标 */
class WhoGrowthTest {

    @Test
    fun `月度插值中点`() {
        // 男体重 P50：0 月 3.3，1 月 4.5 → 0.5 月 = 3.9
        assertEquals(3.9, WhoGrowth.value("男", GrowthMetric.WEIGHT, 50, 0.5)!!, 1e-9)
    }

    @Test
    fun `边界钳制到0与24月`() {
        assertEquals(3.3, WhoGrowth.value("男", GrowthMetric.WEIGHT, 50, -1.0)!!, 1e-9)
        assertEquals(12.2, WhoGrowth.value("男", GrowthMetric.WEIGHT, 50, 30.0)!!, 1e-9)
    }

    @Test
    fun `男女与三指标可查`() {
        assertEquals(3.3, WhoGrowth.value("男", GrowthMetric.WEIGHT, 50, 0.0)!!, 1e-9)
        assertEquals(3.2, WhoGrowth.value("女", GrowthMetric.WEIGHT, 50, 0.0)!!, 1e-9)
        assertEquals(49.9, WhoGrowth.value("男", GrowthMetric.HEIGHT, 50, 0.0)!!, 1e-9)
        assertEquals(34.5, WhoGrowth.value("男", GrowthMetric.HEAD, 50, 0.0)!!, 1e-9)
        assertNull(WhoGrowth.value(null, GrowthMetric.HEAD, 50, 0.0))
    }

    @Test
    fun `三档单调且间隔合理`() {
        for (m in intArrayOf(0, 3, 6, 12, 18, 24)) {
            for (metric in GrowthMetric.entries) {
                val p3 = WhoGrowth.value("女", metric, 3, m.toDouble())!!
                val p50 = WhoGrowth.value("女", metric, 50, m.toDouble())!!
                val p97 = WhoGrowth.value("女", metric, 97, m.toDouble())!!
                assertTrue("$metric m=$m", p3 < p50 && p50 < p97)
            }
        }
    }
}
