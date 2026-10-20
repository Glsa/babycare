# 0010 · 我的页 — 技术设计

日期：2026-10-18 · 前置：RequireSpec 已定稿

## 1. 总体

页面 = 纯展示 + 三个弹层（档案编辑 / 导入确认 / 清空确认）+ 两个 SAF launcher。数据逻辑收在 `MineViewModel`，备份编解码是**纯 JVM 无 Android 依赖**的 `BackupCodec`（可单测）。

## 2. 新增文件

### `data/backup/BackupCodec.kt`（纯 JVM）

```kotlin
@Serializable data class BackupDoc(version: Int, exportedAt: Long, records: List<BackupRecord>)
@Serializable data class BackupRecord(uuid, type, payload: String /*原文JSON*/,
    occurredAt: Long, updatedAt: Long, deleted: Boolean, author: String, babyId: String)

object BackupCodec {
    const val VERSION = 1
    fun encode(entities: List<RecordEntity>): String          // 全量含软删墓碑
    fun decode(text: String): BackupDoc?                       // runCatching 兜底，坏文件 null
    data class ImportPlan(newCount: Int, updateCount: Int, toApply: List<BackupRecord>)
    fun planImport(incoming: List<BackupRecord>, localUpdatedAt: Map<String, Long>): ImportPlan
    // LWW 复用 SyncMerge.shouldApply(local, incoming) —— 与后端/V1.1 同一处判定
}
```

序列化走既有 `RecordJson`（已配 ignoreUnknownKeys，向前兼容）。`BackupRecord.fromEntity()` / `.toEntity()` 做 RecordEntity 互转。

### `ui/mine/MineViewModel.kt`

- `baby: StateFlow<RecordEntity?>`（BABY_PROFILE 记录原文，编辑弹层直接解 payload 取体重/身长）
- `saveProfile(nickname, birthday, gender, weightKg?, heightCm?)` → `repo.update(entity, BabyProfilePayload(...))`（同 uuid，updatedAt 推进 → 第 N 天/疫苗推算自动联动）
- `buildBackup(): String` → `BackupCodec.encode(repo.allOnce())`
- `readBackup(uri): ImportPlan?`（contentResolver 读文本 + decode + `planImport(incoming, repo.uuidUpdatedAtOnce())`）→ 存 pendingPlan 供确认
- `applyImport(plan)` → `repo.importAll(plan.toApply.map { it.toEntity() })`（一个事务批量 upsert）
- `wipeAll()` → 先停进行中计时（TimerCenter 前台服务），再 `repo.wipeAll()`，changes 信号照发（Widget 自动刷成无宝宝态）
- 一次性结果用回调/事件流回 MinePage 弹 snackbar

### `ui/mine/MinePage.kt`

- baby = null → 显示 `OnboardingScreen`（与其他 Tab 同模式，清空后天然回引导）
- 有宝宝：六区块（档案卡 / 提醒灰置行 / 深色开关行 / 功能卡含两灰置+导出+导入 / 危险卡 / 底部锁提示）
- 设置行复用统一 `SettingRow(emoji, title, subtitle, trailing)`；灰置 = 内容 alpha 0.4 + 「V1.1」小标签，点击 snackbar
- 深色开关读 `app.darkOverride`，onCheckedChange → `app.setDarkOverride()`
- SAF（Activity Result API，无需任何权限）：
  - 导出 `CreateDocument("application/json")`，建议名 `babycare-backup-yyyyMMdd-HHmm.json`，回调 Uri → 写入 `buildBackup()`
  - 导入 `OpenDocument()`（mime `application/json` + `application/octet-stream`，部分文件管理器给备份标后者），回调 Uri → `readBackup` → 确认框「新增 X · 更新 Y」→ `applyImport`
- 档案编辑弹层：Dialog 内昵称/生日（DatePicker 禁未来，复用建档样式）/性别 FilterChip/体重/身长 OutlinedTextField（小数，选填）

## 3. 改动文件

| 文件 | 改动 |
| --- | --- |
| `data/dao/RecordDao.kt` | +`allOnce(): List<RecordEntity>`（全量含软删，导出用）、+`uuidUpdatedAt(): Map<uuid,updatedAt>`（导入比对用）、+`upsertAll(List)`（@Insert REPLACE 收 List，单事务）、+`deleteAll()` |
| `data/dao/MetaDao.kt` | +`clear()` |
| `data/repo/BabyRepository.kt` | +`allOnce()`、+`uuidUpdatedAtOnce()`、+`importAll()`（写完 tryEmit changes）、+`wipeAll()`（records+meta 全清，tryEmit changes） |
| `BabycareApplication.kt` | +`darkOverride: MutableStateFlow<Boolean>`（初始读 prefs `theme_dark`）+ `setDarkOverride(Boolean)`（写 prefs + emit） |
| `MainActivity.kt` | setContent 内 collect `app.darkOverride`，`BabycareTheme(darkTheme = override || isSystemInDarkTheme())` |
| `ui/pages/Placeholders.kt` | 删 MinePage 占位 |
| `ui/BabycareApp.kt` | import 改 `ui.mine.MinePage`，传 VM + snackbarHost |

`ui/theme/Theme.kt` 不动（`darkTheme` 参数已存在）。

## 4. 关键决策

- **导入 = 本地一次「下行」**：逐条 LWW，与 0001 后端、V1.1 同步客户端共用 `SyncMerge`，永不出现「更旧的备份覆盖新数据」。
- **导出含墓碑**：软删记录进备份，保 LWW 语义闭环（删除也能随备份迁移）。
- **亲喂计时暂态不导**：meta 不进备份（换机恢复进行中计时无意义）。
- **深色持久化走 prefs 不进 Room**：设置不是业务数据，与 deviceId 同层。
- **清空先停计时**：`TimerCenter` 有进行中睡眠/亲喂时按同路径结束（不落记录）再清库，避免前台服务悬空引用已清数据。

## 5. 测试计划

`BackupCodecTest`（纯 JVM，+7 左右）：

1. round-trip：entities → encode → decode 字段全等（含 payload 原文）
2. 墓碑在导出里（deleted=true 保留）
3. LWW 覆盖：同 uuid，incoming.updatedAt 更大 → 进 toApply
4. LWW 拒绝：incoming.updatedAt 更小/相等 → 不进 toApply
5. 新增：本地无该 uuid → 计入 newCount
6. 计数正确：混合列表 new/update 数目对
7. 坏 JSON（截断/非 JSON/缺字段）decode → null 不抛

验收跑 `:app:testDebugUnitTest`（预期 69 + 新增全绿）+ `assembleDebug`。
