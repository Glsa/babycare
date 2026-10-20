# 0013 · 今天页重排 + 每日必做 — 交付说明

日期：2026-10-18 · 结果：完成，96 单测全绿（+6 DailyKitTest + roundtrip 补 2 类型），assembleDebug 通过

## 交付内容

- **今天页重排**（Q1）：概要 → 进行中睡眠/亲喂 → 快捷记录 → **每日必做（新）** → 今日补剂 → 今日早教 → 疫苗临近 → 今日行程 → 今日记录 → 时间轴
- **每日必做**（Q3/Q4）：records 新类型 `daily_task`（任务定义）/`daily_task_log`（当日打卡，dateKey=yyyy-MM-dd）；首次启动预置 5 项（维AD/洗脸/排气操/黑白卡/听力训练，meta 标记只 seed 一次，删光不塞回）；点圆钮勾选/取消（toggle 软删模式）；「管理」弹层支持删除与添加，无重命名
- **行程页去重**（Q2）：删除行程页的今日早教卡（今天页已有），三来源卡剩疫苗 + 补剂；成长页早教中心保留（Q6）

## 落点

| 文件 | 内容 |
| --- | --- |
| `data/model/RecordTypes.kt` / `Payloads.kt` | DAILY_TASK / DAILY_TASK_LOG 注册 + 编解码分支 |
| `domain/DailyKit.kt`（新） | DEFAULT_TASKS / dayLog / sortedTasks / nextSortOrder / taskPayload |
| `ui/today/TodayViewModel.kt` | state +dailyTasks/dailyLogs；5 路 combine 嵌套合并；init seed；toggle/add/delete |
| `ui/agenda/TodayCards.kt` | DailyTasksCard（复刻补剂卡样式，不截断） |
| `ui/today/DailyTaskSheet.kt`（新） | 管理弹层（删除 + 添加） |
| `ui/today/TodayPage.kt` | LazyColumn 重排 + 卡片接线 |
| `ui/agenda/AgendaPage.kt` | 删早教卡块 |

## 设计要点回顾

- 打卡跨天自动复位：dateKey 过滤，`dayFlow` 跨天重订阅双保险
- 删除任务 = 软删，历史打卡保留（备份/同步闭环）
- 每日必做卡不进时间轴（Timeline 不加分支，未知类型自动跳过）

## 踩坑

- `return@forEach` 写在嵌套 Composable lambda（如 `Row { Text(x ?: return@forEach) }`）里会报 "'return' is prohibited"——先在 forEach 体内解出变量再进 Composable。
- ModalBottomSheet 文件需 `@file:OptIn(ExperimentalMaterial3Api::class)`（与既有 sheet 一致）。

## 待验证

真机冒烟（0003~0013 攒一批一起跑）：重排后首屏、勾选/取消/管理弹层、跨天复位、行程页无早教卡。
