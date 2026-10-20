# 0016 · 云同步 App 客户端 — 技术设计（design）

日期：2026-10-20 · 上游：RequireSpec.md（D-1~D-4 已拍板）· 契约：`docs/features/0001-biz-cloud-sync-backend/design.md` §3

## 1. 方案与关键取舍

| # | 决策 | 取舍与理由 |
| --- | --- | --- |
| K1 | **HTTP 用 OkHttp 4.12 单 artifact**，不用 Ktor/Retrofit | 只有 7 个接口，手写 `POST/GET + kotlinx.serialization` 足够；Ktor 要 3~4 个 artifact（client-core/engine/content-negotiation），Retrofit 引注解与代码生成。符合「克制依赖」 |
| K2 | **脏跟踪 = records 表加 `dirty` 列**（Room v1→v2） | 不加列的替代方案（时间水位线）在时钟漂移/半批失败下不可靠；独立 pending 表要多一次 join。一列 `INTEGER NOT NULL DEFAULT 1` 迁移成本最低。**默认 1**：存量记录推送状态未知，视为待推（首次绑定的全量上行因此天然成立） |
| K3 | **推送确认按 `(uuid, updatedAt)` 双条件清脏** | push 在途时用户又编辑了同一条（updatedAt 已变），清脏必须不命中 → 记录保持 dirty，下一轮重推。防止「推送确认吃掉新编辑」 |
| K4 | **游标存 meta 表（`SYNC_CURSOR` 已预留），批次应用 + 游标推进放一个 Room 事务** | 崩溃不会出现「数据应用了但游标没走」→ 重复拉同一批；LWW 幂等，重复应用无害，事务只是省掉冗余重放 |
| K5 | **occurredAt/babyId 用 payload 信封键 `_occurred_at_ms`/`_baby_id` 随行**，后端零改动 | 后端 records 表没有这两列（契约本就预期 payload 内含 `time`/`baby_id`，示例可证）；App 本地是查询冗余列。推送时把列值注入 payload JSON，拉取时抽出来重建列。`ignoreUnknownKeys=true` 保证旧版本解码不受影响；下划线前缀与现有 camelCase 字段零冲突 |
| K6 | **前台感知用 Application 注册 ActivityLifecycleCallbacks**，不引 lifecycle-process | 零新依赖，十几行；只是拿「有无 started Activity」一个布尔，犯不上加 artifact |
| K7 | **引擎纯 Kotlin（`SyncEngine`）+ Android 接线壳（`SyncCenter`）** | 存储经窄接口 `SyncStore` 注入（实现包 Room 事务），传输经接口 `SyncTransport` 注入（实现包 OkHttp）。引擎表驱动单测不需要 Robolectric——与现有 24 个纯 JUnit 测试同一风格 |
| K8 | **清空数据分叉**：同步已配置 → 全表墓碑化（软删+置脏）走推送；未配置 → 维持现有物理删 | 否则清空后一次 pull 会把服务端数据全部「复活」。墓碑推到对端 = 两边一致消失，符合「数据不分裂」 |
| K9 | **每日必做播种双保险**：① 播种判定改「该类型是否存在任何行（**含墓碑**，新 DAO `hasAny`）」——现有 `observeByType` 过滤 `deleted=0`，墓碑不参与判定，跨设备会把对端故意删光的默认任务重新播种；② 同步已绑定但首轮未完成时（`initialSyncDone` 门闩）不播种——封住「首拉进行中、宝宝档案已到而任务未到」的竞态窗口 |
| K10 | **401 = 令牌失效，停退避、状态栏提示重新配对**；其余失败照常指数退避 | token 不会「过一会儿变好」，无脑重试只会空转 |

**确认过的事实基础**（摸底结论）：后端 `type` 校验仅格式正则（handlers.go:16,213），本地 14 种类型全可过；所有写路径（UI/TimerCenter/Widget/ReminderCenter）都过 `BabyRepository`，`repo.changes` SharedFlow 每次 `add/update/softDelete/importAll` 都发信号——SyncCenter 订阅它即可覆盖全部触发点，**Repository 只需补 dirty 语义，不需新增通知机制**；我的页 170-174 已有「云同步」灰置占位；`deviceId`（author 来源）已在 Application prefs。

## 2. 数据模型与接口

### 2.1 Room v1→v2

```sql
ALTER TABLE records ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1;
```

- `RecordEntity` 加 `dirty: Boolean`（`add/update/softDelete` 构造时置 `true`；`applyIncoming` 置 `false`；`importAll` 置 `true`——备份导入的行同样要上行）
- `RecordDao` 新增：
  - `dirtyOnce(limit: Int): List<RecordEntity>` — `WHERE dirty=1 ORDER BY updatedAt LIMIT :limit`（先老后新，保证编辑链完整上行）
  - `markPushed(uuid: String, updatedAt: Long)` — `UPDATE … SET dirty=0 WHERE uuid=:uuid AND updatedAt=:updatedAt`（K3）
  - `markAllDirty()`
- `BabyDatabase` version=2 + `MIGRATION_1_2`（用户已有真实数据，禁 destructive）

### 2.2 传输信封（`sync/SyncEnvelope.kt`）

```
上行：payload JSON（本地原文）解析为 JsonObject → 注入 "_occurred_at_ms"(Long)、"_baby_id"(String) → 发送
下行：收到 payload JsonObject → 抽出两个键填回实体列 → 本地存储的 payload 剥掉信封键（往返对称）
缺失防御：键缺失时 occurredAt 退化为 updatedAt、babyId 退化为当前活跃宝宝（老数据兜底，不丢记录）
```

### 2.3 偏好与 meta

| 键 | 载体 | 内容 |
| --- | --- | --- |
| `sync_base_url` / `sync_token` / `sync_family_id` / `sync_device_name` / `sync_enabled` | SharedPreferences（`SyncPrefs`，照 Application 的 darkOverride 先例暴露 StateFlow） | 配置与开关；**deviceId 沿用 Application 现有 prefs 键，不另存** |
| `SYNC_CURSOR`（已有） | meta 表 | 拉取游标，事务内推进 |
| `DEVICE_NAMES`（新增） | meta 表 | `{deviceId: name}` JSON，记录人角标 + 设备列表用；配对/创建/打开同步页时刷新 |

### 2.4 传输层（`sync/SyncApi.kt`）

- 接口 `SyncTransport`（引擎只认它）：`ping / createFamily / pair / refreshPairingCode / devices / push / pull` 七个 suspend 方法 + DTO
- 实现 `OkHttpSyncTransport`：单 client、`Authorization: Bearer`、`application/json`、`Dispatchers.IO`；错误归一化为 `SyncException(kind: Network|Auth(401)|Client(4xx)|Server(5xx), code: String?)`
- DTO 字段名严格按契约 §3（`family_name`/`updated_at`/`has_more`/`next_cursor`…），golden JSON 测试钉死

### 2.5 存储缝（`SyncStore`，K7）

引擎视角的窄接口，Room 实现里 `applyPulled(changes, nextCursor)` 用 `withTransaction` 包「逐条 LWW + 游标推进」：

```
interface SyncStore {
    suspend fun dirtyOnce(limit: Int): List<RecordEntity>
    suspend fun markPushed(uuid: String, updatedAt: Long)
    suspend fun markAllDirty()
    suspend fun pushCursorCeiling(cursor: Long)          // push 返回的 cursor 直接抬高本地游标（跳过回读自己的数据）
    suspend fun applyPulled(changes: List<PulledChange>, nextCursor: Long): Int  // 返回应用条数
    suspend fun tombstoneAll()                           // K8 清空路径
}
```

### 2.6 引擎与状态（`sync/SyncCenter.kt`）

`SyncUiState`（StateFlow，我的页/角标消费）：

```
data class SyncUiState(
    val configured: Boolean,      // url 已存
    val bound: Boolean,           // token 已存（= familyId/deviceName 齐备）
    val enabled: Boolean,         // 总开关（bound 才有意义；断开=回 false）
    val running: Boolean,
    val lastOkAt: Long?,          // 上次整轮成功
    val lastError: String?,       // 人话错误（「网络不通」「登录失效，请重新配对」）
    val authExpired: Boolean,     // 401 标记，重绑才清
    val initialSyncDone: Boolean, // 首轮（绑定后第一轮）完成 = K9 门闩
    val pendingCount: Int,        // 脏记录数（状态刷新时查一次）
)
```

## 3. 核心流程

### 3.1 一轮同步（`SyncEngine.round()`，串行）

```
前置：enabled && bound && !authExpired，否则直接返回
1. push 阶段：loop { dirtyOnce(500) 为空 → break；组 changes（信封注入）→ transport.push
   → 成功：逐条 markPushed(uuid, pushedUpdatedAt)（K3 双条件）；游标抬高到响应 cursor
   → 失败：抛出，整轮失败进退避 }
2. pull 阶段：loop { transport.pull(cursor, 500) → store.applyPulled(changes, next_cursor) → has_more 为假 break }
3. 成功收尾：lastOkAt=now；backoff 清零；initialSyncDone=true
```

**单循环调度**（SyncCenter，actor 式）：`kick()` 只是发信号；循环体「等待信号 → 若上一轮还在跑则置 rerun 标志 → 跑 round() → 若 rerun 再跑」。用户连续记 5 笔 = 最多第一轮带上 1~4 笔、第二轮收尾，不产生 5 个并发请求——「每次写即推」语义保持（不延迟、不攒批），只是天然合流。

### 3.2 触发源（全部汇入 kick）

| 触发 | 实现 |
| --- | --- |
| 每次写（D-1） | SyncCenter 订阅 `repo.changes` |
| 回前台（D-2） | ActivityLifecycleCallbacks：`onActivityStarted` 计数 0→1 时 kick + 启动 ticker |
| 前台周期 60s（D-2） | ticker 协程，仅前台存活（1→0 时取消） |
| 推后即拉（D-2） | round() 内在 push 完成后接 pull，天然成立 |
| 失败退避（D-4） | 5s → 30s → 2min → 5min 封顶，成功清零；退避期间新 kick 仍记 rerun，等退避到点执行 |

### 3.3 绑定（配对流程）

```
创建家庭：POST /v1/family {family_name, device_name} → 存 token/familyId/deviceName → markAllDirty() → kick（首轮 = 全量上行 + 全量下行 LWW 合并，F8 成立）
配对加入：POST /v1/pair {pairing_code, device_name} → 同上
断开：清 token/familyId/游标/DEVICE_NAMES，enabled=false；本地数据与 dirty 标记保留（回纯本地；再绑新家庭时 markAllDirty 全量带入）
401 处理：authExpired=true 停退避，我的页提示重新配对
```

### 3.4 记录人角标（D-3）

- `TimelineRow`（TodayComponents.kt:174）title 行加小角标：**仅 `entity.author != 本机 deviceId` 时显示**，文案 = `DEVICE_NAMES[author]`，缺省「其他设备」；本机记录不加（全加是噪音，交接班场景关心的是对方记了什么）
- `TodayViewModel` 暴露 `authors: StateFlow<Map<String, String>>`（meta `DEVICE_NAMES` 反序列化）；TodayPage collect 后按行计算传入

### 3.5 同步页 UI（`ui/mine/SyncSheet.kt`，ModalBottomSheet，照 RemindersSheet 骨架）

```
未配置  → 服务器地址输入（http(s)://host:port，ping 校验通过才可存）+ 保存
未绑定  → Tab 创建家庭（家庭名/设备名）｜ Tab 配对码加入（6 位码/设备名）→ 成功即绑定并跳已绑定态
已绑定  → 总开关；状态行（上次同步时间/失败原因/待推 N 条）；设备列表（本机标注）；
          刷新配对码（展示新码）；断开（二次确认，说明数据保留本机）
我的页  → 170-174 灰置行去灰，改开 SyncSheet；行右侧副标题显示同步状态摘要（如「已同步 · 14:32」）
```

## 4. 模块与文件拆分

```
app/src/main/java/com/babycare/app/
├── sync/
│   ├── SyncEnvelope.kt      # 信封编解码（纯函数）
│   ├── SyncApi.kt           # SyncTransport 接口 + DTO + OkHttpSyncTransport + SyncException
│   ├── SyncEngine.kt        # 纯引擎：round() 逻辑，依赖 SyncStore/SyncTransport/时钟（可测）
│   ├── SyncStore.kt         # 窄存储缝接口 + RoomStore 实现（withTransaction）
│   └── SyncCenter.kt        # 装配壳：触发源汇入、单循环调度、退避、SyncUiState、前台感知
├── ui/mine/SyncSheet.kt     # 三态弹层
├── data/
│   ├── entity/RecordEntity.kt     # +dirty
│   ├── dao/RecordDao.kt           # +dirtyOnce/markPushed/markAllDirty
│   ├── db/BabyDatabase.kt         # v2 + MIGRATION_1_2
│   ├── repo/BabyRepository.kt     # 写路径 dirty 语义、applyIncoming dirty=false
│   └── model/MetaKeys.kt          # +DEVICE_NAMES
├── BabycareApplication.kt    # SyncCenter by lazy + onCreate start(appScope) + ActivityLifecycleCallbacks
├── ui/today/{TodayViewModel,TodayPage,TodayComponents}.kt   # 记录人角标
└── ui/mine/MinePage.kt       # 入口去灰
AndroidManifest.xml           # +INTERNET、+usesCleartextTraffic=true
app/build.gradle.kts          # +okhttp 4.12.0（implementation）、+mockwebserver（testImplementation）

app/src/test/java/com/babycare/app/sync/
├── SyncEngineTest.kt     # 表驱动：分批、rerun 合流、清脏双条件防丢写、游标推进/回绕、LWW 跳过、墓碑应用、401 停退避、退避序列、首绑全量、清空墓碑路径
├── SyncEnvelopeTest.kt   # 注入/抽取往返对称、键缺失兜底、与现有 payload 字段无冲突
├── SyncApiDtoTest.kt     # golden JSON：DTO 编解码与契约 §3 示例逐字段一致
└── SyncTransportTest.kt  # MockWebServer：鉴权头、401/400/500 归类、push 批量、pull 分页
```

实现顺序：Room v2 + Repository dirty 语义 → SyncEnvelope（+测试）→ SyncTransport（+golden/MockWebServer 测试）→ SyncEngine（+表驱动测试）→ SyncCenter 装配 → SyncSheet/我的页 → 角标 → Manifest/gradle。

## 5. 风险与兜底

| 风险 | 影响 | 兜底 |
| --- | --- | --- |
| 设备时钟不准 → LWW 取到「假新」 | 罕见且后端设计已认（0001 §6 同条目） | 家庭设备默认 NTP；skipped 计数可见异常。不做服务端时间裁决（后端预留了 seq） |
| `usesCleartextTraffic=true` 放开 http | 明文传输（内网调试需要） | 生产部署走 HTTPS（backend README 已含）；地址栏提示「内网地址将以不加密方式传输」 |
| 播种竞态（K9）之外的极端：两台都离线状态各自首建 | 各自独立家庭，数据各自孤岛 | 配对码机制本身解决（必须显式进同一家庭）；不自动合并 |
| push 半批成功后断网 | 部分 markPushed 已清、其余保持 dirty | 幂等重推（0001 §4）；dirty 语义天然正确 |
| 401 后用户长期不重绑 | 同步停摆无感知 | 状态行常驻提示 + 我的页行副标题透出 |
| meta（亲喂计时态）不同步 | 换机/对端看不到「进行中」亲喂 | 明确不做（RequireSpec 已列）；亲喂结束落 records 后自然同步 |
| payload ≥64KB | 理论上备注超长会被服务端拒 | 现有 payload 都是短文本；备注字段加长度上限属于体验优化，暂不做 |
| Room v2 迁移失败 | 升级用户数据丢 | 迁移就一句 ALTER TABLE；`exportSchema` 保持 false，上线前真机升级验证一次 |
