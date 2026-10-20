# 0010 · 我的页 — 交付说明

日期：2026-10-18 · 状态：已完成（真机冒烟待跑，与 0003~0009 一起最后过一遍）

## 验收对照（RequireSpec 验收 8 条）

1. **六区块齐全**：档案卡 / 提醒设置（灰置）/ 深色开关 / 功能卡（统计灰置+自动备份灰置+导出+导入）/ 危险卡 / 底部锁提示 → `MinePage.kt`，配色全走 BabyTheme tokens，深浅两态同源 ✅
2. **档案编辑**：昵称/生日（DatePicker 禁未来）/性别/出生体重/出生身长（选填小数）；保存走 `repo.update` 同 uuid，updatedAt 推进，第 N 天/疫苗推算联动；occurredAt 跟随新生日（与建档口径一致）✅
3. **深色开关**：`darkOverride` StateFlow + prefs 持久化，`MainActivity` 里 `override || isSystemInDarkTheme()` 立即全局生效，杀进程保持 ✅
4. **导出**：SAF `CreateDocument` 建议名 `babycare-backup-yyyyMMdd-HHmm.json`，内容 = 全量记录含软删墓碑（LWW 语义闭环）✅
5. **导入**：`OpenDocument`（json + octet-stream）→ 确认框报「新增 X · 更新 Y」→ `SyncMerge.shouldApply` LWW 合并（与后端/V1.1 客户端同一处判定）；坏文件/空备份报「无法识别」不崩 ✅
6. **清空**：确认框（红「清空」+「先导出」入口）→ TimerCenter 取消进行中计时 → records+meta 全清 → 各页 Flow 自动回引导页，Widget 走 changes 流刷无宝宝态 ✅
7. **灰置两行**：alpha 0.5 + 「V1.1」角标，点击 snackbar ✅
8. **单测**：76 全绿（+7：`BackupCodecTest` — round-trip、墓碑保留、LWW 覆盖/拒绝/相等、新增计数、空库、坏文件三类）✅

## 交付内容

| 类别 | 文件 |
| --- | --- |
| 备份编解码（纯 JVM） | `data/backup/BackupCodec.kt`（BackupDoc/BackupRecord/ImportPlan + encode/decode/planImport） |
| 页面 | `ui/mine/MinePage.kt`（六区块 + 档案编辑弹层 + 导入确认 + 清空确认 + SAF 双 launcher） |
| VM | `ui/mine/MineViewModel.kt`（MineUi 流 / createBaby / saveProfile / exportTo / readBackup / applyImport / wipeAll） |
| DAO | `RecordDao`（+allOnce/+upsertAll/+deleteAll）、`MetaDao`（+clear） |
| 仓库 | `BabyRepository`（+allOnce/+importAll/+wipeAll，写后 tryEmit changes 供 Widget 刷新） |
| 深色开关 | `BabycareApplication`（darkOverride + prefs）、`MainActivity`（collect 后传入 BabycareTheme） |
| 装配 | `ui/BabycareApp.kt`（MineViewModel 工厂 + 传 snackbar）；删除 `ui/pages/Placeholders.kt`（占位使命结束） |
| 测试 | `app/src/test/.../data/backup/BackupCodecTest.kt`（7 个） |

## 偏离设计

1. `MineViewModel` 增加了 `createBaby`（design 未列）：MinePage 无宝宝时显示引导（与其他 Tab 同模式），建档逻辑与 `TodayViewModel.createBaby` 同口径复制一份（data→domain 反向依赖，收不进 repo）。
2. `saveProfile` 同时更新档案记录 `occurredAt` 为新生日当天 0 点（design 未提）：与建档口径保持一致，档案记录的时间轴位置跟随真实生日。

## 踩坑（2026-10-18 真机补记）

- `darkOverride` 初版写成属性初始化器直接读 prefs（`val darkOverride = MutableStateFlow(prefs().getBoolean(...))`），装机即闪退：属性初始化早于 `attachBaseContext`，`mBase` 为 null 时 Context 方法全 NPE。已改 `by lazy`（0011 的 `feedInterval` 同修）。单测是纯 JVM，测不到 Application 生命周期——教训：动 Application 的改动当期至少真机装一次。

## 已知边界

- 深色开关是两态（关=跟随系统 / 开=深色），无「强制浅色」——RequireSpec Q2 拍板。
- 导入不导入亲喂计时暂态（meta 不进备份）：换机恢复进行中计时无意义。
- 「先导出」按钮触发 SAF 后若用户取消，回到我的页（清空弹层已关）——需要再点一次清空，属可接受的二次保护。
- V1.1 灰置三项（提醒设置/统计/自动备份）共用「即将上线」snackbar 文案各自独立。
- FR-13 提醒实装仍未做（Q1 拍板独立课题），V1.0 收尾时补。
