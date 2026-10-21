# 0016 云同步 App 客户端 · 交付说明

## 交付内容

V1.1 FR-20 App 侧全量落地：本地每次写立即触发 push（不攒批、不阻塞 UI），回前台 + 前台每 60s + 推后即拉增量 pull，LWW 合并与后端/备份导入同语义。我的页「云同步」入口三态弹层：填服务器地址 → 创建家庭/配对码加入 → 总开关 + 同步状态 + 设备列表 + 刷新配对码 + 断开。时间轴记录条目显示另一台设备记录的「谁记的」角标。

## 落点

| 文件 | 内容 |
| --- | --- |
| `sync/SyncApi.kt` | 0001 契约 §3 全部 DTO（蛇形 @SerialName）+ SyncException 四类归类（NETWORK/AUTH/CLIENT/SERVER，带人话 userText）+ OkHttpSyncTransport（唯一碰网络处，5s 连/15s 读写超时） |
| `sync/SyncEnvelope.kt` | 传输信封：上行把 occurredAt/babyId 列注入 payload 的 `_occurred_at_ms`/`_baby_id` 保留键（后端 records 表没有这两列），下行抽出重建列、剥键存原文 |
| `sync/SyncStore.kt` | 引擎视角窄存储缝（K7）；SyncApply 纯规划（LWW 过滤 + 信封解包 + dirty=false 重建）；RoomStore 批量应用 + 游标推进包一个事务 |
| `sync/SyncEngine.kt` | 一轮 = push 脏记录到推完（≤500/批，双条件清脏，游标抬到响应 seq）→ pull 到追平（hasMore 续拉） |
| `sync/SyncPrefs.kt` | 配置（地址/token/家庭/设备名/开关/首轮完成标记）走 SharedPreferences StateFlow |
| `sync/SyncCenter.kt` | 触发调度（repo.changes→kick、前台生命周期 + 60s ticker、绑定/开关即跑）+ CONFLATED 单 worker 天然合流 + 退避 5s/30s/2min/5min + 401 停退避待重绑 + 配对动作（探活保存/创建/加入/刷新码/设备列表→DEVICE_NAMES meta） |
| `data/entity/RecordEntity.kt` + `BabyDatabase.kt` | `dirty` 列（默认 1，存量状态未知视为待推）；Room v1→v2 迁移 |
| `data/dao/RecordDao.kt` | dirtyOnce/markPushed（uuid+updatedAt 双条件）/markAllDirty/hasAny（含墓碑）/countDirty/tombstoneAll |
| `data/repo/BabyRepository.kt` | update/softDelete 置脏；下行应用不经 repo（不置脏不发 changes，防 pull→push 回环）；tombstoneAll/hasAnyType/observeMeta |
| `ui/mine/SyncSheet.kt` | 三态弹层；`ui/mine/MinePage.kt` 入口解灰（副标题随三态）、清空弹窗与页脚文案按同步态分叉；RemindersSheet 的 ToggleRow 提 internal 复用 |
| `ui/today/TodayComponents.kt` + 三页调用点 | TimelineRow `authorLabel: (() -> String?)?`（lambda 让 State 读取落在行自己的重组域）；Today/Calendar/Growth 三处传入 `vm.authorLabel(author)`；TodayViewModel 播种双保险（等首轮 + hasAny 含墓碑） |
| `BabycareApplication.kt` | syncCenter 装配常驻启动；author = 本机设备 ID |
| `AndroidManifest.xml` / `gradle` | INTERNET 权限 + usesCleartextTraffic（内网 http 调试）；okhttp 4.12.0（implementation）+ mockwebserver（test） |

## 设计要点

- **信封键**：`_` 前缀与现有 camelCase payload 字段零冲突；解码 ignoreUnknownKeys，旧版本读新数据无感。往返对称，本地 payload 不残留信封键。
- **每次写即推不轰炸**：写路径只发一条 CONFLATED 通道信号，单 worker 消费——连写 10 笔合流成一轮（跑着时新信号并成一个，跑完立即再跑），无 debounce 延迟。
- **首绑全量上行**：迁移 DEFAULT 1 + 绑定时 markAllDirty，存量数据走同一条分批 push 通道，无特殊路径。
- **双条件清脏**：markPushed 带 (uuid, updatedAt)，推送在途时的新编辑（updatedAt 已变）不会被旧确认误清。
- **推后游标抬高**：push 响应的家庭最新 seq 直接 raiseCursorTo（只升不降），不回读自己刚推的数据（否则 author 会被服务端 ID 覆盖，角标逻辑就废了）。
- **清空分叉（K8）**：同步激活 → tombstoneAll（软删全部 + 置脏 → 推给对端一并消失）；未激活 → 物理 wipeAll 不变。
- **播种双保险（K9）**：每日必做播种前 awaitInitialSyncIfBound()（等首轮拉完，避免新装机重复播种）+ hasAny(type)（含墓碑——对端删光也不复活）。
- **401 停退避（K10）**：令牌失效不会自愈，停自动重试，状态行提示重新配对；重绑/解绑才复位。

## 使用方法

1. 起后端：`backend/`（README 有本地/部署说明），App 与后端同一内网或可达公网
2. 主设备：我的 → 云同步 → 填服务器地址（测试并保存）→ 创建家庭（家庭名 + 本机名）
3. 第二台：填同一地址 → 配对码加入（输主设备「刷新配对码」得到的 6 位码）
4. 之后全自动：每次记录即推，对端回前台或 1 分钟内可见；时间轴显示对方记的条目角标

## 验证

- `:app:testDebugUnitTest` **140 条全绿**（新增 sync 4 文件 27 条：SyncEngineTest 8——分批/无脏不推/首绑全量/在途编辑不清脏/拉取分页/LWW 拒旧/墓碑软删/信封重建；SyncEnvelopeTest 6——注入/往返对称/剥键/缺键兜底/坏 JSON/键名零冲突；SyncApiDtoTest 6——golden JSON 逐字段对齐契约；SyncTransportTest 7——令牌与路径/查询参数/401/400 带错误码/500/拒连归类）
- `:app:assembleDebug` 通过
- 真机双机冒烟待跑（配对、A 记 B 见、断网 10 笔恢复补推、清空同步消失、角标显示）

## 增量：双档案收敛（K11，双机实测修复）

实测发现：B 先被引导建档再配对（不建档进不了我的页），同步后家里两份档案，B 界面跟自己的空档案 → A 的记录「看不见」。

- 新增 `sync/SyncConverge.kt`（纯规划：档案保最旧、任务同名保最旧、平局比 uuid 全端一致）+ `SyncCenter.converge()` 每轮成功后对账落地
- `RecordDao.reassignBaby` / `BabyRepository.reassignBaby`：输家档案名下记录换挂赢家；UPDATE 推进 updatedAt（服务端 LWW 只认更晚，babyId 只活在信封里）
- 存量已分叉数据**无需重绑自愈**：收敛写置脏走推送，对端拉到后下一轮对账为无冲突即停
- 新增 `SyncConvergeTest` 8 条；148 单测全绿，assembleDebug 通过

## 增量：首绑拉不到历史（K12，双机二轮实测修复）

K11 装机后 B 仍看不到 A 的数据——排查发现 B 的 Room 里**根本没有** A 的记录：旧轮序「先推后拉」在首绑时，markAllDirty 先把 B 自家 ~15 行推上去，push 响应把游标抬到 76，之后 pull 从 76 起永远拉不到 seq 1~61 的 A 的历史（拉空也返回 200，状态显示「同步成功」）。K11 在 B 上无冲突可收，属空转。

- `SyncEngine.round(pullFirst)`：首轮（initialSyncDone=false）**先拉后推**；非首轮维持原序（每次写即推的即时性不受影响）
- `bind()` 即 `resetCursor()`（换家庭重绑同样全量）；`SyncStore.resetCursor()` 落 meta
- **存量已绑设备自愈**：prefs 新增 `sync_proto_version`，启动发现落后 → 一次性游标归零全量重拉（LWW 幂等，已有行同版本被拒、只补缺失）
- 新增引擎测试 2 条（先拉后推时序 / 非首轮原序）；**150 单测全绿**，assembleDebug 通过。两台装机打开 App 即自动全量重拉 + 收敛

## 遗留

- `usesCleartextTraffic="true"` 是为内网 http 调试的全局放行；公网部署走 https 后可移除
- 计时暂态（亲喂计时器 meta、进行中睡眠由记录天然同步）不同步 meta，换机不恢复「进行中」计时（设计拍板：无意义）
- 墓碑物理 GC 是后端 V2 事项；本地导出备份仍含墓碑（既有行为）
- 双机真机验证后无问题即可发 V1.1
