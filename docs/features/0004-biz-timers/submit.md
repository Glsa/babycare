# submit · 计时类：亲喂计时器 + 睡眠计时器（前台服务）

> 2026-10-18 交付。需求基线：[RequireSpec](RequireSpec.md) · 设计：[design](design.md)

## 功能点勾对验收标准

| 验收标准（RequireSpec） | 结果 |
| --- | --- |
| 1. 亲喂选边 1 击开始；换边/暂停/结束 1 击；记录与手动补录同口径 | ✅ 计时纯函数 `TimerCalc`（7 用例）；生成的 feed_nursing 记录走同一 payload，时间轴可编辑 |
| 2. 睡眠 1 击开始；结束弹层默认现在、标签自动判可改；跨天夜觉正确 | ✅ `StopSleepSheet`（结束时间可调、tag 跟随结束小时自动判）；`observeOngoingSleep` 不限当天，23:10 开始次日查看正常显示 |
| 3. 首页卡片 + 前台通知双处实时；通知按钮后台可用 | ✅ 卡片随 30s 心跳刷新；通知 chronometer 系统级走秒；动作按钮 PendingIntent → Service → TimerCenter |
| 4. 进程死亡不丢状态 | ✅ 状态在 Room/meta（服务自身无状态）；START_STICKY 重建 + VM 观察到计时中自动拉起服务。**真机杀进程场景未实测**（无设备） |
| 5. TimerCalc 表驱动 + JSON 往返全绿；assembleDebug 通过 | ✅ 36 用例 0 失败；BUILD SUCCESSFUL |

## 文件清单

```
app/src/main/java/com/babycare/app/
├── data/model/NursingTimerState.kt   # 亲喂计时暂态（meta 单行 JSON）
├── data/dao/MetaDao.kt               # +delete(key)
├── data/dao/RecordDao.kt             # +byType 单次查询
├── data/repo/BabyRepository.kt       # +进行中睡眠 / 亲喂暂态 读写
├── domain/TimerCalc.kt               # 换边/暂停/累计/落定 纯函数
├── timer/TimerCenter.kt              # 计时状态变更唯一入口
├── timer/TimerService.kt             # 前台服务：常驻通知 + 按钮动作分发
└── ui/today/
    ├── TimerCards.kt                 # 睡眠/亲喂 进行中卡片
    ├── TodayViewModel.kt             # +ongoingSleep/nursingTimer 状态与计时动作
    ├── TodayPage.kt                  # 卡片挂载 + 按钮联动 + 通知权限请求
    └── RecordSheets.kt               # +NursingStart/NursingTimer/StopSleep 三弹层

app/src/main/res/drawable/ic_timer.xml          # 通知小图标
app/src/test/.../domain/TimerCalcTest.kt        # 7 用例
AndroidManifest.xml                              # FGS specialUse + POST_NOTIFICATIONS
```

## 实现要点（与设计一致）

- **状态载体二分**：睡眠 = sleep 记录 `endAt=null`（DaySummary/Timeline/同步天然认它）；亲喂 = meta 单行 JSON（记录表只存完成事实）
- **TimerCenter 单一入口**：UI 与通知按钮都走它，先改状态再刷服务通知，无双路径漂移
- **暂停实现**：恢复时把 `sideStartAt` 平移过暂停区间，累计自然剔除暂停（无需分段存储）
- **服务无状态**：通知按 Room/meta 现算重建；无计时时补空前台满足 startForegroundService 契约后自灭
- **时间戳恢复双保险**：START_STICKY 重建 + TodayViewModel 观察到进行中就 `refreshService()`
- 通知动作上限 3：双计时并存时 = 换边 / 结束亲喂 / 结束睡眠（暂停进 App 操作）

## 明确未覆盖

前台服务/通知/权限属 instrumented 范畴（同 0003 口径）；真机冒烟清单：①杀 App 重开恢复计时 ②通知按钮后台结束 ③Android 14+ FGS 类型合规 ④双计时并存通知。

## 遗留与后续

1. **0005 · 日历页**（月视图 + 日详情回看）
2. widget 三款直接读 Room/meta 同一状态源（Glance，分钟级刷新）
3. 深夜态（帧 09）：睡眠卡置顶已有，差时间轴「今晚记录」过滤
