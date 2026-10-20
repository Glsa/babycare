# 0011 · 喂奶间隔倒计时 — 技术设计

日期：2026-10-18 · 前置：RequireSpec 已定稿

## 1. 总体

计算是**纯 JVM 函数**（可测），设置是 Application 级 StateFlow（与深色开关同款），展示只动今天页头部 badge 区，设置入口只动我的页。

## 2. 新增文件

### `domain/FeedInterval.kt`（纯 JVM）

```kotlin
@Serializable data class FeedIntervalSettings(
    val dayMin: Int = 180,      // 白天间隔（分钟）
    val nightMin: Int = 180,    // 夜晚间隔（分钟）
    val dayStartHour: Int = 7,  // 白天从几点
    val nightStartHour: Int = 19,
)

object FeedIntervalKit {
    /** 这次喂奶发生在白天还是夜晚：[dayStart, nightStart) 为白天，含头不含尾 */
    fun isDayHour(hour: Int, s: FeedIntervalSettings): Boolean

    /** 距下次喂奶还剩多少毫秒；负数 = 已超时 */
    fun remainingMillis(lastFeedAt: Long, now: Long, s: FeedIntervalSettings, zone: ZoneId): Long
    // = lastFeedAt + interval(lastFeedAt 的昼夜) - now

    /** badge 文案：null = 不显示（无喂奶时刻）；否则 (text, overdue: Boolean) */
    fun badgeText(lastFeedAt: Long?, now: Long, s: FeedIntervalSettings, zone: ZoneId): Pair<String, Boolean>?
}
```

文案规则（与睡眠计时 `hmsText` 同风格）：`1:23` / `0:45`（时:分，分钟粒度）；超时 `该喂奶了 · 超 0:12`。

### 测试 `domain/FeedIntervalTest.kt`（+8 左右）

昼夜判定边界（6:59/7:00/18:59/19:00）、自定义分界、白天/夜晚间隔取值、超时负值、无记录 null、跨午夜（23:00 喂按夜晚间隔）、文案格式。

## 3. 改动文件

| 文件 | 改动 |
| --- | --- |
| `BabycareApplication.kt` | +`feedInterval: MutableStateFlow<FeedIntervalSettings>`（prefs key `feed_interval` 存 JSON，坏值回落默认）+ `setFeedInterval()` |
| `ui/today/TodayPage.kt` | 头部 badge 区：`距上次喂奶` badge 旁加倒计时 badge（`⏱ 距下次喂奶 1:23` 喂奶色 / `🍼 该喂奶了 · 超 0:12` 红 `MaterialTheme.colorScheme.error`）；lastFeedAt 取 `max(亲喂计时 startAt, state.lastFeed.occurredAt)` |
| `ui/mine/MinePage.kt` | 设置卡加一行「🍼 喂奶间隔」subtitle 动态「白天 3h · 夜晚 3h」→ 编辑弹层（4 输入框：白天/夜晚小时数、白天/夜晚起点整点，保存校验起点不等且小时合法） |

## 4. 关键决策

- **VM 不动**：设置从 `app.feedInterval` StateFlow 直读（composable collect），倒计时是纯函数，无新增仓库/DAO。
- **小时输入分钟存储**：弹层输小时（3.5），`*60` 取整落 prefs；显示副标题时 `180分 → 3h`（整除显示整数，否则 3.5h）。
- **时区**：昼夜判定用系统 `ZoneId`（与全 App 口径一致）。
- **红 badge**：用 `MaterialTheme.colorScheme.error` 作前景 + 10% 透明 error 作底（TypeBadge 支持 color/soft 参数，传同色即可）。
