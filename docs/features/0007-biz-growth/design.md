# design · 成长（生长 + 疫苗）

对应 RequireSpec 澄清结论 Q1~Q7。写操作仍走 TodayViewModel（单一写 VM 模式），读流各自 ViewModel。

## 1 数据模型

- `GrowthPayload` 不动（weightKg/heightCm/headCm 可空 + source，够用，Q7 不加备注）。
- `VaccineDosePayload` 加两个字段（默认值编码、ignoreUnknownKeys 解码，前向兼容无感）：
  - `key: String = ""` —— 对应计划标识（Q3）；空 = 旧数据，按疫苗名+剂次兜底
  - `status: String = "done"` —— `done` 已种 / `skipped` 不接种（Q4）
- 计划**不落库**（Q2）：22 剂是常量表 + 生日推导，Room 里只有用户操作产生的 VACCINE_DOSE 记录。

## 2 domain/Vaccines.kt（纯函数）

```kotlin
data class DosePlan(
    val key: String, val vaccine: String, val dose: Int,
    val date: LocalDate,          // 计划接种日（按生日月龄推）
    val monthLabel: String,       // 出生 / 1月 / … / 6岁
)
enum class DoseStatus { NOT_DUE, DUE, DONE, SKIPPED }
data class DoseView(val plan: DosePlan, val status: DoseStatus, val record: RecordEntity?)

object Vaccines {
    // 附录 A 全 22 剂，顺序即表序：出生(乙肝①、卡介苗) → 1月(乙肝②) → … → 6岁(白破加强、A+C流脑②)
    fun doses(birthday: LocalDate): List<DosePlan>          // 出生剂 = 生日当天，其余 plusMonths
    fun views(birthday: LocalDate, today: LocalDate, vaccineRecords: List<RecordEntity>): List<DoseView>
    // 匹配：records 解码 VACCINE_DOSE payload，key 精确匹配，无 key 老数据按 (vaccine, dose)
    // 状态：有记录 → DONE/SKIPPED；无记录按计划日 vs today → DUE / NOT_DUE
    fun progress(views: List<DoseView>): Pair<Int, Int>     // (已种数, 总数 22)，skipped 不计
    fun nextDose(views: List<DoseView>): DoseView?          // 未完成里计划日最早的（Q6）
}
```

记录匹配时同 key 多条（极端：同步冲突复制）取 updatedAt 最新的，其余忽略。

## 3 domain/GrowthKit.kt（纯函数）

```kotlin
data class GrowthLatest(
    val weight: Pair<RecordEntity, GrowthPayload>? = null,  // 记录 + payload
    val height: Pair<RecordEntity, GrowthPayload>? = null,
    val head: Pair<RecordEntity, GrowthPayload>? = null,
)
object GrowthKit {
    fun latest(records: List<RecordEntity>): GrowthLatest   // 各项取 occurredAt 最新且值非空的一条
    fun history(records: List<RecordEntity>): List<Pair<RecordEntity, GrowthPayload>> // 倒序（新在前）
}
```

## 4 TodayViewModel 写入口

```kotlin
fun saveGrowth(weightKg: Double?, heightCm: Double?, headCm: Double?, source: String, occurredAt: Long)
fun updateGrowth(e, weightKg, heightCm, headCm, source, occurredAt)
fun saveVaccineDose(vaccine, dose, key, date: Long, location, note)          // status=done
fun markVaccineSkipped(vaccine, dose, key, planDate: Long)                    // status=skipped
fun updateVaccineDose(e, date, location, note)                                // key/status 从原 payload 带回
```

疫苗记录 occurredAt = 接种日期（时间取 10:00 与体检口径一致，避免零点时区边角）；删除复用 `delete(entity)`，删完状态自然回到 DUE/NOT_DUE。

## 5 UI · ui/growth/

**GrowthViewModel**：`combine(baby, repo.observeByType(GROWTH), repo.observeByType(VACCINE_DOSE))` → `GrowthUiState(loaded, baby, growthRecords, vaccineRecords)`。剂量视图在页面 `remember` 派生（today 进页面定格，与日历/行程同口径）。

**GrowthPage**（替换占位，接 BabycareApp）：

```
第 N 天 · X 个月          ← 简头部（AgeCalc）
[生长数据卡] 体重 6.4kg·10月12日 | 身高 62.5cm | 头围 40.8cm  [记一笔]
[生长记录列表] 每条：📏 数值 · 来源 · 日期，点击 → Detail
[疫苗进度卡] 已种 4/22 ▓▓░░ + 下一剂：脊灰②（口服）· 11月10日 · 24 天后
[剂次列表] 22 行：乙肝疫苗 第1剂 · 出生 | [已种 10月10日]/[待种]/[未到月龄]/[不接种]
             点击 → 未种弹录入；已种弹编辑
```

- 生长记录行复用 `TimelineRow`（buildTimelineItems 已支持 GROWTH 文案）。
- 剂次行状态样式：已种 = 疫苗色实底 chip + 接种日；待种 = 主色 chip；未到月龄 = 灰 chip；不接种 = 灰删除线文本。

**GrowthSheet**（挂进 RecordSheetHost，模式同 AgendaSheet）：

- 三个输入框：体重（克，整数，占位「如 6400 ≈ 6.4kg」）、身高 cm（小数）、头围 cm（小数），至少填一项才能保存
- 来源 chips：在家 / 体检
- TimeRow（今天/昨天 chips），新建默认现在、编辑带原值
- 编辑入口：时间轴 Detail → 编辑（editableSheet 加 GROWTH 映射）

**VaccineSheet**（成长页与时间轴共用）：

- 标题自动「乙肝疫苗 · 第 1 剂」（plan 带出或编辑回填，不可改）
- 日期行（DatePickerDialog，**必填**，默认今天；编辑带原值）+ TimePointDialog 调时分
- 接种点、备注/批号（选填）
- 未种路径（从剂次行点入）：主按钮「记录已种」+ 次按钮「不接种」；不接种不弹日期直接落库（日期取计划日）
- 编辑路径（RecordSheet.Vaccine(entity)）：主按钮保存；删除走 Detail

## 6 接线

- `BabycareApp`：growthVm factory；Tab 3 → `ui.growth.GrowthPage(todayVm, growthVm, snackbar, modifier)`；Placeholders 删 GrowthPage
- `RecordSheets.kt`：`RecordSheet.Growth(editing)` / `RecordSheet.Vaccine(editing)` + editableSheet 两条映射 + host 两个 case；疫苗未种路径由 GrowthPage 直持 VaccineSheet（带 plan 预填），不经 RecordSheet 枚举
- `BabyRepository`：无新增（observeByType 已通用）
- `Timeline.kt`：疫苗行补「不接种」标记（`status=="skipped"` 时文案后缀）

## 7 测试

- `VaccinesTest`：22 剂数与表序；日期推导（生日当天 / +1月 / +3月 / +72月）；status 判定四态（有记录 done/skipped、无记录按计划日前后）；key 匹配 + 无 key 兜底；skipped 不计入 progress；nextDose 取最早未完成
- `GrowthKitTest`：latest 各项独立取最新非空值；history 倒序；坏 payload 跳过
- 预期 +7~8 个用例，46 → ~54

## 8 风险与边界

- 生日改动：排期随之变（推导），已种记录 key 不变仍能对上；体检行程那边已有确定性 uuid 逻辑不受影响
- 同步：VACCINE_DOSE 记录照常同步；计划两端各自推导，无一致性问题
- 时间轴里疫苗/生长记录在 22 剂之外的自建情况：V1 无自建入口，不处理
