# 0012 · 瓶喂（奶粉/母乳） — 需求录入 + 澄清

日期：2026-10-18 · 来源：用户口头需求 · 规模小，Request/RequireSpec 合并

## 需求

「奶粉喂养」改成「瓶喂」，瓶喂分两种奶：**奶粉**（配方奶）和**母乳**（吸出装瓶喂）。

## 拍板

| # | 问题 | 拍板 | 理由 |
| --- | --- | --- | --- |
| Q1 | 记录类型 key 改不改 | **不改**：`feed_formula` 继续代表「瓶喂」，payload 加 `kind` 字段（`formula`/`breast`，默认 `formula`） | 存量数据零迁移自动归为奶粉；备份/同步/Widget 深链键全不用动 |
| Q2 | 展示文案 | 奶粉 `🍼 奶粉 120 ml`、母乳 `🥛 母乳 120 ml`；弹层标题「瓶喂」 | 一眼区分两种奶；emoji 与现有语义一致 |
| Q3 | 入口文案 | 快捷条/Widget 圆钮「奶粉」改「瓶喂」（点开再选奶种），深链 key `formula` 不变 | 入口表达品类，奶种在弹层里选 |
| Q4 | 统计口径 | 今日奶量/次数把母乳瓶喂一并计入（毫升同质累加） | 喂了多少毫升才是关心的量；将来统计页再分列 |
| Q5 | 喂奶间隔倒计时 | 母乳瓶喂同样重置倒计时（observeLastFeed 本就含瓶喂类型） | 瓶喂不分奶种，都是「开始喂奶」 |

## 改动面

| 文件 | 改动 |
| --- | --- |
| `data/model/Payloads.kt` | `FeedFormulaPayload` + `kind: String = KIND_FORMULA`（companion 常量） |
| `ui/today/RecordSheets.kt` | FormulaSheet 标题「瓶喂」+ 奶种 FilterChip；保存/编辑带 kind |
| `ui/today/TodayViewModel.kt` | saveFormula / updateFormula 加 kind 参数 |
| `domain/Timeline.kt` | 时间轴按 kind 出 🍼奶粉 / 🥛母乳 |
| `ui/today/TodayComponents.kt` | 快捷钮「瓶喂」；概要「瓶喂 N 次」 |
| `widget/QuickWidget.kt` / `OverviewWidget.kt` | 圆钮 label「瓶喂」 |
| `domain/DaySummary.kt` | 注释口径更新（计算不变） |

## 验收

1. 记一笔瓶喂：默认选中奶粉，可切母乳；两种分别正确落库、时间轴正确显示 🍼/🥛。
2. 旧数据（无 kind）全部显示为奶粉，编辑旧记录可改奶种。
3. 母乳瓶喂计入今日奶量与次数，并重置喂奶倒计时。
4. 单测：kind 默认值前向兼容 + 时间轴两种文案。
