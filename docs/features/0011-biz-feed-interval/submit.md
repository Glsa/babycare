# 0011 · 喂奶间隔倒计时 — 交付说明

日期：2026-10-18 · 状态：已完成（真机冒烟待跑，与 0003~0010 一起）

## 验收对照（RequireSpec 验收 7 条）

1. **头部 badge + 30s 心跳刷新**：`TodayPage` 头部「距上次喂奶」旁并排倒计时 badge，复用现有 `nowMillis` 心跳；无喂奶记录（且无进行中亲喂）不显示 ✅
2. **默认即用**：`FeedIntervalSettings(dayMin=180, nightMin=180)`，不设置就生效 ✅
3. **我的页设置**：设置卡新增「喂奶间隔」行（副标题动态显示当前值），弹层 4 输入（白天/夜晚小时数 + 昼夜起点整点），保存后 `feedInterval` StateFlow 立即驱动今天页刷新 ✅
4. **昼夜判定按喂奶开始时刻**：`isDayHour` 含 7:00/19:00 边界与跨午夜；倒计时跨分界不换档 ✅
5. **超时红 badge**：`🍼 该喂奶了 · 超 0:12`，error 色前景 + 12% 透明底 ✅
6. **进行中亲喂起算**：`max(亲喂计时 startAt, 最近喂奶记录 occurredAt)` ✅
7. **单测**：89 全绿（+13：昼夜边界、反向分界、白天/夜晚间隔、跨午夜、超时负值、无记录、文案格式、取整、副标题）✅

## 交付内容

| 类别 | 文件 |
| --- | --- |
| 计算（纯 JVM） | `domain/FeedInterval.kt`（FeedIntervalSettings + FeedIntervalKit：isDayHour / remainingMillis / badgeText / hoursText） |
| 设置状态 | `BabycareApplication.kt`（`feedInterval` StateFlow + prefs 单 key JSON，坏值回落默认） |
| 展示 | `ui/today/TodayPage.kt`（头部 badge 区 Row 双 badge，倒计时超时红） |
| 设置入口 | `ui/mine/MinePage.kt`（喂奶间隔行 + FeedIntervalDialog 四输入，起点相等置灰） |
| 测试 | `domain/FeedIntervalTest.kt`（13 个） |

## 偏离设计

无（实现与 design 一致；`FeedIntervalDialog` 里小时输入 0.25–24、起点 0–23 的校验范围是 design 未细化的补全）。

## 已知边界

- 倒计时纯展示，不弹通知——到期通知留给 FR-13 提醒实装（数据与文案已就绪，直接复用 `remainingMillis`）。
- 设置存 prefs 不随备份迁移：换机/清空后回落默认 3 小时，重设一次即可（RequireSpec Q4 拍板）。
- 分钟粒度文案（H:MM），秒级向上取整，不会出现 0:00。
- 亲喂进行中且早于最近喂奶记录（罕见：刚结束一瓶又开始亲喂）取较早的亲喂 startAt 会被 max 挡掉——取的是更晚者，符合「最近一次开始喂奶」语义。
- Widget 端未加倒计时（RequireSpec Q1 拍板，需要时回填今日概览左列）。

## 增量 · Widget 倒计时（2026-10-18 回填）

用户反馈回填「已知边界」第 5 条：今日概览/快捷记录两款 Widget 显示距下次喂奶。

| 落点 | 内容 |
| --- | --- |
| `domain/FeedInterval.kt` | `hm` 改公开 `hmText`（badge 与 Widget 文案共用分钟格式化） |
| `widget/Wg.kt` | `feedCountdownText`：短文案「距下次 1:30」/「该喂奶了 · 超 0:10」，同源 remainingMillis |
| `widget/WidgetSnapshot.kt` | +nursingStartAt（计时中亲喂优先）+ interval（prefs 同源）；`lastFeedStart = max(lastFeedAt, nursingStartAt)` |
| `widget/OverviewWidget.kt` | 左列 🍼 行下加 ⏱ 行，超时红（vaccine token） |
| `widget/QuickWidget.kt` | 摘要下加 11sp 小字行，超时红 |
| `widget/WidgetRefresher.kt` | 兜底闹钟三档：睡眠 60s / 倒计时在显 5min / 空闲 30min |
| 测试 | `widget/WgTest.kt`（3 个：无记录/倒计时/超时） |

没喂过奶不占行；睡眠计时款不加（用户看的是大按钮场景）。刷新最旧 5 分钟，Doze 下允许拖后。
