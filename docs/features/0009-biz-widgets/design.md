# design · 桌面 Widget 三款

前置：RequireSpec Q1~Q9 已拍板。技术底座 Glance（UI 说明 §6 既定），色板/汇总口径全部复用现有 domain 层，Widget 层只做「取数 + 摆版 + 动作路由」。

## 1. 依赖与装配

- `gradle/libs.versions.toml`：+`glance = "1.1.1"`；`app` 依赖 `androidx.glance:glance-appwidget`（传递带 glance runtime）。不用 glance-material3。
- 新包 `widget/`，与 `timer/` 平级：

```
widget/
├── Wg.kt              # 色板映射（亮暗 ColorProviders）+ 文案纯函数
├── WidgetSnapshot.kt  # suspend 取数：宝宝/当日汇总/进行中睡眠/上次喂奶
├── WidgetRefresher.kt # changes 流订阅 + AlarmManager 兜底，唯一刷新入口
├── QuickWidget.kt     # 2×2 快捷记录（provideGlance 布局）
├── OverviewWidget.kt  # 4×2 今日概览
├── SleepWidget.kt     # 4×2 睡眠计时（含 SleepToggleAction）
└── WidgetReceivers.kt # 三个 GlanceAppWidgetReceiver（薄壳）
```

## 2. 数据层小改：BabyRepository.changes

```kotlin
private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
val changes: SharedFlow<Unit> = _changes          // 丢旧保新，无订阅者不挂起
// add / update / softDelete 尾部 _changes.tryEmit(Unit)
```

- meta 写入不 emit（亲喂计时态不在 widget 展示）
- TimerCenter 的睡眠开始/结束全走 repo.add/update → 自动触发刷新，无需另挂钩子

## 3. WidgetSnapshot（取数，一次查完三款共用）

```kotlin
data class WidgetSnapshot(
    val baby: BabyInfo?,                    // null = 未建档（占位态）
    val dayNumber: Int,                     // 第 N 天
    val summary: DaySummary,                // 复用 computeDaySummary（含进行中睡眠折算）
    val sleepStartAt: Long?,                // 进行中睡眠开始；null = 没在睡
    val lastFeedAt: Long?,                  // 距上次喂奶
    val now: Long,
)
suspend fun loadSnapshot(context: Context): WidgetSnapshot
```

- 取数路径：`(context.applicationContext as BabycareApplication).repository`；当日 records 用 `byType` 全查后内存滤今天（量小，避免给 DAO 加一次性日区间查询）
- 文案格式化全部抽到 `Wg.kt` 纯函数（JVM 可测）：`hmsText(millis)`（"1:23:45"）、`feedSummaryText`（"780 ml · 8 次"）、`sleepHoursText`（复用 `TimeKit.hoursText`）等

## 4. 三款布局（Glance 组合）

色板：`ColorProviders(LightBabyColors.x, DarkBabyColors.x)` 直用 Compose Color 常量，系统深色模式自动切；若该 API 不可用（编译不过），退路是 res `values/values-night` 建同名色——实现时先试直用。

| 款式 | 结构 |
| --- | --- |
| QuickWidget 2×2 | 上：第 69 天（主色文字）+「780 ml · 8 次」；下：2×2 网格四圆钮（奶粉🧡/亲喂🩷/尿布💚/睡眠💙，类型色底 + 白字） |
| OverviewWidget 4×2 | 左列：奶量/睡眠/大小便三行汇总（emoji + 数值）；右列：6 快捷键 2×3 网格（奶粉/亲喂/尿布/睡眠/洗澡/生长） |
| SleepWidget 4×2 | 进行中：😴 + hms 时长（大字）+ 开始 HH:mm + 距上次喂奶 +「结束」大按钮（primary 底）；未睡：😴 睡眠计时 +「开始」大按钮 |

- 未建档：三款统一居中「先在 App 里给宝宝建档」，整卡点击开 App
- 圆钮/大按钮用 `Box(background=类型色) + Text` + `clickable`，不依赖 Glance Button 样式

## 5. 动作路由

| 按钮 | 动作 |
| --- | --- |
| 睡眠开始/结束 | `actionRunCallback<SleepToggleAction>()`：进行中→`timerCenter.stopSleepNow()`（与通知按钮同路径，标签按结束小时自动判）；未睡→`startSleep()`。跑完顺手 `update` 三款（changes 流是主要刷新源，这里是双保险） |
| 奶粉/尿布/亲喂/生长 | `actionStartActivity(MainActivity intent + extra sheet=formula/diaper/nursing/growth)` |
| 洗澡 | 深链 `sheet=bath`，TodayPage 消费到 bath 键 = 直接 `saveBath(now)` + snackbar（与 App 内快捷条同口径） |
| 占位卡/无动作区域点击 | 开 App 不带 extra |

深链接收：MainActivity `launchMode=singleTop`；onCreate + onNewIntent 解析 `getStringExtra("sheet")` → `PendingSheetBus`（Application 级 `MutableStateFlow<String?>`）emit；`BabycareApp` collect 转发 `TodayPage(pendingSheet=...)`，TodayPage `LaunchedEffect` 里 `RecordSheet.fromWidgetKey(key)` 落成初始弹层后清空 Bus。键→弹层映射放 `RecordSheet` companion：formula→Formula(null)、diaper→Diaper(null)、nursing→NursingStart、growth→Growth(null)、bath 特殊（即记不弹层）。

## 6. 刷新（WidgetRefresher）

```kotlin
class WidgetRefresher(context, repo) {
    fun start(scope)   // Application.onCreate 调一次
    // = scope.launch { repo.changes.collect { updateAll() } }
    // + updateAll 后按「是否睡眠进行中」重排 AlarmManager（60s / 30min 两档）
}
```

- AlarmManager `setRepeating`（不精确，Doze 下允许拖后——分钟级精度够用）；PendingIntent→BroadcastReceiver 触发 `updateAll()`
- 兜底 alarm 的意义：进程被杀后 Glance 内容停留旧值，alarm 拉起进程重画 + 跨天摘要重置 + 睡眠时长跳动
- `updatePeriodMillis=0`（provider XML 里关掉系统节流更新，全走自己的 alarm）

## 7. Manifest 与 provider XML

- 三个 receiver（`WidgetReceivers.kt`），`exported=false`，intent-filter `APPWIDGET_UPDATE`
- `res/xml/` 三份 provider info：`targetCellWidth/Height`（31+）与 `minWidth/minHeight`（26~30 按 dip 70×40/250×40 档），`previewLayout`（31+）指向各自真布局；`res/drawable/` 一张简易 `widget_preview.png` 兜底
- `initialLayout` 用一个共用的「载入中」简单布局，Glance 就位后替换

## 8. 测试与预期

- 新增 JVM 单测：`WgTest`（hmsText 分/时/天边界、feedSummaryText、占位文案判定）约 4~5 个；64 → ~69
- Glance 组合层不测（无 Robolectric，视觉靠真机冒烟）
- 验证：`testDebugUnitTest + assembleDebug` 全绿

## 9. 边界与取舍

- 秒级刷新不做（耗电，UI 说明已拍分钟级）；26~30 预览图简陋
- resizable 关：三款固定尺寸，拖拽不改版式
- Glance API 若与预期不符（ColorProviders day/night、background 圆角），按 §4 退路走 res 资源，不改行为设计
