# design · 今天页 + 快捷记录 + 首启档案

## 1. 结构与状态

- `TodayViewModel`：Room Flow → `TodayUiState(baby/date/records/summary/lastFeed/nowMillis)`；30 秒心跳刷新「进行中睡眠/距上次喂奶」；跨天用 `LocalDate` key 去重后才重订阅当日查询
- 弹层调度：`activeSheet: RecordSheet?`（sealed：Formula/Nursing/Diaper/Sleep/Detail），编辑 = 同弹层带 `editing: RecordEntity?` 回填
- 依赖新增仅 `lifecycle-viewmodel-compose`（viewModel factory）

## 2. 页面结构（对齐帧 01）

```
今天页 LazyColumn:
  头部：10月17日 周六（text3）｜ 小汤圆 + 第69天徽章（primarySoft 底）+ 2个月7天
  距上次喂奶胶囊（feed 色）
  今日概要卡（surface 20dp）：🍼 780ml·8次（含亲喂 N 次 M 分）｜ 😴 14.2h（进行中…）｜ 💧 尿6便2 ｜ 🛁 已洗/未
  快捷五钮（圆形 soft 底 + 类型色 emoji）：奶粉/亲喂/尿布/睡眠/洗澡
  今日时间轴：类型色圆点 emoji + 标题 + 时间/备注
```

今日行程卡**本版不做**（数据来源是补剂/早教/疫苗 feature），留到行程 feature 一起。

## 3. 关键取舍

| 点 | 选择 | 理由 |
| --- | --- | --- |
| 亲喂/睡眠 | 手动补录先行，计时器 0004 | 计时要前台服务/通知/恢复，独立成片；手动版已覆盖补录场景 |
| 时间选择 | 快捷档（现在/15分前/30分前/1小时前/2小时前）+ 自定义对话框（TimePicker + 今天/昨天 chips） | Material3 无现成 TimePickerDialog；快捷档覆盖 90% 补录（夜奶=昨天+时间），自定义兜底 |
| 睡眠归属 | occurredAt = startAt（开始日） | 深夜帧「今晚记录」语义：23:10 夜觉算当晚 |
| 删除 | 软删 + Snackbar 无撤销 | 墓碑同步语义；误删可将来在回收站恢复（V2） |
| 编辑范围 | 可创建的五种类型可编辑；生长/疫苗等后续类型只读+删除 | 避免本版做全类型编辑器 |
| 洗澡 | 快捷钮一键直存 + Snackbar | PRD：一键打卡 |

## 4. 文件拆分

```
domain/TimeKit.kt        日期边界/时长文案/距上次喂奶文案（纯函数）
domain/Timeline.kt       RecordEntity → 时间轴展示模型（纯函数）
domain/BabyInfo.kt       baby_profile 解析（纯函数）
ui/today/TodayViewModel.kt
ui/today/TodayPage.kt    页面骨架 + 头部 + 概要 + 快捷条 + 时间轴
ui/today/TodayComponents.kt  SummaryCard/QuickAction/TimelineRow/TypeBadge
ui/today/RecordSheets.kt 五个弹层 + TimePointDialog + 详情/删除确认
ui/onboarding/OnboardingScreen.kt
ui/theme/TypeColors.kt   type → (类型色, soft 底)
```

DAO 增量：`observeRange` 加 babyId 过滤；新增 `observeLast(babyId, types)`（距上次喂奶，不限今天）。

## 5. 风险

| 风险 | 兜底 |
| --- | --- |
| Material3 实验 API（BottomSheet/DatePicker/TimePicker）变動 | 文件级 `@OptIn(ExperimentalMaterial3Api)`，升级集中改 |
| 时区/跨天（00:00 后仍看昨天） | 日期 key 去重重订阅已处理；TimeKit 全部显式传 ZoneId |
| 弹层层级（详情→编辑→时间对话框）叠弹 | activeSheet 单值状态机：开新必关旧 |
