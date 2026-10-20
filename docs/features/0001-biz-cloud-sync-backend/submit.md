# submit · 云同步后端

> 2026-10-17 交付。需求基线：[RequireSpec](RequireSpec.md) · 设计：[design](design.md)

## 功能点勾对验收标准

| 验收标准（RequireSpec §6） | 结果 |
| --- | --- |
| 1. 两设备经创建/配对后数据互通 | ✅ `TestSyncEndToEnd` + 真机冒烟（curl 全流程） |
| 2. 同 uuid LWW 取新者，旧者 skipped | ✅ `TestPushLWW`（新/旧/相等三例表驱动） |
| 3. 无/错令牌 401；家庭间隔离 | ✅ `TestAuthFailures`、`TestFamilyIsolation`、`TestFamilyIsolationEndToEnd` |
| 4. deleted 墓碑可同步 | ✅ `TestDeletedTombstone` |
| 5. 非法输入 400 且不落库 | ✅ `TestValidationFailures`（7 类输入） |
| 6. 测试全绿、单二进制可跑 | ✅ `go test ./...` 全绿；`go build` + 冒烟通过 |

## 文件清单

```
backend/
├── go.mod / go.sum             # module babycare/backend；modernc.org/sqlite v1.59.0（纯 Go）
├── README.md                   # 启动 / 接口摘要 / curl 示例 / 部署注记
├── cmd/babycare-sync/main.go   # 入口：flag+env 配置、优雅退出
└── internal/
    ├── api/server.go           # 路由 + auth/日志/recover 中间件
    ├── api/handlers.go         # 7 个接口 + 校验
    ├── api/api_test.go         # 端到端 10 个用例
    ├── auth/auth.go            # 令牌/配对码/ID 生成（令牌只存哈希）
    └── store/store.go(+test)   # SQLite 三表 + LWW/游标语义
```

另：根目录新增 `.gitignore`（Android 构建产物 + db 文件 + 二进制）。

## 使用方法

```bash
cd backend
go run ./cmd/babycare-sync                 # :8080，./babycare.db
go test ./...
# 交叉编译部署：CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -o babycare-sync ./cmd/babycare-sync
```

App 对接契约 = [design.md §3](design.md#3-api-契约v1)（含全流程 curl 示例）。

## 实现要点（与设计一致）

- **LWW 用 DELETE+INSERT 落地**：更新时记录拿到新 `seq`（游标），已拉过的设备增量拉取能看到更新——这是「UPDATE 保 rowid」方案会漏的坑，`TestUpdateBumpsSeq` 专门锁住该行为
- push 幂等：重发同批全部 skipped，弱网重试安全（`TestPushIdempotent`）
- `author_device_id` 服务端从令牌判定，请求体带的一律忽略
- 令牌 64 hex 只在创建/配对响应出现一次，库内存 SHA-256；日志只有方法/路径/状态/耗时
- 依赖锁定 v1.59.0：v1.60+ 要求 Go 1.26，本机 1.25.10（GOTOOLCHAIN=local），升级 Go 后可随手升

## 测试覆盖

| 文件 | 用例 |
| --- | --- |
| store_test | LWW 表驱动 3 例、更新换 seq、游标分页、家庭隔离、墓碑、空批 |
| api_test | ping、建家庭+配对+设备列表、端到端同步、push 幂等、401×2、配对码过期（注入时钟）、刷新配对码、校验 7 类、家庭隔离 |

未覆盖（明确留下）：并发压测（家庭规模不需要）；systemd/Docker 部署脚本（部署时另做）。

## 遗留与后续

1. **App 侧同步客户端**（Room 表带 uuid/updated_at/deleted/author + 同步 Worker）——下一个 feature
2. 部署实施（服务器 + 反代 HTTPS + 备份 cron）——要用时说一声
3. 设备管理（删除设备作废令牌）——V1.1「我的」页配套
4. 墓碑 GC（保留 90 天物理删）——量级到了再做
