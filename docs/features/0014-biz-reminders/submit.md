# 0014 提醒实装 · 交付说明

## 交付内容

FR-13 提醒全部实装：疫苗（前 3/前 1/当天 9:00）、补剂（方案时间点）、行程（提前量可设）、关键节点（满月/百天/半岁/周岁前一天）、喂奶间隔到期（0011 喂奶提醒提前到本期完成）。总开关 + 5 分项开关在「我的 → 提醒设置」，行程编辑面板加提前提醒 chips。通知点击直达对应 Tab。

## 落点

| 文件 | 内容 |
| --- | --- |
| `domain/ReminderPlanner.kt` | 纯函数规划：未来 7 天窗口内全部提醒 + due() 到点过滤 + ReminderSettings 开关模型 |
| `remind/ReminderCenter.kt` | 单闹钟链：reschedule = 规划 → 发 due 窗口内(−15min,+60s] → 挂最近一条 `setAndAllowWhileIdle`；通知 id = dedupeKey.hashCode + setOnlyAlertOnce 防重发轰炸；喂奶通知随新一次喂奶自动取消 |
| `remind/ReminderReceiver.kt` | 闹钟到点重排（goAsync）+ BootReceiver 开机恢复 |
| `ui/mine/RemindersSheet.kt` | 设置面板：总开关 + 5 分项，首开请求通知权限（33+） |
| `ui/agenda/AgendaSheet.kt` | 提前提醒 chips（当天/前 1 天/前 3 天），onSave 加第 5 参 |
| `BabycareApplication` / `MainActivity` / `BabycareApp` | reminderCenter 装配启动；通知 EXTRA_TAB → pendingTab → 切 Tab |
| `AndroidManifest.xml` | RECEIVE_BOOT_COMPLETED + 两个 receiver |

## 设计要点

- **单闹钟链**：任意时刻只挂最近一条提醒的闹钟，到点发完再挂下一条。重排时机：启动、repo.changes、设置变化、闹钟到点、Widget 兜底闹钟 tick、开机。无需批量 PendingIntent 管理。
- **喂奶防轰炸**：不落去重表——dedupeKey 当通知 ID + setOnlyAlertOnce，重发是静默替换；prefs 只记 last_feed_notif_key 用于换了新一顿后取消旧通知。
- **超时钳制**：喂奶已超时的 spec at=now+1s，既过 plan 的 at>now 过滤又落在 due 窗口立即发。
- **due 宽容窗**：过期 >15min 不补发（设备关机漏掉的早报不轰炸），≤60s 的将到也发。

## 踩坑

- 里程碑初版「满月 to 1L」= 生日+1 天，语义错；改为 plusMonths(1)（半岁/周岁同理），百天保持 +100 天。
- 测试断言纠偏：补剂方案「未开始」不是全排除，而是从 startDate 起排——这是规划器的正确行为，改的是测试。

## 验证

- `:app:testDebugUnitTest` 103 条全绿（新增 ReminderPlannerTest 9 条：疫苗窗口/已种排除、补剂多时点/停用/未开始、行程提前量/完成排除/过期触发日、节点前 1 天、喂奶到期/超时/无记录、总/分开关、due 窗口）。
- `:app:assembleDebug` 通过。
- 真机冒烟待跑（通知权限弹窗、灭屏到点、重启后恢复、点通知跳 Tab）。
