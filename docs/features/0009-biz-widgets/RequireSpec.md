# RequireSpec · 桌面 Widget 三款

需求来源：PRD FR-14（行为约定逐条对照）、UI 设计说明 §4/帧 08、`docs/UI设计说明.md` §6 实现注记。

## FR-14 原文要点

| 款式 | 尺寸 | 内容 |
| --- | --- | --- |
| 快捷记录 | 2×2 | 第 N 天 + 今日奶量摘要 + 4 大按钮（奶粉/亲喂/尿布/睡眠） |
| 今日概览 | 2×4 | 左：奶量/睡眠/大小便今日汇总；右：6 快捷键（+洗澡、生长） |
| 睡眠计时 | 4×2 | 😴 进行中 + 已睡时长实时跳 + 开始/结束大按钮 + 距上次喂奶 |

行为约定：按钮后 ≤1 次附加点击完成记录（尿布三选一/奶粉快捷档微窗；亲喂/睡眠直接开始计时）；进行中的计时常驻显示；深夜配色与 App 暗色一致。

## 澄清结论（设计拍板）

| # | 问题 | 结论 |
| --- | --- | --- |
| Q1 | 今日概览 PRD 写 2×4，但内容是「左汇总/右快捷键」两列，2 格宽放不下 | 尺寸笔误，按 4×2（宽横）做，与帧 08 摆放一致；三款最终为 2×2 / 4×2 / 4×2 |
| Q2 | 「微窗」怎么做？Glance 原生 dialog 支持弱、机型兼容差 | 不做原生微窗：奶粉/尿布/亲喂/生长 → 深链开 App 直弹对应弹层（App 内 1 击完成，满足 ≤1 附加点击）；**睡眠开始/结束在 widget 后台直接完成（0 附加点击，与通知按钮同路径）**；洗澡 → 开 App 即自动记一笔（与 App 内快捷条同口径，误触可删） |
| Q3 | 亲喂要选边，widget 上直接开始吗？ | 不默认边：深链弹 NursingStart 选边开始（仍是 1 次附加点击） |
| Q4 | 刷新机制？ | 两层：① `BabyRepository` 增加 changes 流（add/update/softDelete 后 emit），`WidgetRefresher` collect 后即刷三款——App 进程内任何记录/计时变更（含 TimerCenter，它全走 repo）秒级生效；② AlarmManager 兜底：睡眠进行中每 60 秒（时长跳动），常态每 30 分钟（跨天摘要重置、进程被杀后校准），App 冷启动时重挂 |
| Q5 | 暗色怎么判？ | Glance `ColorProviders(亮, 暗)` 跟随系统深色模式，与 App（`isSystemInDarkTheme`）同口径；色值直接复用 `Light/DarkBabyColors` |
| Q6 | 没建档时 widget 显示什么？ | 三款显示占位文案「先在 App 里给宝宝建档」，点击开 App |
| Q7 | 深链路由怎么落？ | MainActivity 设 `singleTop`，onCreate/onNewIntent 解析 extra `sheet` → 应用级 PendingSheetBus（StateFlow）→ TodayPage 消费成初始弹层；键位：formula/diaper/nursing/bath/growth |
| Q8 | 技术选型确认 | Glance（`glance-appwidget` 1.1.1 稳定线）；不用 glance-material3（自定义色板直接映射）；`updatePeriodMillis=0` 全走 AlarmManager 统一管理 |
| Q9 | 预览与适配 | API 31+ 用 `previewLayout`（真布局预览）；26~30 简易色块 previewImage；`resizable` 关（固定尺寸）；密话/键盘锁 widget 不做 |

## 验收标准

1. 三款可从系统桌面添加，布局与帧 08 一致（2×2 / 4×2 / 4×2，亮暗两套）
2. 快捷记录：第 N 天 + 「780 ml · 8 次」口径摘要 + 四钮；睡眠钮未睡=开始（后台直接计时）/进行中=结束（后台直接落记录，标签按结束小时自动判）
3. 今日概览：左列奶量/睡眠/大小便汇总 + 右列 6 快捷键全部可达（≤1 附加点击）
4. 睡眠计时：进行中显示时长（分钟级跳动）+ 开始时间 + 距上次喂奶 + 结束钮；结束即时刷新
5. 记录变更后 widget 秒级刷新（App 进程存活时）；App 记完按 Home 回桌面看到新值
6. 新增纯函数单测全绿 + assembleDebug 通过

## 已知边界

- 睡眠时长分钟级跳动（秒级刷新耗电不做，UI 说明 §6 已拍板）
- 26~30 添加列表里预览图简陋（灰底图标级）
- widget 与 App 里的「亲喂计时」不展示在 widget（只在通知/App 内）——Q-5 拍板先三款
- App 进程被杀时，桌面 widget 数据停在上次值，最长 30 分钟后由兜底 alarm 校准
