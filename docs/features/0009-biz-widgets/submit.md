# 0009 · Widget 三款 — 交付说明

日期：2026-10-18 · 状态：已完成（真机冒烟待跑，与 0003~0008 一起最后过一遍）

## 验收对照（RequireSpec 验收 6 条）

1. **快捷记录 2×2**：第 N 天 + 今日奶量摘要（`780 ml · 8 次`）+ 奶粉/亲喂/尿布/睡眠四圆钮 → `QuickWidget.kt` ✅
2. **今日概览 4×2**：左列当日汇总（天数/喂奶/睡眠/尿布）+ 右列 6 快捷键（多洗澡、生长）→ `OverviewWidget.kt` ✅
3. **睡眠计时 4×2**：未睡 = 开始大按钮；进行中 = 😴 + `1:23:45` 实时时长 + 开始时刻 + 距上次喂奶 + 结束大按钮 → `SleepWidget.kt` ✅
4. **点击路径 ≤1 附加操作**：睡眠键/大按钮后台直做（0 附加）；奶粉/亲喂/尿布/生长开 App 直弹对应弹层（1 附加）；洗澡开 App 即记（0 附加，与 App 内快捷条同口径）→ 深链 `sheet` extra + `MainActivity(singleTop)` + `pendingSheet` 总线 ✅
5. **刷新分钟级**：repo `changes` 流（增改删即刷）+ AlarmManager 兜底（睡眠中 60s / 常态 30min，随睡眠态换档）→ `WidgetRefresher.kt` ✅
6. **深夜配色与 App 暗色一致**：`wgPalette` 逐 token 映射 Light/DarkBabyColors，`ColorProvider(day, night)` 跟随系统 ✅

单测：**69 个全绿**（+5：`WgTest` — hms 补零/负值、奶量摘要、概览喂奶 4 形态、概览睡眠 3 形态、尿布文本/天数）。

## 交付内容

| 类别 | 文件 |
| --- | --- |
| 依赖 | `gradle/libs.versions.toml`（glance 1.1.1）、`app/build.gradle.kts` |
| 纯函数文案 | `widget/Wg.kt`（Wg 对象，全部可测文本） |
| 色板/共用件 | `widget/WidgetUi.kt`（WgPalette、NoBabyContent、CircleButton、BigButton、openAppAction、EXTRA_SHEET） |
| 取数 | `widget/WidgetSnapshot.kt`（loadSnapshot 一次性快照 + updateAllWidgets）、`data/repo/BabyRepository.kt`（+changes 流、+dayOnce、observeLastFeed 原有） |
| 三款本体 | `widget/QuickWidget.kt`、`OverviewWidget.kt`、`SleepWidget.kt`（含 SleepToggleAction） |
| 壳与刷新 | `widget/WidgetReceivers.kt`（3 + AlarmReceiver）、`widget/WidgetRefresher.kt` |
| 资源 | `res/xml/widget_{quick,overview,sleep}_info.xml`、`res/layout/widget_loading.xml`、`res/drawable/widget_preview.xml` |
| 装配 | `AndroidManifest.xml`（singleTop + 4 receiver）、`BabycareApplication.kt`（pendingSheet + appScope + 常驻刷新）、`MainActivity.kt`（onCreate/onNewIntent 收 sheet extra） |
| 消费端 | `ui/BabycareApp.kt`（切回今天 Tab）、`ui/today/TodayPage.kt`（消费 pendingSheet：bath 即记 + 其余弹层）、`RecordSheets.kt`（+fromWidgetKey） |
| 防御 | `timer/TimerCenter.kt` refreshService 加 try/catch（widget 后台路径可能吃 Android 12+ FGS 启动限制，状态源在 Room 不致命） |

## 偏离设计

1. **previewLayout 未做**（design §9 已预留）：Glance 的 compose 布局无法生成静态 layout 资源给系统预览引用，全版本统一走 `previewImage` 矢量占位图。
2. **日夜间色 API 名修正**：设计稿写的 `ColorProviders(day, night)` 实为 `androidx.glance.color.ColorProvider(day, night)` 工厂函数；`androidx.glance.color.ColorProviders` 是 M3 主题容器（不是 ColorProvider 子类型），不能用。已 javap 解包 Glance 1.1.1 AAR 逐一核实。
3. **`updateAll(context)` 不存在**：GlanceAppWidget 1.1.1 无此成员；改为 `GlanceAppWidgetManager.getGlanceIds(Widget类::class.java)` 逐实例 `update(context, id)`。
4. **`defaultWeight()` 作用域**：是 RowScope/ColumnScope 的成员扩展而非可 import 的顶层函数；OverviewWidget 的 KeyRow 改为在 Column 作用域内拼好 modifier 传入。

## 踩坑

- `MutableStateFlow` import 少了 `.flow` 包名，一处错在 Application 里引发 BabycareApp/TodayPage 三处连锁「Cannot infer type」——跨文件级联报错先修源头。
- OverviewWidget 手改布局时一度吞掉「尿布」键，自查对照设计恢复——六键清单要以设计稿为准逐项核对。

## 已知边界

- Widget 上不展示未建档宝宝之外的多宝宝切换（V1 单宝宝，与 App 同口径）。
- 闹钟走 `setRepeating`（RTC，不唤醒设备），锁屏深睡时可能延迟到唤醒后补刷——对「分钟级」足够，不引入 setExactAndAllowWhileIdle 的耗电与权限代价。
- 亲喂计时进行中时，widget 亲喂键仍是深链开 App（App 内已有进行中卡片接管），不在 widget 端做亲喂启停。
- `updatePeriodMillis=0`：完全交给 changes 流 + 闹钟，避免系统 1 小时粗粒度更新与自定义刷新打架。
