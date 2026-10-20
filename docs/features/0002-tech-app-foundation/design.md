# design · App 基础骨架

## 1. 关键取舍

| 决策点 | 选择 | 理由（含备选） |
| --- | --- | --- |
| 记录存储 | **单表 records + 类型化 payload JSON 列**（镜像后端 0001 的模型） | 同步客户端零转换（App 行 ↔ 后端 record 字段一一对应）；加类型/加字段零 Room 迁移。备选「每类型一张表」：SQL 聚合方便，但 12 张表 + 同步要做联合映射，维护成本高；家庭数据量（百条/天）下 Kotlin 内存汇总毫无压力 |
| 序列化 | kotlinx.serialization | Kotlin 原生、编译期生成、`ignoreUnknownKeys` 天然前向兼容。备选 Moshi/org.json：前者要 codegen/reflect，后者手写字段 |
| 导航 | Scaffold + `when(tab)` 切换，不引导航库 | 五个平级 Tab 无深链需求；省一个依赖 |
| 时间 | java.time（minSdk 26 内建） | 不引 kotlinx-datetime，无脱糖 |
| 图标 | Tab 用 emoji（与 Figma 设计稿一致） | 省 material-icons-extended 大依赖 |
| 主题扩展色 | MaterialTheme 承载基础角色 + `LocalBabyColors` 承载 25 token 全集 | M3 色板只有十来个角色，装不下类型色；CompositionLocal 扩展是官方惯用法 |

## 2. 数据模型

```
records 表（与后端 0001 records 镜像 + App 侧冗余列）
  uuid       TEXT PRIMARY KEY   ← 客户端生成，全生命周期不变（同步主键）
  type       TEXT               ← 12 种之一（见下）
  payload    TEXT               ← 类型化 JSON（kotlinx.serialization）
  occurredAt INTEGER            ← 发生时间 ms（可补录改写；查询/汇总用这列，区别于 updatedAt）
  updatedAt  INTEGER            ← 修改时间 ms（LWW 依据）
  deleted    INTEGER            ← 软删墓碑
  author     TEXT               ← 设备 ID（同步后即「谁记的」）
  babyId     TEXT               ← 冗余列便于过滤（多宝宝预留）
  索引：type / occurredAt / babyId

meta 表（key-value）：sync_cursor、active_baby_id 等
```

记录类型（type 常量，payload 对应 data class）：
`baby_profile / feed_formula / feed_nursing / diaper / bath / sleep / growth / edu_checkin / vaccine_dose / supplement_plan / supplement_log / agenda`

## 3. 模块与文件拆分

```
com.babycare.app/
├── BabycareApplication.kt      Application：db/repo 单例、设备 ID
├── MainActivity.kt             入口
├── data/
│   ├── entity/RecordEntity.kt  entity/MetaEntity.kt
│   ├── dao/RecordDao.kt        dao/MetaDao.kt
│   ├── db/BabyDatabase.kt
│   ├── model/RecordTypes.kt    type 常量
│   ├── model/Payloads.kt       12 个 payload data class + 编解码
│   ├── model/SyncMerge.kt      LWW 判定（纯函数）
│   └── repo/BabyRepository.kt  增/查/软删/observe
├── domain/
│   ├── AgeCalc.kt              第N天/月龄/百天（纯函数）
│   └── DaySummary.kt           当日汇总（纯函数，入参 records 出参数值）
└── ui/
    ├── theme/Color.kt Theme.kt Type.kt
    ├── BabycareApp.kt          五 Tab Scaffold
    └── pages/Placeholders.kt   五个占位页
```

测试（`app/src/test`，纯 JVM）：AgeCalcTest / DaySummaryTest / SyncMergeTest / PayloadsTest / ColorTokensTest。

## 4. 主题映射

- `BabyColors` data class：25 token 与 Figma 变量同名（bg/surface/surfaceAlt/divider/text1..3/primary(+Soft)/七类类型色(+Soft)，nurse 与 primary 同值）
- MaterialScheme 映射：primary→primary、background→bg、surface→surface、surfaceVariant→surfaceAlt、onBackground/onSurface→text1、onSurfaceVariant→text2、outlineVariant→divider、error→vaccine
- `dynamicColor` 移除（品牌色优先于壁纸取色）
- 页面取色统一 `MaterialTheme.colorScheme.*`（基础）+ `BabyTheme.colors.*`（类型色等扩展）

## 5. 风险与兜底

| 风险 | 兜底 |
| --- | --- |
| KSP/Room 版本与 Kotlin 2.2.10 不匹配 | 构建即失败即改版本；锁死在 toml 单点 |
| payload 加字段后旧 App 读新数据 | 解码 `ignoreUnknownKeys`（单测锁定） |
| occurredAt 与 updatedAt 混用导致汇总错位 | 汇总/时间轴一律 occurredAt；updatedAt 只给同步用（领域函数签名分离） |
| 未来多宝宝切换 | babyId 列已在，V1 固定取单宝宝 |
