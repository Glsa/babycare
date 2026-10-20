# submit · 行程

## 验收对照（RequireSpec）

| # | 验收项 | 结果 | 说明 |
| --- | --- | --- | --- |
| 1 | 新建/编辑行程在行程 Tab 与日历正确分区 | ✅ 代码完成 | `AgendaKit.groupAgendas` 三段分区（单测覆盖边界：第 7 天入即将到来、第 8 天入未来、跨月分组升序） |
| 2 | 今天卡与行程 Tab 同步显示；打勾/取消两处同步 | ✅ 代码完成 | done 在 payload，Room Flow 驱动两处刷新；toggle 即 update |
| 3 | 删除后各处即时消失 | ✅ 代码完成 | 软删走既有 delete 流 |
| 4 | 体检按生日生成一次，重启不重复，删掉不补回 | ✅ 代码完成 | 确定性 uuid `checkup-{节点}-{babyId}` + `byUuid` 判存；TodayViewModel init 观察 baby 流触发（单测覆盖 42 天 = 生日+41、9 节点、uuid 唯一稳定） |
| 5 | 纯函数测试全绿 + assembleDebug | ✅ | 46 单测（40 旧 + 6 新）全绿 |

真机冒烟未跑（与 0003~0005 同欠，四期一起列清单）。

## 交付内容

- `domain/AgendaKit.kt`：`agendaEntries`（解码+升序）、`dayAgendas`（某日行程）、`groupAgendas`（今天/即将到来 7 天/未来按月）
- `domain/Checkups.kt`：附录 C 九节点（42 天 + 3/6/8/12/18/24/30/36 月龄）+ 确定性 uuid（生成逻辑在 TodayViewModel，保持 domain 纯函数）
- `ui/agenda/`：`AgendaViewModel`（全量行程内存分区）、`AgendaPage`（三段分区 + 月折叠 + FAB）、`AgendaSheet`（标题/类型/日期/时间/备注，新建默认明天 10:00）、`AgendaComponents`（`AgendaRow` + `TodayAgendaCard`）
- 复用接线：`RecordSheet.Agenda` 分支挂进 `RecordSheetHost`（今天页/日历时间轴里的行程也能编辑删除）；`TimePointDialog` 放开 internal 供跨包用
- 今天页：`TodayAgendaCard`（概要卡下方，前 3 条 + 汇总，整卡点按跳行程 Tab——Tab 状态在根布局，回调传入）；时间轴行程文案带「已完成」
- 日历未来日：`AgendaRow` 列表回填（替换空态文案，无计划仍显示原空态）
- `BabyRepository.observeAgendas()`；`TodayViewModel`：saveAgenda/updateAgenda（done 从原 payload 带回）/toggleAgendaDone + init 体检生成

## 与设计的偏离

无实质偏离。设计里「今日行程卡点击跳 Tab」的 `onOpenAgenda` 落在 `TodayPage` 新参数上，由 `BabycareApp` 传 `{ selected = 2 }`。

## 踩坑记录

- **object 内的函数不能当顶层 import**：`import com.babycare.app.domain.groupAgendas` 报 Unresolved——它是 `AgendaKit` 的成员，要 `import AgendaKit` 后 `AgendaKit.groupAgendas(...)`（`computeDaySummary` 是顶层函数所以能直接 import，两者混了）
- Kotlin Flow 的 `filterNotNull` 是独立扩展，得单独 import（不在 `map/filter` 的惯性导入清单里）
- `LazyColumn { }` 的 content lambda 是 `LazyListScope`（非 Composable），`remember` 不能写在里面——派生数据先在页面顶部算好再进 DSL（todayAgenda/futureAgenda 都提到 LazyColumn 外）

## 已知边界

- 行程 Tab 的「今天」在数据变化时定格，跨午夜停在页上不自动重算（与日历页同口径）
- 「即将到来」显示周几、未来计划显示「11月10日」；同一天多条按时间升序
- 补剂/早教/疫苗三类来源未接入（随成长 feature 往行程页加 Section、往今日行程卡加条目）
- remindDaysBefore 字段保留默认 0，提醒通知（FR-13）整体延后
