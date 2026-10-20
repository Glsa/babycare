# submit · App 基础骨架（主题 + Room 数据层 + 导航）

> 2026-10-18 交付。需求基线：[RequireSpec](RequireSpec.md) · 设计：[design](design.md)

## 功能点勾对验收标准

| 验收标准（RequireSpec） | 结果 |
| --- | --- |
| 1. `assembleDebug` 通过，五 Tab 可切换，亮暗色随系统 | ✅ 构建通过；亮暗双 token + isSystemInDarkTheme 切换。**真机冒烟未跑**（本机无连接设备），装 APK 后看五 Tab 与暗色即可确认 |
| 2. 25 token 齐全且与 Figma 一致 | ✅ `ColorTokensTest` 亮/暗抽样断言（10 个色值） |
| 3. 第 N 天/月龄/百天 表驱动 | ✅ `AgeCalcTest`（含 10-17=第 69 天、2个月7天、百天=11-17、生日当天=第1天、早于生日抛错） |
| 4. 当日汇总 表驱动 | ✅ `DaySummaryTest.demoDay`（8 次 780ml、睡眠 14.2h 含进行中、尿 6 便 2、洗澡/早教） |
| 5. LWW 四分支 | ✅ `SyncMergeTest` |
| 6. payload 往返 + 未知字段容忍 | ✅ `PayloadsTest`（12 类型全往返、ignoreUnknownKeys、坏 JSON 容忍、默认值编码） |

`testDebugUnitTest`：16 个用例 0 失败 0 错误。

## 文件清单

```
app/src/main/java/com/babycare/app/
├── BabycareApplication.kt          # db/repo 单例 + 设备 ID（同步 author）
├── MainActivity.kt                 # 入口（占位 Greeting 移除）
├── data/
│   ├── entity/RecordEntity.kt      # records 单表：uuid PK/occurredAt/updatedAt/软删/author/babyId
│   ├── entity/MetaEntity.kt        # key-value（sync_cursor 等）
│   ├── dao/RecordDao.kt MetaDao.kt
│   ├── db/BabyDatabase.kt
│   ├── model/RecordTypes.kt        # 12 种 type 常量
│   ├── model/Payloads.kt           # 12 种 payload + 编解码（前向兼容）
│   ├── model/SyncMerge.kt          # LWW 判定（与后端同语义）
│   └── repo/BabyRepository.kt      # 增/改/软删/observe/applyIncoming
├── domain/AgeCalc.kt               # 第N天/月龄/百天
├── domain/DaySummary.kt            # 当日汇总
└── ui/
    ├── theme/Color.kt Theme.kt     # 25 token 亮/暗 + M3 映射 + BabyTheme.colors
    ├── BabycareApp.kt              # 五 Tab Scaffold
    └── pages/Placeholders.kt

app/src/test/java/com/babycare/app/  # 5 个测试类 16 用例
```

构建配套：`gradle/libs.versions.toml`（room 2.8.4 / ksp 2.2.10-2.0.2 / serialization 1.9.0）、`gradle.properties`（`android.disallowKotlinSourceSets=false`，KSP 挂生成源需豁免）、compileSdk 36.1→37（Room 2.8 依赖链要求；targetSdk 仍 36）。

## 实现要点（与设计一致）

- **单表 + 类型化 payload**：App 行 ↔ 后端 0001 record 字段一一对应，同步客户端零转换；加类型/加字段零 Room 迁移
- **occurredAt / updatedAt 严格分离**：展示汇总用前者，LWW 用后者，函数签名上就分开了
- **前向兼容双保险**：解码 `ignoreUnknownKeys` + 未知 type 宽松返回 null（新设备产生的记录旧设备跳过不崩）
- **不用动态取色**：品牌暖色优先；类型色走 `BabyTheme.colors` 扩展口，M3 只装基础角色
- Tab 用 emoji 图标（与设计稿一致），省 material-icons-extended

## 测试覆盖与明确未覆盖

未覆盖：Room DAO 层（需 Robolectric/instrumented，DAO 目前是薄查询，等今天页接上真实数据后随集成一起补）；真机冒烟（无设备）。

## 遗留与后续

1. **0003 · 今天页 + 快捷记录**（奶粉/尿布先）：接 DaySummary 与 BabyRepository 出真实 UI
2. 首次启动引导（宝宝档案录入，FR-1）随「我的」或今天页空态做
3. DAO 层测试 + 演示数据 seeder（开发调试用）
