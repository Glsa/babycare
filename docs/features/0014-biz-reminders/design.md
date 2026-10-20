# 0014 · 提醒实装（FR-13）— 技术设计

日期：2026-10-18 · 前置：Request（含拍板）已定稿

## 1. 纯函数层 `domain/ReminderPlanner.kt`（JVM 可测）

```kotlin
/** 一条待发提醒：at 触发时刻；kind 用于去重键；tab 点击直达的底部 Tab 下标 */
data class ReminderSpec(val at: Long, val dedupeKey: String, val title: String, val text: String, val tab: Int)

data class ReminderSettings(        // prefs 单 key JSON
    val master: Boolean = true,      // 总开关
    val vaccine: Boolean = true,
    val supplement: Boolean = true,
    val agenda: Boolean = true,
    val milestone: Boolean = true,
    val feedInterval: Boolean = true,
)

object ReminderPlanner {
    /**
     * 未来 7 天窗口内的全部提醒（含喂奶一次性），按 at 升序。
     * 输入全部现成：Vaccines.doses / SupplementPlanPayload / AGENDA 记录 / 生日 / FeedIntervalSettings+lastFeedStart。
     */
    fun plan(input: ReminderInput, now: Long, zone: ZoneId): List<ReminderSpec>

    /** 闹钟到点调用：at ∈ (since, now+60s] 的提醒（同刻多条一起发）。since = 上次触发时刻（防重发）。 */
    fun due(list: List<ReminderSpec>, since: Long, now: Long): List<ReminderSpec>
}
```

规则：

- **疫苗**（开关 vaccine）：未种未跳过且计划日 ∈ (今天, 今天+7]的剂次 → 前 3 / 前 1 / 当天各一条 9:00；标题「💉 疫苗提醒」，文案「{疫苗名}第{剂}剂 {M月D日} 到期」。
- **补剂**（supplement）：生效方案（未停用、startDate≤今天）× 未来 7 天每天 × 各时间点；dedupeKey = `supp:{uuid}:{yyyyMMdd}:{HH:mm}`；文案「💊 {名称} {剂量} · 到点打卡」。
- **行程**（agenda）：AGENDA 记录未 done 且日期未来 7 天 → 按 remindDaysBefore 的 9:00；dedupeKey = `agenda:{uuid}:{触发日}`；文案「🗓️ {标题} · {M月D日}」。
- **关键节点**（milestone）：生日推导满月(28天)/百天(100)/半岁(180)/周岁(365)，各前 1 天 9:00（过去的跳过）；文案「📷 明天是{名称}，拍照留念」。
- **喂奶间隔**（feedInterval）：lastFeedStart + 昼夜间隔（FeedIntervalKit 同源）；单条；dedupeKey = `feed:{lastFeedStart}`。
- 总开关关 = 返回空表；分项各自过滤。全部 at 必须 > now（过期不排）。

## 2. 调度与通知 `remind/ReminderCenter.kt`

```kotlin
class ReminderCenter(context, repo, app) {
    fun start(scope)            // 启动排一次；collect repo.changes / settings 变更 / 每 30 分钟兜底 → reschedule()
    suspend fun reschedule()    // planner.plan 取 first.at → am.setAndAllowWhileIdle(RTC_WAKEUP, at, pi)
    suspend fun fire()          // receiver 调：发 due（通知）→ prefs 记 lastFiredAt → reschedule()
}
```

- 单 PendingIntent（固定 requestCode），每次 set 覆盖前一个，无批量 cancel。
- `ReminderReceiver`：goAsync + 自建 scope 调 `fire()`。
- `BootReceiver`：开机 `reschedule()`（Manifest `RECEIVE_BOOT_COMPLETED`）。
- 兜底：挂在现有 `WidgetAlarmReceiver` 的 tick 里顺带 `reschedule()`（跨天/进程被杀校准）。
- 通知：channel `reminders`（名称「提醒」，默认重要性）；notificationId = dedupeKey.hashCode()（天然去重替换）；contentIntent → MainActivity extra `tab`。
- 喂奶超时只发一次：fire 时若 dedupeKey 以 `feed:` 开头，比对 prefs `feed_reminder_fired_for`（值 = lastFeedStart），不同才发并记录。

## 3. 设置与路由

- `BabycareApplication`：`reminderSettings: MutableStateFlow<ReminderSettings>`（prefs 单 key，坏值回落默认，`by lazy` 防 attachBaseContext 前初始化）+ `setReminderSettings`；`pendingTab: MutableStateFlow<Int?>`。
- `BabycareApp`：collect `pendingTab` → 切 Tab 并清空。
- `MainActivity.handleSheetIntent`：读 extra `tab` → `app.pendingTab.value = it`。
- `ui/mine/RemindersSheet.kt`（新）：总开关 + 5 分项 SettingRow 风格行；首次打开请求 POST_NOTIFICATIONS（33+）。
- `MinePage`：提醒设置行实装（去 dimmed/badge，onClick 开 sheet；副标题显示开启的分项数）。
- `RecordSheets.Agenda` 弹层：加提前量 chips（当天/前 1 天/前 3 天 → remindDaysBefore 0/1/3，默认当天），编辑回显。

## 4. 落点清单

| 文件 | 改动 |
| --- | --- |
| `domain/ReminderPlanner.kt`（新） | 纯函数规划 + due 过滤 |
| `remind/ReminderCenter.kt`（新） | 调度 + 通知发送 |
| `remind/ReminderReceiver.kt`（新） | 闹钟/开机两个 receiver |
| `BabycareApplication.kt` | reminderSettings/pendingTab + ReminderCenter.start |
| `ui/BabycareApp.kt` / `MainActivity.kt` | pendingTab 路由 |
| `ui/mine/RemindersSheet.kt`（新）+ `MinePage.kt` | 设置入口 |
| `ui/today/RecordSheets.kt` | 行程弹层提前量 chips |
| `widget/WidgetAlarmReceiver`（WidgetReceivers.kt） | tick 顺带 reschedule |
| `AndroidManifest.xml` | 两个 receiver + boot 权限 |

## 5. 测试

`ReminderPlannerTest`（JVM，~10 个）：疫苗 3/1/当天三条与已种排除、补剂多方案多时间点与停用排除、行程 remindDaysBefore、节点前 1 天与过期跳过、喂奶间隔昼夜取值与无记录不排、总开关/分项过滤、due 窗口与 since 去重。
