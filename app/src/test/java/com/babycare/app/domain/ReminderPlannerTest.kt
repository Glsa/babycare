package com.babycare.app.domain

import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.AgendaPayload
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.model.SupplementPlanPayload
import com.babycare.app.data.model.VaccineDosePayload
import com.babycare.app.data.model.encodePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** 提醒规划（0014 · FR-13）：未来 7 天窗口 + 各分项规则 + due 过滤 */
class ReminderPlannerTest {

    private val zone = ZoneOffset.UTC
    private val on = ReminderSettings()

    private fun at(d: LocalDate, hour: Int, minute: Int = 0): Long =
        d.atTime(hour, minute).toInstant(zone).toEpochMilli()

    private fun entity(type: String, payload: com.babycare.app.data.model.RecordPayload, occurredAt: Long) =
        RecordEntity(
            uuid = java.util.UUID.randomUUID().toString(),
            type = type, payload = encodePayload(payload),
            occurredAt = occurredAt, updatedAt = 0L, deleted = false, author = "a", babyId = "b",
        )

    // ---- 疫苗 ----

    @Test
    fun `疫苗_未完成剂次排前1前0两条_前3已过`() {
        // 今天 11-12 10:00，b2 计划日 11-15（生日+1月）
        val today = LocalDate.of(2026, 11, 12)
        val now = at(today, 10)
        val input = ReminderInput(birthday = LocalDate.of(2026, 10, 15), emptyList(), emptyList(), emptyList(), FeedIntervalSettings())
        val list = ReminderPlanner.plan(input, on, now, zone).filter { it.dedupeKey.startsWith("vac:") }
        // 前3（11-12 9:00）已过被滤；剩 前1（11-14）与当天（11-15）
        assertEquals(listOf("vac:b2:-1", "vac:b2:0"), list.map { it.dedupeKey })
        assertEquals(at(LocalDate.of(2026, 11, 14), 9), list[0].at)
        assertEquals(ReminderPlanner.TAB_GROWTH, list[0].tab)
    }

    @Test
    fun `疫苗_已种与不接种都排除`() {
        val today = LocalDate.of(2026, 11, 12)
        val now = at(today, 10)
        val done = entity(
            RecordTypes.VACCINE_DOSE,
            VaccineDosePayload("乙肝疫苗", 2, at(LocalDate.of(2026, 11, 10), 9), key = "b2", status = "done"),
            at(LocalDate.of(2026, 11, 10), 9),
        )
        val input = ReminderInput(LocalDate.of(2026, 10, 15), listOf(done), emptyList(), emptyList(), FeedIntervalSettings())
        assertTrue(ReminderPlanner.plan(input, on, now, zone).none { it.dedupeKey.startsWith("vac:") })
    }

    // ---- 补剂 ----

    @Test
    fun `补剂_未来7天乘时间点_今天已过时点跳过`() {
        val today = LocalDate.of(2026, 11, 12)
        val now = at(today, 10)
        val plan = entity(
            RecordTypes.SUPPLEMENT_PLAN,
            SupplementPlanPayload("维生素D3", "400IU", listOf("09:00", "21:00"), at(today.minusDays(1), 9)),
            at(today.minusDays(1), 9),
        )
        val input = ReminderInput(LocalDate.of(2026, 10, 15), emptyList(), listOf(plan), emptyList(), FeedIntervalSettings())
        val list = ReminderPlanner.plan(input, on, now, zone).filter { it.dedupeKey.startsWith("supp:") }
        // 今天 09:00 已过 → 今天只剩 21:00；其后 6 天 × 2 = 13 条，首条今天 21:00
        assertEquals(13, list.size)
        assertEquals(at(today, 21), list.first().at)
        assertTrue(list.first().text.contains("维生素D3"))
    }

    @Test
    fun `补剂_停用排除_未开始从开始日起排`() {
        val today = LocalDate.of(2026, 11, 12)
        val now = at(today, 10)
        val stopped = entity(
            RecordTypes.SUPPLEMENT_PLAN,
            SupplementPlanPayload("铁剂", "", listOf("09:00"), at(today.minusDays(2), 9), stopped = true),
            at(today.minusDays(2), 9),
        )
        val future = entity(
            RecordTypes.SUPPLEMENT_PLAN,
            SupplementPlanPayload("DHA", "", listOf("09:00"), at(today.plusDays(2), 9)),
            at(today.plusDays(2), 9),
        )
        val input = ReminderInput(LocalDate.of(2026, 10, 15), emptyList(), listOf(stopped, future), emptyList(), FeedIntervalSettings())
        val list = ReminderPlanner.plan(input, on, now, zone).filter { it.dedupeKey.startsWith("supp:") }
        // 停用的 0 条；后天开始的 → 今天/明天不排，从开始日起 5 天
        assertTrue(list.none { it.dedupeKey.startsWith("supp:铁剂") })
        assertEquals(5, list.size)
        assertEquals(at(today.plusDays(2), 9), list.first().at)
    }

    // ---- 行程 ----

    @Test
    fun `行程_提前量触发_完成排除_提前日已过不排`() {
        val today = LocalDate.of(2026, 11, 12)
        val now = at(today, 10)
        val day = today.plusDays(2)
        fun agenda(remind: Int, done: Boolean = false) = entity(
            RecordTypes.AGENDA,
            AgendaPayload(title = "3月龄儿保", kind = "checkup", remindDaysBefore = remind, done = done),
            at(day, 10),
        )
        val input = ReminderInput(LocalDate.of(2026, 10, 15), emptyList(), emptyList(), listOf(agenda(1), agenda(3), agenda(1, done = true)), FeedIntervalSettings())
        val list = ReminderPlanner.plan(input, on, now, zone).filter { it.dedupeKey.startsWith("agenda:") }
        // remind=1 → 今天+1 9:00；remind=3 → 触发日已过；done 排除
        assertEquals(1, list.size)
        assertEquals(at(today.plusDays(1), 9), list[0].at)
        assertEquals(ReminderPlanner.TAB_AGENDA, list[0].tab)
    }

    // ---- 关键节点 ----

    @Test
    fun `节点_百天前1天在窗口内_过期跳过`() {
        val today = LocalDate.of(2026, 11, 12)
        val now = at(today, 8) // 今天 9:00 的节点提醒还没过
        val input = ReminderInput(birthday = today.minusDays(99), emptyList(), emptyList(), emptyList(), FeedIntervalSettings())
        val list = ReminderPlanner.plan(input, on, now, zone).filter { it.dedupeKey.startsWith("mile:") }
        // 百天 = 明天，前 1 天 = 今天 9:00；满月/半岁/周岁都远超窗口
        assertEquals(1, list.size)
        assertEquals(at(today, 9), list[0].at)
        assertTrue(list[0].text.contains("拍照留念"))
    }

    // ---- 喂奶间隔 ----

    @Test
    fun `喂奶_间隔到期与超时钳到当前`() {
        val today = LocalDate.of(2026, 11, 12)
        val now = at(today, 10)
        // 8:00 喂（白天 180 分）→ 11:00 到期
        val pending = ReminderInput(LocalDate.of(2026, 10, 15), emptyList(), emptyList(), emptyList(), FeedIntervalSettings(), lastFeedStart = at(today, 8))
        assertEquals(at(today, 11), ReminderPlanner.plan(pending, on, now, zone).first { it.dedupeKey.startsWith("feed:") }.at)
        // 昨天喂的 → 超时 → 钳到 now+1s 立即发
        val overdue = pending.copy(lastFeedStart = at(today.minusDays(1), 8))
        assertEquals(now + 1_000, ReminderPlanner.plan(overdue, on, now, zone).first { it.dedupeKey.startsWith("feed:") }.at)
        // 没喂过 → 不排
        assertTrue(ReminderPlanner.plan(pending.copy(lastFeedStart = null), on, now, zone).none { it.dedupeKey.startsWith("feed:") })
    }

    // ---- 开关与 due ----

    @Test
    fun `总开关与分项过滤`() {
        val today = LocalDate.of(2026, 11, 12)
        val now = at(today, 10)
        val input = ReminderInput(LocalDate.of(2026, 10, 15), emptyList(), emptyList(), emptyList(), FeedIntervalSettings(), lastFeedStart = at(today, 8))
        assertTrue(ReminderPlanner.plan(input, on.copy(master = false), now, zone).isEmpty())
        assertTrue(ReminderPlanner.plan(input, on.copy(feedInterval = false), now, zone).none { it.dedupeKey.startsWith("feed:") })
    }

    @Test
    fun `due窗口_过期太久不补发`() {
        val now = LocalDateTime.of(2026, 11, 12, 10, 0).toInstant(zone).toEpochMilli()
        fun spec(at: Long) = ReminderSpec(at, "k$at", "t", "x", 0)
        val list = listOf(spec(now - 20 * 60_000), spec(now - 10 * 60_000), spec(now + 30_000), spec(now + 5 * 60_000))
        val due = ReminderPlanner.due(list, now)
        assertEquals(2, due.size) // 20 分钟前的漏发不补；10 分钟前和 30 秒后的发
        assertFalse(due.any { it.dedupeKey == "k${now - 20 * 60_000}" })
    }
}
