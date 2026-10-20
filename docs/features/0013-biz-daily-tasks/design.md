# 0013 · 今天页重排 + 每日必做 — 技术设计

日期：2026-10-18 · 前置：Request（含拍板）已定稿

## 1. 数据模型

```kotlin
// Payloads.kt
@Serializable data class DailyTaskPayload(val name: String, val sortOrder: Int = 0) : RecordPayload
@Serializable data class DailyTaskLogPayload(val taskUuid: String, val dateKey: String) : RecordPayload
// RecordTypes：DAILY_TASK / DAILY_TASK_LOG，加入 ALL；encode/decode when 加分支
```

- dateKey = `LocalDate.toString()`（yyyy-MM-dd），打卡 `occurredAt` = 点击时刻。
- 时间轴不显示这两类（`Timeline.kt` 不加分支，未知类型自动跳过）。

## 2. 新增

### `domain/DailyKit.kt`（纯 JVM）

```kotlin
object DailyKit {
    val DEFAULT_TASKS = listOf("💊 吃维AD", "🧼 洗脸", "🤸 排气操", "⬛ 黑白卡", "👂 听力训练")
    fun dayLog(logs: List<RecordEntity>, taskUuid: String, dateKey: String): RecordEntity?
    fun sortedTasks(tasks: List<RecordEntity>): List<RecordEntity>   // 按 payload.sortOrder，稳定
}
```

### `ui/agenda/TodayCards.kt` 追加 `DailyTasksCard`

样式复刻 SupplementTodayCard：标题「✅ 每日必做」+ 管理 + 「剩 N 项」；行 = `CheckCircle`（复用）+ 名字（完成后置灰）；**不截断**（任务本来就该全可见）。

### `ui/today/RecordSheets.kt` 追加管理弹层

`DailyTaskManageSheet`：ModalBottomSheet，任务行（名字 + 🗑 删除，即时落库）+ 底部输入框「添加任务」+ 添加按钮。改名不做（Q8）。

## 3. 改动

| 文件 | 改动 |
| --- | --- |
| `ui/today/TodayViewModel.kt` | state + `dailyTasks/dailyLogs`；新流 `dailyFlow = combine(observeByType(DAILY_TASK), observeByType(DAILY_TASK_LOG))` 并进 `ui` 的 5 路 combine（kotlinx combine 上限 5，先两两合并再进）；init 里 Checkups 预置同位加 seed（meta `daily_tasks_seeded`，无标记且无任务才插默认 5 条）；`toggleDailyTask / addDailyTask / deleteDailyTask`（toggle 模式复用补剂：有当日 log 软删、无则新建） |
| `ui/today/TodayPage.kt` | LazyColumn 重排：概要 → 进行中计时 → **快捷记录** → **每日必做** → 补剂 → 早教 → 疫苗 → 行程 → 时间轴 |
| `ui/agenda/AgendaPage.kt` | 删 EduTodayCard 块（三来源卡剩疫苗/补剂，注释同步） |
| `data/model/Payloads.kt` / `RecordTypes.kt` | 新类型注册 |

## 4. 关键决策

- **combine 上限**：kotlinx.coroutines `combine` 最多 5 个直接参数；新增 2 流先合成一个 `Pair` 流再并进，避免 vararg 版的类型挤压。
- **seed 放 TodayViewModel init**：与 Checkups 体检预置同位（observeBaby → 判 meta 标记），行程页/成长页不依赖该逻辑也能看到任务（records 驱动）。
- **删除任务 = 软删**，历史打卡 log 保留（同步/备份闭环；任务不显示即无感知）。
- **打卡跨天**：dateKey 驱动，昨天勾了今天自然回到未完成（`dayFlow` 跨天重订阅 + dateKey 过滤双保险）。

## 5. 测试

`DailyKitTest`（+5 左右）：dayLog 命中/未命中（别的任务/别的日期）、sortedTasks 排序、DEFAULT_TASKS 非空且 5 项；Payloads 编解码回归（新类型 decode round-trip 由既有 encodePayload/decodePayload when 分支覆盖，补 2 条）。
