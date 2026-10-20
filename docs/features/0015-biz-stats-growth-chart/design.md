# 0015 统计与生长曲线 · design

## §1 纯函数层（domain）

### StatsKit.kt

```kotlin
data class DayStat(
    date / formulaMl / formulaCount / nursingCount / nursingMinutes /
    peeCount / poopCount / sleepMillis / nightMillis / eduCount
)
object StatsKit {
    // 全量 records → 连续日序列；7/30 档从 today 往回补零；全部档从最早记录日（无记录回退 today）
    fun dailyStats(records, days: Int?, zone, today, now): List<DayStat>
    // 生长点：(月龄 = days/30.4375, 值)，按 occurredAt 升序；只留该指标非空的记录
    fun growthPoints(records, birthday, metric): List<Pair<Double, Double>>
    fun toCsv(stats): String   // 表头中文，逗号分隔，睡眠小时保留 1 位
}
enum class GrowthMetric { WEIGHT, HEIGHT, HEAD }  // label/unit 扩展属性
```

聚合复用 computeDaySummary 的口径但**独立实现**（要按天分桶 + 夜觉拆分，DaySummary 无夜觉字段）。睡眠跨天：按 startAt 落桶（与今天页同口径）。

### WhoGrowth.kt

```kotlin
object WhoGrowth {
    // 表：男/女 × 三项 × P3/P50/P97，各 25 个月度值（0–24 月），WHO 标准（近似，0.1 精度）
    fun value(gender: String, metric: GrowthMetric, percentile: Int, monthAge: Double): Double
    // 线性插值；月龄 <0 钳 0，>24 钳 24
}
```

## §2 图表层（ui/stats/StatsCharts.kt）

- `BarChart(values1, values2?, labels, unitText, tint1, tint2)`：双系列并列柱（奶量=瓶喂/亲喂）或单系列；无数据画空态文案；柱顶不标数（太密），卡头给合计/日均
- `GrowthChart(points, gender, metric, birthday)`：y 轴范围 = 用户点与 P3/P97 的并集；P3–P97 半透明填充带 + P50 虚线 + 用户折线圆点；x 轴标注月龄刻度（0/6/12/18/24）
- `HeatMap(cells, weeks)`：近 12 周 × 7 行格子，透明度 = 当日早教打卡次数（0/1/2/≥3 四档），周一为行首

全部 `Canvas` 自绘，色值走 `BabyTheme.colors`，无交互（个人工具够用）。

## §3 页面与接线

- `ui/stats/StatsPage.kt`：TopBar（返回 + 「统计」+ 导出按钮）+ LazyColumn：范围 chips → 奶量卡 → 尿布卡 → 睡眠卡 → 生长曲线卡（指标 chips）→ 早教热图卡 → 底部说明（WHO 近似声明）
- `ui/stats/StatsViewModel.kt`：combine 6 个 observeByType 流（FEED_FORMULA/FEED_NURSING/DIAPER/SLEEP/GROWTH/EDU_CHECKIN）+ baby + 范围 state → UiState（沿用 0008「全量不重订阅、内存滤」口径）；CSV 导出照抄 MineViewModel.exportTo
- `BabycareApp`：`showStats` 全屏层盖住 Scaffold；`MinePage(onOpenStats = { showStats = true })`，统计行去灰
- MinePage 统计行 subtitle 改为「奶量/睡眠/生长曲线」

## §4 落点清单

| 文件 | 动作 |
| --- | --- |
| `domain/StatsKit.kt` / `domain/WhoGrowth.kt` | 新增 |
| `ui/stats/StatsCharts.kt` / `ui/stats/StatsPage.kt` / `ui/stats/StatsViewModel.kt` | 新增 |
| `ui/BabycareApp.kt` | 加 showStats 层 + StatsViewModel 装配 |
| `ui/mine/MinePage.kt` | 统计行去灰 + onOpenStats |
| 测试 `StatsKitTest` / `WhoGrowthTest` | 新增 ~8 条 |

## §5 测试计划

StatsKitTest：分桶与补零（7 天档无记录日）、夜觉拆分、跨类型混合、growthPoints 月龄换算与过滤、CSV 表头与行数。WhoGrowthTest：插值中点、边界钳制、男女不同值、三项指标可查。
