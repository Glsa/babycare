# 0012 · 瓶喂（奶粉/母乳） — 交付说明

日期：2026-10-18 · 状态：已完成

## 验收对照（Request 验收 4 条）

1. 弹层改「瓶喂」+ 奶种 chips（默认奶粉，可切母乳），两种正确落库；时间轴 `🍼 奶粉 120 ml` / `🥛 母乳 120 ml` ✅
2. 旧数据（无 kind 字段）解码为奶粉，编辑旧记录可改奶种 ✅
3. 母乳瓶喂计入今日奶量/次数（`FeedFormulaPayload` 类型不变，DaySummary 求和天然包含）、重置喂奶倒计时（observeLastFeed 本就含瓶喂）✅
4. 单测 90 全绿（TimelineTest +1 母乳文案断言、+1 旧 JSON 前向兼容；PayloadsTest 期望值改命名参数）✅

## 交付内容

| 文件 | 改动 |
| --- | --- |
| `data/model/Payloads.kt` | `FeedFormulaPayload` + `kind` 字段（默认 `formula`），companion 常量 `KIND_FORMULA`/`KIND_BREAST` |
| `ui/today/RecordSheets.kt` | FormulaSheet 标题「瓶喂」+ 奶种 FilterChip；onSave 带 kind |
| `ui/today/TodayViewModel.kt` | saveFormula / updateFormula 加 kind 参数 |
| `domain/Timeline.kt` | 按 kind 出 emoji 与标题 |
| `ui/today/TodayComponents.kt` | 快捷钮与概要「奶粉」→「瓶喂」 |
| `widget/QuickWidget.kt` / `OverviewWidget.kt` | 圆钮 label「瓶喂」（深链 key 不变） |
| `domain/DaySummary.kt` | 口径注释更新（计算不变） |

## 偏离设计

无（type key、深链 key、备份/同步全不动，纯 payload 加字段）。

## 已知边界

- 统计不分奶种列示（今日奶量合并计），将来统计页（V1.1）可按 kind 分列，数据已具备。
- Widget 圆钮 emoji 固定 🍼（打开弹层再选奶种），不因上次选择记忆——记忆属低频需求，先不做。
- 旧版本 App 读新数据（kind 字段被 ignoreUnknownKeys 忽略）显示为奶粉——仅 V1.1 多设备场景且旧端不常见，可接受。
