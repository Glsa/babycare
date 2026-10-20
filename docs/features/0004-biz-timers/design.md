# design · 计时类

## 1. 状态存哪（核心决策）

两条计时用**不同载体**：睡眠天然有「进行中」表达——sleep 记录 `endAt=null`，0003 的 DaySummary/Timeline 已支持该语义，计时状态直接就是记录本身；亲喂 payload 是「最终事实」（左右总分钟），塞计时字段会污染记录语义，改存 **meta 单行 JSON**。

|  | 睡眠 | 亲喂 |
| --- | --- | --- |
| 载体 | records：sleep 记录 `endAt = null`（occurredAt = startAt） | meta：`active_nursing` = JSON |
| 开始 | insert 记录 | 写 meta `{startAt, activeSide, sideStartAt, pausedAt, leftMillis, rightMillis, note}` |
| 进行中 | update 不发生，读即状态 | 换边/暂停时把上一段累计进 left/rightMillis 再写回 |
| 结束 | update 记录补 `endAt + tag + note` | 累计出左右分钟 → insert feed_nursing → 清 meta |
| 恢复 | `observeByType(SLEEP)` 过滤 `endAt=null`（不限 babyId 当天，跨天夜觉也在） | `metaDao.observe` 反序列化 |

好处：睡眠进行中天然进同步流（V1.1 另一端直接看到「睡眠中」）；亲喂记录表只存完成事实；零新增持久化机制。

## 2. 变更单一路径：TimerCenter

`TimerCenter`（Application 单例，持 appContext + repo）：全部计时操作（start/stop/cancel/switch/pause）的**唯一入口**——先改状态（Room/meta），再通知 TimerService 刷新。UI 与通知按钮都走它，避免两条路径状态漂移。所有操作 suspend，由调用方（VM 协程 / Service 协程）驱动。

## 3. TimerService（前台服务）

- `foregroundServiceType="specialUse"`（计时器场景标准做法；个人应用不上架无审查负担），manifest 声明 subtype 说明
- **START_STICKY**：进程被杀系统重建 → `onStartCommand(null)` → 读状态重建通知（时间戳恢复的第二重保险）
- **单通知承载（最多双计时）**：chronometer = 最早 startAt（系统自动走秒）；内容按进行中的计时拼行；动作按钮 换边/结束亲喂/结束睡眠（≤3 个）
- 通知动作 → `onStartCommand` 分发到 TimerCenter；`MainActivity.onResume` 发 REBIND 兜底（服务死了但计时还在）
- 通知渠道 low importance、`setOnlyAlertOnce`、点开进 MainActivity

## 4. UI（今天页）

- 快捷条上方插「进行中」卡片区（睡眠卡 / 亲喂卡可同时出现）：
  - **亲喂卡**：🤱 左 X 分 · 右 Y 分（实时）、开始时间；按钮 暂停/继续、换边、结束、取消（小字）
  - **睡眠卡**：😴 已睡 X（durationText）、HH:mm 开始；按钮 结束、取消（确认）
- 快捷条联动：睡眠进行中 → 「睡眠」钮变打开结束弹层；亲喂进行中 → 「亲喂」钮变打开亲喂计时弹层；否则原行为（亲喂弹选边 + 手动补录入口；睡眠 1 击直开 + Snackbar）
- **StopSleepSheet**（结束睡眠弹层）：开始时间只读、结束时间 TimeRow 默认现在（可改，校验 > 开始）、tag 按结束小时自动选中可改、备注；保存 → update 记录
- **亲喂计时弹层**：进行中复用卡片内容放大（左/右实时大字 + 三钮）；未进行时 = 选边开始（左/右大钮）+「手动补录」文字入口（复用 0003 NursingSheet）
- 通知权限：首次任一计时开始时 `rememberLauncherForActivityResult` 请求（API 33+），拒绝不阻断

## 5. 边界

- 重复开始：TimerCenter 拒绝（已有进行中）——UI 联动后基本触发不到，纯防御
- 时间轴里删除进行中的 sleep 记录 → 视同取消计时（软删 + 刷新通知）
- 亲喂分钟换算：毫秒四舍五入到分钟（`+30s / 60s`）；结束时刻 occurredAt = startAt
- meta JSON 坏数据 → 反序列化失败当无计时（不崩）
- 亲喂/睡眠同时进行：允许（喂睡同哄），两张卡 + 一条合并通知

## 6. 测试（`app/src/test`，纯 JVM）

- `TimerCalcTest`：累计（单侧 / 换边 / 暂停跨段 / 暂停中不累计）、分钟四舍五入边界（30s、90s、-0）、finalize 出 payload 字段正确
- `NursingTimerState` JSON 往返（含默认值字段）
- sleep 结束 tag 自动判：sleepTag 已有测试覆盖，不重复

Service / 通知 / 权限：instrumented 范畴，本版不覆盖（同 0003 口径，真机冒烟补）。
