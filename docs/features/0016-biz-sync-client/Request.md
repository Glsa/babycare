# 0016 · 云同步 App 客户端 — 需求录入（Request）

日期：2026-10-20 · 来源：用户口头需求（会话）

## 原始需求

> 实现同步功能，并且每次写都进行同步。

## 会话中已确认的事实与决策

1. 同步协议层面是**双向增量**（上游已定，非本期决策）：
   - push 只发本地变更过的记录，服务端按 uuid LWW upsert，幂等可重发
   - pull 按游标（cursor）增量拉取，服务端 seq 单调递增
   - 契约以 `docs/features/0001-biz-cloud-sync-backend/design.md` §3 为准
2. **触发策略（用户拍板）：每次写立即同步**——本地每产生一次记录变更（新增/编辑/删除）即触发上行 push，不做攒批 debounce。
3. 后端已完成并测试通过（`backend/`，Go，SQLite 三表 families/devices/records）。
4. 本期即 PRD 的 V1.1「App 同步客户端」（FR-20），App 侧数据层（records 单表 + uuid/updatedAt/软删/author + `SyncMerge` LWW 判定）自 V1 起已就绪。

## 关联材料

- PRD：`docs/产品需求文档.md` FR-20（同步模型/LWW/配对码认证/隐私开关/记录人标注）
- 后端契约：`docs/features/0001-biz-cloud-sync-backend/design.md`
- 本地存储现状：`data/entity/RecordEntity.kt`（镜像后端模型）、`data/repo/BabyRepository.kt`（唯一写入口）
- 我的页：`docs/features/0010-biz-mine-page/`（设置类数据走 SharedPreferences 的先例；导入导出 LWW 同语义）
