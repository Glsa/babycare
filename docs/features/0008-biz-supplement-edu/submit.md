# submit · 补剂 + 早教 + 行程回填

## 验收对照（RequireSpec）

| # | 验收项 | 结果 | 说明 |
| --- | --- | --- | --- |
| 1 | 补剂：方案 CRUD + 停用生效；当日任务 = 进行中方案 × 时间点（未开始的不出）；打卡/取消独立、时间轴可见 | ✅ 代码完成 | `SupplementKit.activePlans/todayTasks` 单测覆盖（停用滤除/坏 payload、排序、开始日边界、done 三元匹配含跨午夜） |
| 2 | 早教：推荐按月龄、确定性、避昨天；打卡生成时间轴记录可编辑时长；≥13 月不显示 | ✅ 代码完成 | `EduKit.pool/picks` 单测覆盖（分段、同日确定性、条数轮换、避昨天、池小允许重复）；时长编辑走 EduSheet |
| 3 | 疫苗临近：待种 + 7 天内未到月龄剂次，今天卡与行程 Tab 置顶，点击跳成长页 | ✅ 代码完成 | `Vaccines.nearDoses` 单测覆盖（含第 7 天/第 8 天边界、DONE/SKIPPED 不进） |
| 4 | 成长页早教中心卡：今日推荐 + 本周打卡次数 | ✅ 代码完成 | 推荐与今天页同算法（EduKit），本周 = 最近 7 天（含今天） |
| 5 | 纯函数单测全绿 + assembleDebug | ✅ | 64 单测（55 旧 + 9 新）全绿 |

真机冒烟未跑（与 0003~0007 同欠，一起列清单）。

## 交付内容

- `domain/EduKit.kt`：四类 28 项 0~12 月项目库（不可变常量，顺序即算法依赖）+ `pool(monthAge)` + `picks(pool, date, yesterdayTitles)`（epochDay 确定性轮转）
- `domain/SupplementKit.kt`：`activePlans`（滤停用/坏 payload）+ `todayTasks`（时间点排序展开，done = 名字+planTime+落今天）
- `domain/Vaccines.kt`：+`nearDoses`（DUE ∪ 7 天内 NOT_DUE，按计划日升序）
- `domain/AgeCalc.kt`：+`monthAge`（周岁月差，Q8）
- `ui/agenda/TodayCards.kt`（新）：`buildTodayTasks`（三来源一次算好）+ `findSuppLog/findEduCheckin`（页面查 existing 传 VM）+ `VaccineNearCard`（置顶，点击跳成长页）/ `SupplementTodayCard`（剩 N 顿 + 管理入口）/ `EduTodayCard` + `EduCenterCard`（成长页，本周打卡数）
- `ui/agenda/SupplementPlanSheet.kt`（新）：方案列表弹层（停用开关、停用灰显置底）+ 编辑/新建弹层（预置 chips、常用时间点 chips、自定义 HH:mm、开始日期）；两层状态机在页面
- `ui/edu/EduSheet.kt`（新）：早教打卡编辑（时长选填 + 时间），挂 `RecordSheet.Edu` 进 RecordSheetHost
- `ui/today/TodayPage`：SummaryCard 后插三来源卡（为空不占位）+ 方案管理弹层；`+onOpenGrowth`
- `ui/agenda/AgendaPage`：三卡置顶（今天段之前）+ 方案管理弹层；`+onOpenGrowth`；`AgendaViewModel` 扩成聚合流（board + 四类记录全量）
- `ui/growth/GrowthPage`：疫苗进度卡下加早教中心卡；`GrowthViewModel` +EDU_CHECKIN 流
- `TodayViewModel`：UiState + 三类记录全量（不随日期重订阅）；写方法 8 个（方案 save/update/toggle停用、打卡 toggle、早教 toggle/save/update）

## 与设计的偏离

1. **方案弹层文件放 `ui/agenda/`**（设计写 `ui/supplement/`）——只在行程/今天页用且复用 `AgendaSheet.DateRow`（private → internal），单开包不值
2. **`updateEduCheckin(e, minutes, occurredAt)` 不带 category/item**（设计签名全参）——从原 payload 带回，与 updateVaccineDose 同模式，也省了 RecordSheetHost 解码
3. `EduCenterCard` 放在 `TodayCards.kt`（成长页用）——复用同文件的 CheckCircle，避免跨包可见性修饰
4. 测试 64（预期 ~63）：EduKit 4 + SupplementKit 4 + nearDoses 1

## 踩坑记录

- 测试 helper `millis()` 的 `hour` 忘给默认值——日期型调用点（startDate 全 0 点）全部报错；可变参数习惯性只默认了 minute
- nearDoses 第三组断言漏了 ipv1：空记录下它同样 DUE（计划 10-10 ≤ 11-03），「空记录只算将来」是想当然——到期判定与记录无关

## 已知边界

- 方案改名后旧 log 匹配不上、同名方案打卡互串（RequireSpec 已记录，V1 接受）
- 提醒通知（FR-13）延后，时间点目前只是打卡口径
- 早教 ≥13 月卡消失，历史打卡仍在时间轴；超龄池子待扩充
- SUPPLEMENT_PLAN 记录会在开始日时间轴出现一行（作为普通记录展示，无编辑入口）
