# design · 日历页

## 1. 复用策略（核心决策）

今天页已经把「记录弹层 + 写操作」做全了。日历页**读流独立、写操作与弹层全复用**：

| 部分 | 做法 |
| --- | --- |
| 月视图/选中日数据 | 新 `CalendarViewModel`（月区间 observeRange → 按日聚合；选中日 observeRange + computeDaySummary） |
| 写操作（保存/编辑/删除） | 直接复用 `TodayViewModel` 的方法（activity 级单例，saveXxx 只依赖 baby StateFlow 与 repo，无「今天」耦合） |
| 弹层 | `RecordSheetHost` 原样复用；日历页本地维护 activeSheet，只开五类表单 + Detail（计时三种弹层不会触发） |
| 汇总卡/时间轴/快捷条 | `SummaryCard` / `TimelineRow` / `QuickActions` 复用（internal 同 module） |

不抽「RecordActions 共享层」——两个 VM 的写方法都是一两行胶水，抽象层数保持克制。

## 2. 月视图

- 周一开头 7 列；**固定 6 行 Column×Row**（数据量小，不用 LazyVerticalGrid）
- 矩阵与聚合纯函数进 `domain/CalendarKit.kt`：
  - `monthMatrix(month, zone): List<List<LocalDate?>>`（前置补上月末尾、后置补下月开头，null = 空格）
  - `groupDayTypes(records, zone): Map<LocalDate, Set<String>>`（色点数据）
- 色点：类型色取 `recordColors(type).first`；**最多显示 5 个点**（按 RecordTypes 固定顺序取，超出省略，实际单日类型很少超过 5）
- 今天 = 主色实心圆白字；选中日 = soft 底圆；今天被选中 = 主色圆
- 未来日可点（FR-11），选中显示空态

## 3. 选中日卡

- 标题：`10月15日 周四 · 第 N 天`（AgeCalc）
- `SummaryCard`：历史日传该日 23:59:59 作 now（无「进行中」概念）；今天传真实时间（静态快照，不跟 30s 心跳——日历是回看视角，实时卡在首页）
- 时间轴 `TimelineRow` → DetailSheet（编辑/删除同今天页）
- 快捷条 `QuickActions`：睡眠/亲喂走**手动补录表单**（`RecordSheet.Sleep/Nursing(null)`），不开计时
- 未来日：`这天还没有计划`空态，无快捷条

## 4. 弹层默认时间（唯一动 RecordSheets 的点）

- 五个表单弹层加 `defaultTime: Long? = null` 参数（null = now）；`RecordSheetHost` 加同名参数透传。今天页不传，日历传 `选中日.atTime(12:00)`
- `TimePointDialog` 加 `baseDate: LocalDate? = null`：null = 现行为（今天/昨天 chips）；非 null = 固定该日期（chips 隐藏，显示日期文本）——从日历补录改时间不会误落到今天

## 5. 边界

- 选中日在切月后保留（看 10 月选中 15 日 → 翻到 11 月：选中卡仍在，月视图该格不在这月）→ 翻页清除选中，回到「只看月视图」，行为直觉
- 跨午夜停在日历页：今天格子不随分钟刷新（月粒度页面，重新进入/翻页刷新）；可接受
- 月区间查询包含整月 00:00 到次月 00:00，与 dayStart/dayEndExclusive 同口径

## 6. 测试（`app/src/test`，纯 JVM）

`CalendarKitTest`：monthMatrix（周一开头、首行补位、6 行、2 月跨年）、groupDayTypes（多类型聚合、跨天分桶、时区显式）
