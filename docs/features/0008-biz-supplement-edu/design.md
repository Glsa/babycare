# design · 补剂 + 早教 + 行程回填

对应 RequireSpec Q1~Q9。套路不变：domain 纯函数 + TodayViewModel 单一写入口 + 各页读流。

## 1 数据模型

不加字段，全部用 0002 定义的 payload：

- 方案 = `SUPPLEMENT_PLAN` 一条（`name/dose/timePoints[]/startDate/stopped`），occurredAt = startDate
- 打卡 = `SUPPLEMENT_LOG` 一条（`name/takenAt/planTime`）
- 早教打卡 = `EDU_CHECKIN`（`category/item/minutes?`）

## 2 domain 纯函数（新增三个，Vaccines 加一个）

```kotlin
// EduKit.kt —— 项目库 + 推荐
data class EduItem(val category: String, val title: String, val minMonth: Int, val maxMonth: Int)
object EduKit {
    val items: List<EduItem>          // 附录 B 细化的完整库（约 26 项，0-1/2-3/4-5/6-7/8-9/10-12 六段 × 四类）
    fun pool(monthAge: Int): List<EduItem>
    fun picks(pool, date: LocalDate, yesterdayTitles: Set<String>): List<EduItem>
    // 条数 = 2 + (epochDay % 2)，2/3 按日轮换；池子按 epochDay 取模轮转起点；
    // 先排除昨天打卡过的，排除后不足条数就允许重复（Q4）
}

// SupplementKit.kt —— 当日任务推导
data class SuppTask(val entity: RecordEntity, val plan: SupplementPlanPayload,
                    val timePoint: String, val done: Boolean)
object SupplementKit {
    fun activePlans(records): List<Pair<RecordEntity, SupplementPlanPayload>>   // !stopped，坏 payload 跳过
    fun todayTasks(plans, today: LocalDate, todayLogs: List<RecordEntity>, zone): List<SuppTask>
    // startDate ≤ today 才出任务；timePoints 排序展开；done = log(name 相同 && planTime 相同 && takenAt 落今天)（Q2/Q3）
}

// Vaccines.kt 增补
fun nearDoses(views: List<DoseView>, today: LocalDate): List<DoseView>
// 待种（已到期）∪ 未到月龄且计划日 ≤ today+7，按计划日升序（Q7）
```

`AgeCalc` 补 `monthAge(birthday, today): Int`（年×12+月，Q8；monthAgeText 内部同口径，顺手复用）。

## 3 TodayViewModel 写入口与状态扩展

```kotlin
// 状态：TodayUiState 增加 supplementPlans / vaccineRecords / eduCheckins（三个 observeByType，
// 不随日期重订阅，直接在外层 combine；eduCheckins 全量拿，页面内存滤今天/昨天）
// 今日打卡记录本来就在当日 records 里，不另加流

fun saveSupplementPlan(name, dose, timePoints: List<String>, startDate: Long)
fun updateSupplementPlan(e, name, dose, timePoints, startDate, stopped)   // 编辑不改停用态，停用走 toggle
fun toggleSupplementStopped(e)
fun toggleSupplementLog(existing: RecordEntity?, name: String, planTime: String)
// existing 非 null → 软删（取消打卡）；null → 新增 log，takenAt=now、occurredAt=now
fun toggleEduCheckin(existing: RecordEntity?, category: String, item: String)
fun saveEduCheckin(category, item, minutes: Int?, occurredAt: Long)   // EduSheet 新建
fun updateEduCheckin(e, category, item, minutes, occurredAt)
```

existing 由页面从当日 records 里查好传入（VM 不重复查）。

## 4 UI · 行程 Tab（回填主战场）

**AgendaViewModel** 重构为聚合读流：

```kotlin
data class AgendaUiState(loaded, baby, board, plans, logs, vaccineRecords, eduCheckins)
// combine(baby, agendas, SUPPLEMENT_PLAN, SUPPLEMENT_LOG, VACCINE_DOSE, EDU_CHECKIN)
// board 仍在 map 里算；today 定格口径不变
```

**AgendaPage** LazyColumn 结构（FR-12 顺序）：

```
行程 标题
[VaccineNearCard]   待种∪7天内，最多 3 条，行点击 → onOpenGrowth()
[SupplementTodayCard]  进行中方案 × 时间点，行点按打勾/取消；右上「管理」
[EduTodayCard]      今日推荐 2~3 项，行点按打卡/取消
—— 今天 · 行程（原有 AgendaRow 列表）
—— 即将到来 / 未来计划（不动）
FAB ＋（不动）
```

前两张卡空则不渲染（疫苗没临近、补剂没方案时页面与现在一致）。`onOpenGrowth: () -> Unit` 参数由 BabycareApp 传 `{ selected = 3 }`。

**SupplementPlanSheet**（ui/supplement/，两层）：

- `PlanListSheet`：进行中（停用的灰显）方案列表，行内停用开关（Switch）；底部「＋ 新建方案」；点行 → 编辑
- `PlanEditSheet`：预置 chips（VD3·400IU / AD / DHA / 铁 / 钙 / 锌）+ 自定义名；剂量文本；时间点 chips（08:00/10:00/12:00/15:00/18:00/21:00）+ 自定义 HH:mm 追加（正则校验）；开始日期 DatePicker（默认今天）；至少 1 个时间点才能保存；编辑态多「删除方案」

## 5 UI · 今天页（今日行程卡扩展）

TodayPage 在 TodayAgendaCard 之前插三张卡（同行程 Tab 同款组件、同 order）：VaccineNearCard → SupplementTodayCard → EduTodayCard → TodayAgendaCard（行程）。空卡不渲染；疫苗卡行点击 `onOpenGrowth`（TodayPage 加参数，BabycareApp 传 `{ selected = 3 }`）。

复用组件放 `ui/agenda/TodayCards.kt`：`VaccineNearCard(doses, onOpenGrowth)`、`SupplementTodayCard(tasks, onToggle, onManage)`、`EduTodayCard(picks, doneTitles, onToggle)`。卡片样式抄 TodayAgendaCard（Surface 20dp、头部 emoji+标题+计数、行内打勾圆钮复用 AgendaRow 的视觉）。

## 6 UI · 成长页早教中心卡 + EduSheet

- GrowthViewModel 加 `eduCheckins` 流；GrowthPage 在疫苗进度卡后插「🎨 早教中心」卡：本月推荐（同 EduKit.picks，行点按打卡/取消，走 todayVm.toggleEduCheckin）+ 底部「本周打卡 N 次」（近 7 天 EDU_CHECKIN 计数）
- `EduSheet`（ui/edu/，挂 RecordSheetHost）：类目与项目名展示（带出）、时长分钟（选填数字）、TimeRow；editableSheet 加 EDU_CHECKIN 映射 → 时间轴 🎨 详情可编辑时长/时间

## 7 接线

- `BabycareApp`：AgendaPage 加 `onOpenGrowth`；TodayPage 加 `onOpenGrowth`；两个页面传 `{ selected = 3 }`
- `RecordSheets.kt`：`RecordSheet.Edu(editing)` + editableSheet 的 EDU_CHECKIN 映射 + host case；SUPPLEMENT_LOG 不加编辑映射（详情只读+删除）
- `Timeline.kt`：无改动（💊/🎨 文案已有）

## 8 测试（预期 55 → ~63）

- `EduKitTest`：pool 按月龄取段（0/2/5/12，13 空）；picks 确定性（同日两次同结果）、条数 2/3 轮换、避昨天、池子小允许重复
- `SupplementKitTest`：activePlans 滤停用与坏 payload；todayTasks 未开始方案不出、时间点排序、done 按名字+时间点+当天 log 判定（跨午夜边界）
- `VaccinesTest` 补 nearDoses：待种（已到期）含、7 天内未到月龄含、第 8 天不含、已种/不接种不含

## 9 风险与边界

- TodayUiState 三条新流是全量表（方案/疫苗/早教打卡），量级个人应用可忽略
- 方案改名/同名方案匹配边界（RequireSpec 已列）；补剂 log 的 occurredAt=打卡瞬间，日历回看按打卡时间落位（漏服当天补打也落当天，符合直觉）
- 早教推荐确定性依赖 epochDay 与池子顺序稳定 → items 用不可变 listOf 常量，别改成动态构建
