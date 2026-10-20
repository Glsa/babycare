# 0015 统计与生长曲线 · 交付说明

## 交付内容

FR-16 提前到本期：我的页「统计与生长曲线」行可点，进入全屏统计页。范围切换（近 7 天/近 30 天/全部）+ 喂养双柱（瓶喂 ml/亲喂分钟）+ 尿布（尿/便）+ 睡眠（总时长 + 夜觉占比）+ 生长曲线（体重/身高/头围三切，WHO P3–P97 参考带 + P50 虚线）+ 早教打卡热图（近 12 周）+ CSV 导出（SAF，带 BOM）。

## 落点

| 文件 | 内容 |
| --- | --- |
| `domain/StatsKit.kt` | 纯函数：dailyStats 按天分桶（7/30 档补零，全部档从最早记录起；睡眠按 startAt 落桶、夜觉按 tag 拆出、进行中按 now 折算）、growthPoints 月龄换算、toCsv |
| `domain/WhoGrowth.kt` | WHO 0–24 月参考表（男女 × 三项 × P3/P50/P97，月度硬编码线性插值），性别未知返回 null 退化不画带 |
| `ui/stats/StatsCharts.kt` | StatsCard 卡壳 + BarChart（双系列并列柱、x 轴标签抽稀、空态）+ GrowthChart（参考带 Path 填充/P50 虚线/用户折线，y 轴标签 Canvas 外排版免 TextMeasurer）+ EduHeatMap（周一为行首，未来日期留白） |
| `ui/stats/StatsViewModel.kt` | 六类记录流合并（combine Iterable 重载绕开 5 参上限），CSV 导出照抄 FR-19 备份导出写法 |
| `ui/stats/StatsPage.kt` | 全屏页：TopBar（←/⬇️ emoji 按钮与项目约定一致）+ BackHandler + 五张卡 |
| `ui/BabycareApp.kt` | Box 包 Scaffold，showStats 全屏层盖住底栏 |
| `ui/mine/MinePage.kt` | 统计行去灰，subtitle「奶量/睡眠趋势 · WHO 生长参考」+ onOpenStats |

## 设计要点

- 全部 Canvas 自绘，零第三方图表库；色值走 BabyTheme tokens（feed/nurse/diaper/bath/sleep/growth/edu）
- WHO 表为近似值（0.1 精度，P3/P97 部分月份按 SD 推得），卡内固定标注「儿保评估以标准生长曲线图为准」
- 档案没填性别 → 参考带不画、只画用户曲线；无任何数据 → 空态文案，坐标兜底 0–1 不崩

## 砍掉项（Request 拍板）

喂养间隔分布、小睡分布（低价值）；导出图片（CSV + 手动截图可代，后移）。

## 验证

- 113 单测全绿：StatsKitTest 6（分桶补零/夜觉拆分/进行中折算/全部档起点/月龄换算/CSV）+ WhoGrowthTest 4（插值/钳制/性别指标/三档单调）
- `:app:assembleDebug` 通过
- 真机冒烟待跑（图表渲染、导出的 CSV 在 Excel/WPS 打开中文正常）

## 增量 · App 图标替换（2026-10-20 回填）

替换 AGP 默认机器人：`drawable/ic_launcher_background.xml`（品牌粉 #E8697A 纯色）+ `drawable/ic_launcher_foreground.xml`（白色宝宝脸：耳朵 + 脸 + 眼/笑 evenOdd 镂空 + 呆毛，全画在 66dp 安全区内；镂空而非叠色，安卓 13 monochrome 主题图标单色渲染也能看出五官）。`mipmap-anydpi/ic_launcher.xml` 原有 adaptive 引用不动；minSdk 26 恒走 adaptive，遗留 mipmap PNG 不会被用到。

