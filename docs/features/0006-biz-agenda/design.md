# design · 行程

## 1. 数据与写入（沿用 0005 复用策略）

- `AgendaPayload`（title/kind/remindDaysBefore/done/note）已有；**occurredAt = 行程时间**（日期 + 时分）
- 完成 = payload.done 翻转（update 整条记录，updatedAt 推进 → 天然可同步）
- 写操作进 `TodayViewModel`（saveAgenda / updateAgenda / toggleAgendaDone），读流新开 `AgendaViewModel`；弹层走 `RecordSheetHost` 复用（新增 `RecordSheet.Agenda` 分支）
- kind：`checkup` / `custom`（`vaccine` 值留给成长 feature，弹层里不出现）

## 2. 纯函数 `domain/AgendaKit.kt`

```kotlin
data class AgendaEntry(entity, payload)          // 解码失败跳过
fun agendaEntries(records): List<AgendaEntry>    // 按 occurredAt 升序
data class AgendaBoard(today, upcoming, futureMonths)
fun groupAgendas(records, today, zone): AgendaBoard
// today：occurredAt 落今天（含已完成）
// upcoming：明天 ~ 今天+7（含第 7 天）
// futureMonths：> 7 天的按 YearMonth 分组升序（月内折叠状态是 UI 态，不进纯函数）
fun todayAgendas(records, today, zone): List<AgendaEntry>  // 今天卡直接用 groupAgendas(...).today 也行，但今天页只算今天一段，独立函数少拉全量分组
```

行程数据量小（个位数/月），`observeByType(AGENDA)` 全量拉 + 内存分组，不分页。

## 3. 体检自动生成 `domain/Checkups.kt`

- 节点表：42 天（生日 + 41 天）+ 3/6/8/12/18/24/30/36 月龄，标题「42天体检」「3月龄体检」…，时间 10:00
- **确定性 uuid**：`checkup-{key}-{babyId}`（如 `checkup-3m-xxx`）→ 幂等：`byUuid == null` 才 add；手动删掉的体检（软删但 uuid 还在）不会被补回，满足验收 4
- 触发点：`TodayViewModel` init 观察 baby 流，按 uuid 去重，每个宝宝 ensure 一次（建档时流会重发，自动覆盖，createBaby 里不用单独调）

## 4. 行程 Tab `ui/agenda/AgendaPage.kt`

- 三段分区 composable：`TodaySection` / `UpcomingSection` / `FutureSection`，后续补剂/早教分区就是往页面里加 Section，结构预留
- 行组件 `AgendaRow`：左侧完成圆钮（○/✓，点按 toggle，不弹确认）+ HH:mm + 标题（完成后置灰）+ 类型徽章（体检=primary / 行程=text2）+（非今天段）周几或日期；行主体点按 → Detail 弹层（复用 `buildTimelineItems` 单条转 `TimelineItemModel`）
- 未来计划：月标题行（2026年11月 · N 项 + 展开/收起箭头）点击切换，`rememberSaveable` 记月展开态，默认收起
- ＋ 用 `FloatingActionButton`；页面数据 `AgendaViewModel`（bag: observeByType → groupAgendas，today 在订阅时定格，跨午夜不刷——与日历页同口径）

## 5. 今天页今日行程卡

- 数据**不加新流**：今天页 `state.records` 已含当天的 AGENDA 记录，`AgendaKit.todayAgendas(records, …)` 内存过滤
- 卡片 `TodayAgendaCard`（放 `ui/agenda/AgendaComponents.kt`）：Surface 圆角卡，标题行「今日行程」+ 前 3 条行 + 底部「剩 N 项待办 · 查看全部 ›」；整卡 click → `onOpenAgenda()`
- `BabycareApp` 给 `TodayPage` 传 `onOpenAgenda = { selected = 2 }`（Tab 状态在根布局）
- 位置：今日概要卡之后、进行中计时卡之前（帧 01 顺序：概要 → 行程卡 → 快捷记录）

## 6. 日历未来日回填

`CalendarPage` 未来日分支：`DayTitle` + 当日行程列表（`AgendaRow` 复用，只读不显示完成钮？不——未来行程也可先勾完成，保留钮）+ 空则「这天还没有计划」。数据就在 `state.selectedRecords`（0005 已订阅未来日），不加查询。

## 7. 弹层 `AgendaSheet`（`ui/agenda/AgendaSheet.kt`，挂进 RecordSheetHost）

- 标题输入框 + 类型 chips（体检/自定义）+ 日期行（`DatePickerDialog`，模式抄首启页，初始值 = 编辑值或明天）+ 时间行（`TimePointDialog`，**baseDate = 所选日期**）+ 备注
- 保存：`occurredAt = 日期.atTime(时,分)`；新建默认明天 10:00
- `editableSheet()` 补 `AGENDA -> RecordSheet.Agenda(entity)`：今天页/日历时间轴里的行程也能编辑

## 8. 测试（纯 JVM）

- `AgendaKitTest`：分组边界（今天跨午夜、第 7 天入 upcoming / 第 8 天入 futureMonths、跨月分组升序）、坏 payload 跳过、升序排序
- `CheckupsTest`：节点日期（42 天 = 生日+41、月龄 plusMonths）、标题、确定性 uuid 稳定
