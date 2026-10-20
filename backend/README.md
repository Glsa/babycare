# babycare-sync · 云同步后端

宝宝记录 App 的同步服务（[FR-20](../../docs/产品需求文档.md)）：爸爸妈妈两台手机共记，本地优先 + 增量同步。Go + SQLite，单二进制零外部依赖。

API 契约（App 对接以此为准）：[docs/features/0001-biz-cloud-sync-backend/design.md](../docs/features/0001-biz-cloud-sync-backend/design.md)

## 快速启动

```bash
cd backend
go run ./cmd/babycare-sync            # 默认 :8080，数据文件 ./babycare.db
go run ./cmd/babycare-sync -addr :9000 -db /data/babycare.db

go test ./...                          # 全量测试
go build -o babycare-sync ./cmd/babycare-sync   # 交叉编译：CGO_ENABLED=0 GOOS=linux GOARCH=arm64
```

配置：命令行 flag 优先，环境变量兜底（`BABYCARE_ADDR` / `BABYCARE_DB`）。配对码有效期 `-code-ttl` 默认 15 分钟。

## 接口一览

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/v1/ping` | 无 | 健康检查 |
| POST | `/v1/family` | 无 | 创建家庭（返回首台设备令牌 + 配对码） |
| POST | `/v1/pair` | 无 | 凭配对码加入家庭 |
| POST | `/v1/pairing-code` | Bearer | 刷新配对码 |
| GET | `/v1/devices` | Bearer | 设备列表 |
| POST | `/v1/sync/push` | Bearer | 上行变更（≤500 条/批，LWW） |
| GET | `/v1/sync/pull?cursor=&limit=` | Bearer | 按游标增量下行 |

## curl 走一遍全流程

```bash
BASE=http://127.0.0.1:8080

# 1. 妈妈手机创建家庭 → 拿到 token1 和 pairing_code
curl -s -X POST $BASE/v1/family -d '{"family_name":"小汤圆家","device_name":"妈妈的手机"}'

# 2. 爸爸手机凭配对码加入 → 拿到 token2
curl -s -X POST $BASE/v1/pair -d '{"pairing_code":"482913","device_name":"爸爸的手机"}'

# 3. 妈妈记一笔奶粉
curl -s -X POST $BASE/v1/sync/push \
  -H "Authorization: Bearer <token1>" \
  -d '{"changes":[{"uuid":"u1","type":"feed_formula","payload":{"ml":150},"updated_at":1761073500000,"deleted":false}]}'

# 4. 爸爸拉取（首次 cursor=0；之后用返回的 next_cursor）
curl -s "$BASE/v1/sync/pull?cursor=0" -H "Authorization: Bearer <token2>"
```

同步规则一句话：**同一条记录谁的 `updated_at` 新听谁的（LWW）；`deleted=true` 是删除墓碑，照拉照存**。push 幂等，弱网重发无害。

## 部署注记

- 只听 HTTP，HTTPS 由反向代理（nginx / caddy）终止；服务绑定 127.0.0.1 或内网即可
- 备份 = 拷 `babycare.db`（WAL 模式下先 `sqlite3 babycare.db 'PRAGMA wal_checkpoint(TRUNCATE);'` 或直接停服务再拷）
- systemd / Docker 皆可，无状态诉求只有这一个 db 文件
- 日志只有 方法/路径/状态/耗时，令牌与请求体不落日志

## 目录结构

```
cmd/babycare-sync/main.go    入口：配置装配、优雅退出
internal/api/                路由、中间件、7 个接口 + 端到端测试
internal/store/              SQLite 存储层 + LWW/游标测试
internal/auth/               令牌/配对码/ID 生成，令牌只存哈希
```
