package com.babycare.app.domain

/**
 * WHO 0–24 月生长参考（0015 生长曲线）：月度 P3/P50/P97 三档，男女分开，线性插值。
 * 数值为 WHO 标准的近似（0.1 精度，P3/P97 部分月份按 SD 推得），界面标注「近似参考带」；
 * 儿保评估仍以标准生长曲线图为准。
 */
object WhoGrowth {

    private class Ref(val p3: DoubleArray, val p50: DoubleArray, val p97: DoubleArray)

    /** 未知性别（档案没填）返回 null，界面退化为只画用户曲线不画参考带 */
    fun value(gender: String?, metric: GrowthMetric, percentile: Int, monthAge: Double): Double? {
        val ref = tables[gender ?: return null]?.get(metric) ?: return null
        require(percentile in setOf(3, 50, 97)) { "只支持 P3/P50/P97" }
        val arr = when (percentile) {
            3 -> ref.p3; 97 -> ref.p97; else -> ref.p50
        }
        val m = monthAge.coerceIn(0.0, 24.0)
        val i = m.toInt()
        if (i >= 24) return arr[24]
        return arr[i] + (arr[i + 1] - arr[i]) * (m - i)
    }

    // ---- WHO 体重 kg ----

    private val wBoyP3 = doubleArrayOf(2.5, 3.4, 4.3, 5.0, 5.6, 6.0, 6.4, 6.7, 7.0, 7.3, 7.5, 7.7, 7.9, 8.0, 8.2, 8.4, 8.6, 8.8, 9.0, 9.2, 9.3, 9.5, 9.7, 9.9, 10.1)
    private val wBoyP50 = doubleArrayOf(3.3, 4.5, 5.6, 6.4, 7.0, 7.5, 7.9, 8.3, 8.6, 8.9, 9.2, 9.4, 9.6, 9.9, 10.1, 10.3, 10.5, 10.7, 10.9, 11.1, 11.3, 11.5, 11.8, 12.0, 12.2)
    private val wBoyP97 = doubleArrayOf(4.4, 5.8, 7.1, 8.0, 8.7, 9.3, 9.8, 10.3, 10.7, 11.0, 11.4, 11.7, 12.0, 12.3, 12.6, 12.8, 13.1, 13.4, 13.7, 13.9, 14.2, 14.5, 14.7, 15.0, 15.3)
    private val wGirlP3 = doubleArrayOf(2.4, 3.2, 3.9, 4.5, 5.0, 5.4, 5.7, 6.0, 6.3, 6.5, 6.7, 6.9, 7.0, 7.2, 7.4, 7.5, 7.7, 7.9, 8.0, 8.2, 8.4, 8.5, 8.7, 8.9, 9.0)
    private val wGirlP50 = doubleArrayOf(3.2, 4.2, 5.1, 5.8, 6.4, 6.9, 7.3, 7.6, 7.9, 8.2, 8.4, 8.6, 8.9, 9.1, 9.3, 9.5, 9.7, 9.9, 10.1, 10.3, 10.5, 10.7, 10.9, 11.1, 11.4)
    private val wGirlP97 = doubleArrayOf(4.2, 5.5, 6.6, 7.5, 8.2, 8.8, 9.3, 9.7, 10.1, 10.5, 10.8, 11.1, 11.4, 11.7, 12.0, 12.3, 12.6, 12.8, 13.1, 13.4, 13.6, 13.9, 14.2, 14.4, 14.7)

    // ---- WHO 身长 cm ----

    private val hBoyP3 = doubleArrayOf(46.1, 50.7, 54.4, 57.4, 59.8, 61.7, 63.3, 64.8, 66.2, 67.6, 68.9, 70.0, 71.1, 72.2, 73.4, 74.4, 75.4, 76.4, 77.4, 78.3, 79.3, 80.3, 81.1, 81.9, 82.7)
    private val hBoyP50 = doubleArrayOf(49.9, 54.7, 58.4, 61.4, 63.9, 65.9, 67.6, 69.2, 70.6, 72.0, 73.3, 74.5, 75.7, 76.9, 78.1, 79.2, 80.2, 81.3, 82.3, 83.3, 84.3, 85.4, 86.2, 87.0, 87.8)
    private val hBoyP97 = doubleArrayOf(53.9, 58.5, 62.3, 65.3, 67.8, 69.9, 71.6, 73.2, 74.6, 76.0, 77.2, 78.4, 79.6, 80.8, 82.0, 83.1, 84.1, 85.2, 86.2, 87.2, 88.2, 89.2, 90.0, 90.9, 91.9)
    private val hGirlP3 = doubleArrayOf(45.6, 49.7, 53.0, 55.6, 57.8, 59.6, 61.2, 62.7, 64.0, 65.3, 66.5, 67.7, 68.9, 70.0, 71.2, 72.2, 73.2, 74.2, 75.2, 76.1, 77.0, 77.9, 78.8, 79.7, 80.6)
    private val hGirlP50 = doubleArrayOf(49.1, 53.7, 57.1, 59.8, 62.1, 64.0, 65.7, 67.3, 68.7, 70.1, 71.5, 72.8, 74.0, 75.2, 76.4, 77.5, 78.6, 79.7, 80.7, 81.7, 82.7, 83.7, 84.6, 85.5, 86.4)
    private val hGirlP97 = doubleArrayOf(52.9, 57.5, 61.1, 63.8, 66.2, 68.1, 69.8, 71.4, 72.9, 74.3, 75.6, 76.9, 78.5, 79.8, 81.0, 82.1, 83.2, 84.2, 85.1, 86.0, 86.9, 87.8, 88.6, 89.4, 90.7)

    // ---- WHO 头围 cm ----

    private val hcBoyP3 = doubleArrayOf(32.4, 35.2, 36.9, 38.3, 39.3, 40.3, 41.0, 41.7, 42.2, 42.7, 43.1, 43.5, 43.8, 44.1, 44.3, 44.6, 44.8, 45.0, 45.2, 45.3, 45.5, 45.7, 45.8, 46.0, 46.1)
    private val hcBoyP50 = doubleArrayOf(34.5, 37.3, 39.1, 40.5, 41.6, 42.6, 43.3, 44.0, 44.5, 45.0, 45.4, 45.8, 46.1, 46.4, 46.7, 46.9, 47.1, 47.3, 47.5, 47.7, 47.9, 48.1, 48.3, 48.4, 48.6)
    private val hcBoyP97 = doubleArrayOf(36.6, 39.4, 41.2, 42.6, 43.8, 44.8, 45.5, 46.2, 46.8, 47.3, 47.7, 48.1, 48.4, 48.7, 49.0, 49.2, 49.4, 49.6, 49.8, 50.0, 50.2, 50.4, 50.5, 50.7, 50.9)
    private val hcGirlP3 = doubleArrayOf(31.9, 34.4, 36.1, 37.3, 38.3, 39.2, 39.8, 40.4, 40.9, 41.4, 41.7, 42.0, 42.3, 42.5, 42.7, 42.9, 43.1, 43.2, 43.4, 43.5, 43.7, 43.8, 44.0, 44.1, 44.2)
    private val hcGirlP50 = doubleArrayOf(33.9, 36.5, 38.3, 39.5, 40.6, 41.5, 42.2, 42.8, 43.4, 43.8, 44.2, 44.5, 44.8, 45.0, 45.2, 45.4, 45.6, 45.8, 45.9, 46.1, 46.3, 46.4, 46.5, 46.7, 46.8)
    private val hcGirlP97 = doubleArrayOf(35.9, 38.5, 40.4, 41.7, 42.8, 43.7, 44.5, 45.1, 45.7, 46.2, 46.6, 47.0, 47.3, 47.6, 47.8, 48.0, 48.2, 48.4, 48.5, 48.7, 48.9, 49.0, 49.2, 49.3, 49.5)

    private val tables = mapOf(
        "男" to mapOf(
            GrowthMetric.WEIGHT to Ref(wBoyP3, wBoyP50, wBoyP97),
            GrowthMetric.HEIGHT to Ref(hBoyP3, hBoyP50, hBoyP97),
            GrowthMetric.HEAD to Ref(hcBoyP3, hcBoyP50, hcBoyP97),
        ),
        "女" to mapOf(
            GrowthMetric.WEIGHT to Ref(wGirlP3, wGirlP50, wGirlP97),
            GrowthMetric.HEIGHT to Ref(hGirlP3, hGirlP50, hGirlP97),
            GrowthMetric.HEAD to Ref(hcGirlP3, hcGirlP50, hcGirlP97),
        ),
    )
}
