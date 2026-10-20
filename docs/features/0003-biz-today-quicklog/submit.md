# submit · 今天页 + 快捷记录 + 首启档案

> 2026-10-18 交付。需求基线：[RequireSpec](RequireSpec.md) · 设计：[design](design.md)

## 功能点勾对验收标准

| 验收标准（RequireSpec） | 结果 |
| --- | --- |
| 1. 首启无档案出现引导页，建档后头部显示第 N 天 + 月龄 | ✅ 代码路径：`observeBaby()` 为空 → `OnboardingScreen`；建档写 baby_profile 记录 + `active_baby_id`；头部徽章走 AgeCalc。**真机冒烟未跑**（无连接设备） |
| 2. 五区块齐全、Room Flow 实时更新；距上次喂奶跨天正确 | ✅ `observeDay` + `observeLastFeed`（不限于今天）双 Flow；30s ticker 刷新「距上次喂奶/睡眠进行中」；`TodayUiState.loaded` 防首启闪引导页 |
| 3. 奶粉 2 击 / 尿布 2 击 / 洗澡 1 击，保存即时刷新 | ✅ 洗澡一击直存 + Snackbar；奶粉六档点选即存；尿布三选一点击即存 |
| 4. 时间轴可编辑（五类）/ 可删除（确认） | ✅ `DetailSheet` 删除走 AlertDialog 二次确认 + 软删；`editableSheet()` 只对五类给编辑入口，其余「暂不支持编辑」 |
| 5. TimeKit / Timeline / BabyInfo 表驱动测试全绿；assembleDebug 通过 | ✅ 6+2+5=13 用例；全套 29 用例 0 失败 0 错误；BUILD SUCCESSFUL |

## 文件清单

```
app/src/main/java/com/babycare/app/
├── data/dao/RecordDao.kt              # +observeLast(babyId, types)
├── data/repo/BabyRepository.kt        # +observeLastFeed（不限今天，跨天也对）
├── domain/TimeKit.kt                  # 时长/日期文案、日界、夜觉判定、距上次喂奶
├── domain/BabyInfo.kt                 # 档案解析（坏数据返回 null 不崩）
├── domain/Timeline.kt                 # 12 类型 → 时间轴条目模型
└── ui/
    ├── today/TodayViewModel.kt        # 状态聚合 + 30s ticker + 保存/编辑/删除/建档
    ├── today/TodayPage.kt             # 头部 + 概要 + 快捷钮 + 时间轴 + 弹层宿主
    ├── today/TodayComponents.kt       # 概要卡/快捷钮/时间行/分钟步进器等
    ├── today/RecordSheets.kt          # 五弹层 + 详情 + TimePointDialog（今天/昨天）
    └── onboarding/OnboardingScreen.kt # 首启建档（昵称/生日/性别）

app/src/test/java/com/babycare/app/domain/  # TimeKit/Timeline/BabyInfo 共 13 用例
```

## 实现要点（与设计一致）

- **亲喂/睡眠手动补录先行**：分钟数/时长档 + 结束时间倒推开始时间；真计时器（前台服务 + 时间戳恢复）留给 0004
- **睡眠 occurredAt = startAt**：夜觉归当晚，日汇总按开始日归属；夜觉/小睡按结束小时自动判（19:00–07:00 夜觉）
- **时间补录**：快捷档 + `TimePointDialog`（TimePicker + 今天/昨天切换，覆盖夜里喂奶改昨天）
- **activeSheet 单值状态机**：`sealed interface RecordSheet`，同一时刻至多一个弹层，编辑态带 `editing: RecordEntity?`
- **建档自引用**：baby_profile 记录的 babyId = 自身 uuid，meta 记 active_baby_id
- **删除 = 软删无撤销**：与同步语义一致（0001 的 tombstone 机制）

## 编译期修复（踩坑记录）

- Material3 当前版本 `DatePicker` 无 `dateValidator` 参数 → 改 `rememberDatePickerState(selectableDates = …)`
- `when` 作用在非密封 `RecordPayload` 上必须带 `else`（K2 报成 return type mismatch）
- 中文字符串模板 `"$v分"` 会把 `v分` 当标识符 → `"${v}分"`

## 测试覆盖与明确未覆盖

未覆盖：ViewModel/Compose UI 层（需 instrumented，交互逻辑薄——都是「状态 → UI」直映射）；Room DAO（同 0002，等真实数据流后随集成补）；真机冒烟（无设备，装 APK 后按验收 1–4 手工过一遍即可）。

## 遗留与后续

1. **0004 · 计时类**：亲喂/睡眠前台服务计时器 + 时间戳恢复 + 进行中卡片
2. 行程卡（今天页预留位）随行程 feature 回填
3. 早教/生长/疫苗/补剂的录入 UI 随成长页 feature
