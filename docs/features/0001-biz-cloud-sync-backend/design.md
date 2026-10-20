# design · 云同步后端

> App 侧对接以本文 API 契约为准。口径：**本地优先 + 同步**——App 本地 Room 是唯一工作库，后端是一台「变更日志中转站」，不解析业务内容。

## 1. 方案与关键取舍

| 决策点 | 选择 | 理由（含备选） |
| --- | --- | --- |
| HTTP 框架 | 标准库 `net/http`（Go 1.22+ 方法路由） | 路由就 7 个，chi/gin 纯属多余依赖；要加中间件再说不迟 |
| 存储 | SQLite（modernc.org/sqlite 纯 Go 驱动） | 单二进制 + 单文件，备份 = 拷文件；家庭规模（万级记录）远未到瓶颈。备选 MySQL：表结构是通用 SQL，`database/sql` 已隔离驱动差异，要迁主要改 DDL |
| 冲突策略 | LWW（Last-Writer-Wins），依据客户端毫秒时间戳 | 喂养/换尿布是「各自新增」，同条同改极罕见；HLC 向量钟这类方案复杂度不值。风险见 §6 |
| 同步游标 | 服务端自增 `seq` | 比「按时间戳拉」确定：每次落库 seq 单调 +1，断点续拉不重不漏 |
| payload | 服务端不解析，整存整取 JSON | App 加字段/加记录类型零后端改动；后端永远不用跟着 PRD 加表 |
| 鉴权 | 长时随机令牌（64 hex），库存 SHA-256 | 家庭场景无注册流程，配对码换令牌即可；令牌泄露可删设备行作废 |
| 部署 | 反向代理终止 HTTPS（不在本 feature 内） | 服务自身只听 HTTP，内网/本机反代皆可 |

## 2. 数据模型

三张表，DDL（SQLite）：

```sql
CREATE TABLE IF NOT EXISTS families (
  id              TEXT PRIMARY KEY,      -- 随机 hex
  name            TEXT NOT NULL DEFAULT '',
  pairing_code    TEXT NOT NULL,         -- 当前有效 6 位数字码
  code_expires_at INTEGER NOT NULL,      -- unix 秒
  created_at      INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
  id           TEXT PRIMARY KEY,         -- 随机 hex
  family_id    TEXT NOT NULL REFERENCES families(id),
  name         TEXT NOT NULL,
  token_hash   TEXT NOT NULL UNIQUE,     -- sha256(令牌)，不存明文
  created_at   INTEGER NOT NULL,
  last_seen_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_devices_family ON devices(family_id);

CREATE TABLE IF NOT EXISTS records (
  family_id       TEXT NOT NULL,
  uuid            TEXT NOT NULL,         -- 客户端生成，全生命周期不变
  type            TEXT NOT NULL,         -- 见 §3 约定
  payload         TEXT NOT NULL,         -- 客户端 JSON 原文
  updated_at      INTEGER NOT NULL,      -- 客户端毫秒时间戳（LWW 依据）
  deleted         INTEGER NOT NULL DEFAULT 0,  -- 软删墓碑
  author_device_id TEXT NOT NULL,
  seq             INTEGER PRIMARY KEY AUTOINCREMENT, -- 服务端游标
  UNIQUE(family_id, uuid)
);
CREATE INDEX IF NOT EXISTS idx_records_pull ON records(family_id, seq);
```

**记录类型约定**（`type` 取值，小写蛇形；服务端只做格式校验不认内容）：
`baby_profile` / `feed_formula` / `feed_nursing` / `diaper` / `bath` / `sleep` / `growth` / `edu_checkin` / `vaccine_dose` / `supplement_plan` / `supplement_log` / `agenda`。payload 内部结构由 App 定义（如 `feed_formula` 含 `ml`/`time`/`note`/`baby_id`）。

## 3. API 契约（v1）

Base：`http://<host>:8080`；鉴权头 `Authorization: Bearer <token>`；请求/响应都是 `application/json`。

| # | 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | GET | `/v1/ping` | 无 | 健康检查 → `{"status":"ok"}` |
| 2 | POST | `/v1/family` | 无 | 创建家庭（F1） |
| 3 | POST | `/v1/pair` | 无 | 配对码加入（F2） |
| 4 | POST | `/v1/pairing-code` | Bearer | 刷新配对码（F3） |
| 5 | GET | `/v1/devices` | Bearer | 设备列表（F7） |
| 6 | POST | `/v1/sync/push` | Bearer | 上行变更（F4/F6） |
| 7 | GET | `/v1/sync/pull?cursor=&limit=` | Bearer | 下行增量（F5） |

### 2 · 创建家庭

```json
// POST /v1/family
{ "family_name": "小汤圆家", "device_name": "妈妈的手机" }
// 201
{ "family_id": "f1a2…", "device_id": "d1…", "token": "64位hex…",
  "pairing_code": "482913", "code_expires_at": 1761073800 }
```

### 3 · 配对加入

```json
// POST /v1/pair
{ "pairing_code": "482913", "device_name": "爸爸的手机" }
// 200 —— 返回结构同上（无 pairing_code）
{ "family_id": "f1a2…", "device_id": "d2…", "token": "…" }
```
配对码过期 → 400 `pairing_code_expired`。

### 4 · 刷新配对码（Bearer）

```json
// POST /v1/pairing-code  （空 body）
// 200
{ "pairing_code": "新6位", "code_expires_at": 1761073800 }
```

### 5 · 设备列表（Bearer）

```json
// GET /v1/devices
{ "devices": [ { "device_id": "d1…", "name": "妈妈的手机", "last_seen_at": 1761073000 } ] }
```

### 6 · push（Bearer）

```json
// POST /v1/sync/push
{ "changes": [
  { "uuid": "u1…", "type": "feed_formula", "payload": {"ml":150,"time":"2026-10-17T21:05:00+08:00","baby_id":"b1…"},
    "updated_at": 1761073500000, "deleted": false }
] }
// 200
{ "applied": 1, "skipped": 0, "cursor": 42 }
```

规则：
- 单批 ≤500 条；单条 payload ≤64KB；`uuid` 非空 ≤64 字符；`type` 匹配 `^[a-z][a-z0-9_]{0,31}$`
- `author_device_id` **由服务端从令牌判定**，请求里带了一律忽略
- LWW：仅当 `updated_at` **严格大于**库内同 uuid 记录才覆盖；否则该条 skipped
- `cursor` 返回本次落库后的家庭最新 seq，客户端可立即作为拉取起点

### 7 · pull（Bearer）

```json
// GET /v1/sync/pull?cursor=0&limit=500
// 200
{ "changes": [
  { "uuid": "u1…", "type": "feed_formula", "payload": {…},
    "updated_at": 1761073500000, "deleted": false,
    "author_device_id": "d1…", "seq": 41 }
],
  "next_cursor": 41, "has_more": false }
```

- `cursor` 传上次 `next_cursor`（首次 0）；`limit` ≤500 默认 200
- `has_more=true` 时用 `next_cursor` 续拉
- 变更含 `deleted=true` 墓碑——App 据此删本地行

### 错误格式

```json
// 400/401/404/405/500
{ "error": "bad_json" }   // 错误码见 §5 处理器实现，均为短蛇形串
```

## 4. 核心流程（App 视角的对接时序）

```
首次：  POST /v1/family（主设备） ──► 存 token
        另一设备 POST /v1/pair（输配对码）──► 存 token
日常：  打开 App ──► GET /v1/sync/pull?cursor=本地游标
            └─ 逐条套用（LWW 同则跳过）→ 推进本地游标 = next_cursor
        本地有变更（记录新增/编辑/删除）──► POST /v1/sync/push
            └─ applied+skipped 都算「已达服务端」；skipped 即被 LWW 裁决为旧
```

push 幂等：重发同批，第二次全部 skipped（updated_at 不大于自己），无副作用——弱网重试安全。

## 5. 模块与文件拆分

```
backend/
├── go.mod                          # module babycare/backend
├── README.md                       # 启动/部署/API 摘要
├── cmd/babycare-sync/main.go       # 入口：flag/env 配置、注入依赖、优雅退出
└── internal/
    ├── api/
    │   ├── server.go               # 路由注册 + 鉴权/日志/recover 中间件
    │   ├── handlers.go             # 7 个接口 + 请求响应类型 + 校验
    │   └── api_test.go             # httptest 端到端：配对/push/pull/LWW/401/400/家庭隔离
    ├── auth/auth.go                # 令牌与配对码生成、哈希
    └── store/
        ├── store.go                # SQLite：建表 + 家庭/设备/记录读写
        └── store_test.go           # LWW 与游标语义的表驱动测试
```

关键实现点：
- SQLite `MaxOpenConns(1)`（单写者，从根上避开 `database is locked`）+ WAL + busy_timeout
- push 单事务批处理；upsert 用 `ON CONFLICT(family_id,uuid) DO UPDATE … WHERE excluded.updated_at > records.updated_at`，按 `RowsAffected` 计 applied/skipped
- `http.Server` 显式四超时（ReadHeader/Read/Write/Idle）；日志只打 方法/路径/状态/耗时，不打令牌与 body

## 6. 风险与兜底

| 风险 | 影响 | 兜底 |
| --- | --- | --- |
| 设备时钟不准 | LWW 可能取到「假新」的旧数据 | 家庭设备默认 NTP；push 响应的 skipped 计数可暴露异常；真遇到再上服务端时间裁决（预留：records 有 seq 可追溯） |
| 墓碑累积 | deleted 记录永远留存 | 家庭量级（百条墓碑）无压力；V2 再做「保留 90 天后物理删」 |
| 令牌泄露 | 他人可读写家庭数据 | 设备列表可见陌生设备；后续加「删除设备」（V1.1 App 侧管理页） |
| SQLite 损坏/误删 | 全家庭数据丢（服务端副本） | db 单文件每日快照即可；App 各自还有全量本地库，任一端可重建 |
| 弱网半批 | 部分到达 | push 幂等（见 §4），重发无害 |
