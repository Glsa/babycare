# babycare · 宝宝记录 Android App

个人家庭应用：记录婴儿喂养（亲喂左右/奶粉毫升）、尿布、洗澡、睡眠、生长（体重/身高/头围）、早教四类、疫苗、补剂；日历回看、今日行程、桌面 Widget 三款。**V1 纯本地（Room）；V1.1 起云同步支持爸妈共记（自建 Go 后端，FR-20）**。V1 之后规划统计/体温/辅食（见 PRD 版本切分）。

## 知识地图

| 主题 | 位置 |
| --- | --- |
| 产品需求（FR-1~20、Q 结论、疫苗/早教附录） | `docs/产品需求文档.md` |
| UI 设计说明（页面清单、tokens、交互、Widget、演示数据口径） | `docs/UI设计说明.md` |
| 设计稿（Figma，10 帧，fileKey SNpLcIrMpNOA1aTSTCSi5p） | https://www.figma.com/design/SNpLcIrMpNOA1aTSTCSi5p |
| **后端同步服务**（Go，入口 `backend/cmd/babycare-sync`；README 有启动/部署） | `backend/` |
| **API 契约**（App 对接以此为准，含 curl 示例） | `docs/features/0001-biz-cloud-sync-backend/design.md` §3 |
| 功能流水线产物（0001 云同步后端 · 0002 App 骨架 · 0003 今天页 · 0004 计时类 · 0005 日历页 · 0006 行程 · 0007 成长·生长+疫苗 · 0008 补剂+早教 · 0009 Widget 三款 · 0010 我的页 · 0011 喂奶间隔倒计时 · 0012 瓶喂奶粉/母乳 · 0013 今天页重排+每日必做 · 0014 提醒实装 · 0015 统计与生长曲线 · 0016 云同步 App 客户端） | `docs/features/` |
| 调研看板（唯一进度入口） | `docs/BOARD.md` |
| 调研流程机制（课题/gate 规则，随 Workflow.md 带入） | `docs/Workflow.md` |
| 常问问题 | `docs/faq.md`（暂未建立；问题被问第二次时创建） |

## 工程现状与约定

- 包名已重命名为 `com.babycare.app`，`minSdk 26`，应用名「宝宝记录」（2026-10-17 完成）
- 技术栈：Kotlin 2.2 + Compose + Material3；Room 2.8 + KSP（存储已落地）、kotlinx.serialization（payload）；Glance 1.1.1（Widget 已落地）、AlarmManager（提醒已落地）、OkHttp 4.12（云同步传输，唯一网络栈）
- 构建注意：compileSdk 37（Room 2.8 依赖链要求，targetSdk 仍 36）；`gradle.properties` 里 `android.disallowKotlinSourceSets=false` 是 KSP 的豁免开关，别删
- 计时类功能（睡眠/亲喂）已落地：**前台服务**（`timer/TimerService`，specialUse）+ **状态在 Room/meta**（睡眠 = sleep 记录 endAt=null；亲喂 = meta 单行 JSON）+ 开始时间戳恢复；变更唯一入口 `timer/TimerCenter`
- **同步已实装**（0016）：`sync/` 包（传输 OkHttpSyncTransport / 信封 SyncEnvelope / 引擎 SyncEngine / 调度 SyncCenter）+ records 表 `dirty` 列（Room v1→v2）；每次写即推（CONFLATED 合流）+ 回前台/前台 60s/推后即拉；occurredAt/babyId 走 payload 信封键（后端表无此两列）；LWW 判定 `data/model/SyncMerge.kt` 与后端、备份导入同语义；**轮后对账收敛（K11，`SyncConverge`）**：双机各建档案/播种任务分叉自愈——档案与每日必做保最旧，输家记录 `reassignBaby` 换挂（UPDATE 必须推进 updatedAt，否则信封 LWW 判同版跳过）；**首轮先拉后推 + 绑定/协议升级重置游标（K12）**：先推会把游标抬过对端历史 seq（拉空也是 200），`bind()` 与 `sync_proto_version` 落后时 `resetCursor()` 全量重放（LWW 幂等只补缺失）
- **当前状态：V1.0 与 V1.1 FR-20 云同步 App 客户端全部落地**（0016：我的页三态云同步弹层 + 记录人角标 + 清空墓碑分叉 + 播种双保险 + 双档案收敛 K11 + 首绑拉全历史 K12 + 引导页直通配对免建档 K13，150 单测全绿）；**待办：双机真机冒烟（0003~0016 一起跑）后发 V1.1**；正式功能开发走 `docs/features/` 流水线（见全局约定）
- 演示数据口径（小汤圆 / 2026-08-10 / 第 69 天等）以 `docs/UI设计说明.md` §5 为准，改设计稿时保持一致

## 注意

- 本仓目前不进 git 也可工作，但密钥类（将来若有云同步）一律环境变量/secrets，禁止硬编码
- 不自动 commit/push，除非明确要求
