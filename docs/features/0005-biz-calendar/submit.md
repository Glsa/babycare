# submit · 日历页

## 验收对照（RequireSpec）

| # | 验收项 | 结果 | 说明 |
| --- | --- | --- | --- |
| 1 | 月视图色点反映当日类型；今天高亮 | ✅ 代码完成 | `groupDayTypes` 按日聚合 + `dotTypes` 固定顺序取前 5；今天 = 主色实心圆白字（单测覆盖聚合与顺序） |
| 2 | 选中过去日汇总/时间轴/第 N 天正确 | ✅ 代码完成 | 复用 `computeDaySummary`（now=当日 23:59:59.999）、`buildTimelineItems`、`AgeCalc.dayNumber`（生日前日期不显示天数徽章） |
| 3 | 日历补录默认时间 = 选中日，色点即时多出 | ✅ 代码完成 | `defaultTime = 选中日.atTime(12:00)` 透传五个弹层；Room Flow 自动驱动色点 |
| 4 | 编辑/删除后即时刷新 | ✅ 代码完成 | 月流与选中日流各自 observe 同一 Room 表，写后自动重发 |
| 5 | 纯函数测试全绿 + assembleDebug | ✅ | 40 单测（36 旧 + 4 新）全绿，构建通过 |

真机冒烟未跑（与 0003/0004 同欠）。

## 交付内容

- `domain/CalendarKit.kt`：`monthMatrix`（周一开头固定 6×7，null 补位）、`groupDayTypes`（按日聚合类型）、`dotTypes`（固定顺序前 5）
- `ui/calendar/CalendarViewModel.kt`：读流独立（月流 + 选中日流 flatMapLatest）；翻页清选中；`goToday` 回当月并选中今天
- `ui/calendar/CalendarPage.kt`：月份条 ‹ › 今天、周头行、6×7 网格（今天主色圆/选中 soft 圆/色点行固定占位）、选中日卡（标题+汇总+快捷条+时间轴）、未来日空态
- `RecordSheets.kt`：`RecordSheetHost` + Formula/Nursing/Diaper/Sleep 四表单加 `defaultTime`/`baseDate`；`TimePointDialog` 加 `baseDate`（非空 = 固定日期，隐藏今天/昨天 chips）；SleepSheet 夜觉默认标签改按 defaultTime 的结束小时判定
- 接线：`Placeholders.kt` 移除日历占位；`BabycareApp.kt` 挂 `calendarVm`

## 与设计的偏离

- **洗澡表单未加 defaultTime**（design §4 写了五个）：洗澡只有编辑入口（新建走一键直存，时间由调用方显式传），加参数是死代码；日历页一键补录洗澡直接传 `defaultTime` 给 `saveBath`。
- **goToday 同时选中今天**（RequireSpec 只说回当月）：只回当月不选中时「今天」按钮在当月点击无反馈，顺手选中更直觉。

## 踩坑记录

- 周一开头网格里**周六是第 6 列（index 5）、周日才是末列**：首版测试把 2026-10-31（周六）断到 `rows[4].last()`，实为 `rows[4][5]`。写月历断言先列 dayOfWeek 对列号的映射。
- `replace_all` 改四个表单的 `TimePointDialog` 调用时把 BathSheet 的同构调用也带上了（它没有 baseDate 参数）——同构代码块批量替换后要 grep 一遍引用确认没有误伤。

## 已知边界

- 跨午夜停在日历页：今天格子与选中今天的汇总不自动刷新（月粒度页面，翻页/重进刷新，design §5 已接受）
- 跨天夜觉（23:00 开始）显示在开始日的格子；汇总口径与今天页一致（occurredAt = startAt 分桶）
- 未来日可选中可翻页，但无补录入口（快捷条不显示）
