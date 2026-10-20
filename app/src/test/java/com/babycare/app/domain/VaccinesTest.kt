package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.VaccineDosePayload
import com.babycare.app.data.model.encodePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class VaccinesTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val birthday = LocalDate.of(2026, 8, 10)
    private val today = LocalDate.of(2026, 10, 18)

    private fun doseMillis(month: Int, day: Int): Long =
        ZonedDateTime.of(2026, month, day, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun record(
        key: String,
        vaccine: String,
        dose: Int,
        dateMillis: Long,
        status: String = Vaccines.STATUS_DONE,
        updatedAt: Long = 0,
        uuid: String = "r-$key-$updatedAt",
    ) = RecordEntity(
        uuid = uuid, type = RecordTypes.VACCINE_DOSE,
        payload = encodePayload(VaccineDosePayload(vaccine, dose, dateMillis, key = key, status = status)),
        occurredAt = dateMillis, updatedAt = updatedAt, deleted = false, author = "t", babyId = "b1",
    )

    @Test
    fun doses_22InOfficialOrder() {
        val doses = Vaccines.doses(birthday)
        assertEquals(22, doses.size)
        // 表头：出生两剂在生日当天，随后 1 月乙肝②、2 月 IPV①
        assertEquals(listOf("b1" to "乙肝疫苗", "bcg" to "卡介苗"), doses.take(2).map { it.key to it.vaccine })
        assertEquals(birthday, doses[0].date)
        assertEquals(LocalDate.of(2026, 9, 10), doses[2].date)   // 乙肝② = +1月
        assertEquals(LocalDate.of(2026, 10, 10), doses[3].date)  // IPV① = +2月
        assertEquals(LocalDate.of(2026, 11, 10), doses[4].date)  // 脊灰② = +3月
        // 表尾：6 岁两剂
        assertEquals(LocalDate.of(2032, 8, 10), doses.last().date)
        assertEquals("出生", doses[0].monthLabel)
        assertEquals("6岁", doses.last().monthLabel)
    }

    @Test
    fun doses_birthdayShiftMovesSchedule() {
        val doses = Vaccines.doses(LocalDate.of(2026, 8, 20))
        assertEquals(LocalDate.of(2026, 9, 20), doses[2].date)
        assertEquals(LocalDate.of(2032, 8, 20), doses.last().date)
    }

    @Test
    fun views_statusFourWays() {
        val records = listOf(
            record("b1", "乙肝疫苗", 1, doseMillis(8, 10)),                       // key 匹配 → 已种
            record("dtap1", "百白破", 1, doseMillis(9, 10), status = Vaccines.STATUS_SKIPPED), // → 不接种
            RecordEntity(   // 旧数据无 key → 按疫苗名+剂次兜底匹配 b2
                uuid = "old-b2", type = RecordTypes.VACCINE_DOSE,
                payload = encodePayload(VaccineDosePayload("乙肝疫苗", 2, doseMillis(9, 10))),
                occurredAt = doseMillis(9, 10), updatedAt = 0, deleted = false, author = "t", babyId = "b1",
            ),
        )
        val byKey = Vaccines.views(birthday, today, records).associateBy { it.plan.key }
        assertEquals(DoseStatus.DONE, byKey["b1"]?.status)
        assertEquals(DoseStatus.SKIPPED, byKey["dtap1"]?.status)
        assertEquals(DoseStatus.DONE, byKey["b2"]?.status)      // 无 key 兜底
        assertEquals(DoseStatus.DUE, byKey["ipv1"]?.status)     // 计划 10-10 ≤ today
        assertEquals(DoseStatus.NOT_DUE, byKey["opv2"]?.status) // 计划 11-10 > today
    }

    @Test
    fun views_sameKeyTakesLatestRecord() {
        val records = listOf(
            record("b1", "乙肝疫苗", 1, doseMillis(8, 10), updatedAt = 1),
            record("b1", "乙肝疫苗", 1, doseMillis(8, 12), status = Vaccines.STATUS_SKIPPED, updatedAt = 5),
        )
        assertEquals(DoseStatus.SKIPPED, Vaccines.views(birthday, today, records).first().status)
    }

    @Test
    fun progress_countsDoneNotSkipped() {
        val records = listOf(
            record("b1", "乙肝疫苗", 1, doseMillis(8, 10)),
            record("bcg", "卡介苗", 1, doseMillis(8, 10)),
            record("dtap1", "百白破", 1, doseMillis(9, 10), status = Vaccines.STATUS_SKIPPED),
        )
        assertEquals(2 to 22, Vaccines.progress(Vaccines.views(birthday, today, records)))
    }

    @Test
    fun nextDose_earliestUnfinished() {
        // 空记录：最早未完成 = 表序第一剂（生日当天的乙肝①）
        assertEquals("b1", Vaccines.nextDose(Vaccines.views(birthday, today, emptyList()))?.plan?.key)
        // 演示口径：前 4 剂已种（乙肝①②/卡介苗/IPV①）→ 下一剂 11-10 的脊灰②（口服）
        val done4 = listOf(
            record("b1", "乙肝疫苗", 1, doseMillis(8, 10)),
            record("bcg", "卡介苗", 1, doseMillis(8, 11)),
            record("b2", "乙肝疫苗", 2, doseMillis(9, 10)),
            record("ipv1", "脊灰灭活（注射）", 1, doseMillis(10, 12)),
        )
        assertEquals("opv2", Vaccines.nextDose(Vaccines.views(birthday, today, done4))?.plan?.key)
        assertEquals(4 to 22, Vaccines.progress(Vaccines.views(birthday, today, done4)))
        // 不接种也移出下一剂：opv2 标不接种 → 轮到同日的百白破①
        val skipped = done4 + record("opv2", "脊灰减毒（口服）", 2, doseMillis(11, 10), status = Vaccines.STATUS_SKIPPED)
        assertEquals("dtap1", Vaccines.nextDose(Vaccines.views(birthday, today, skipped))?.plan?.key)
        // 全部完成 → null
        val allDone = Vaccines.doses(birthday).map {
            record(it.key, it.vaccine, it.dose, doseMillis(8, 10))
        }
        assertNull(Vaccines.nextDose(Vaccines.views(birthday, today, allDone)))
    }

    @Test
    fun nearDoses_duePlusSevenDayWindow() {
        val done4 = listOf(
            record("b1", "乙肝疫苗", 1, doseMillis(8, 10)),
            record("bcg", "卡介苗", 1, doseMillis(8, 11)),
            record("b2", "乙肝疫苗", 2, doseMillis(9, 10)),
            record("ipv1", "脊灰灭活（注射）", 1, doseMillis(10, 12)),
        )
        // today 11-03：opv2/dtap1（11-10）在 7 天窗口内（含第 7 天），DONE/SKIPPED 不进
        val d1 = LocalDate.of(2026, 11, 3)
        val near1 = Vaccines.nearDoses(Vaccines.views(birthday, d1, done4), d1)
        assertEquals(listOf("opv2", "dtap1"), near1.map { it.plan.key })
        // today 11-02：11-10 是第 8 天 → 不含；前 4 剂已种 → 空
        val d2 = LocalDate.of(2026, 11, 2)
        assertTrue(Vaccines.nearDoses(Vaccines.views(birthday, d2, done4), d2).isEmpty())
        // 空记录：已到期的全进（b1/bcg/b2/ipv1），叠加 7 天内的 opv2/dtap1，按计划日升序
        val near3 = Vaccines.nearDoses(Vaccines.views(birthday, d1, emptyList()), d1)
        assertEquals(listOf("b1", "bcg", "b2", "ipv1", "opv2", "dtap1"), near3.map { it.plan.key })
    }
}
