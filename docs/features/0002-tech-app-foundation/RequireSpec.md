# RequireSpec · App 基础骨架

## 背景与目标

给 V1.0 打地基：主题 token、五 Tab 导航骨架、Room 数据层（同步就绪）。做完这步，「今天页 + 快捷记录」等页面开发只差 UI 本身。

## 功能范围

**做**：

1. 主题：Figma 25 个颜色 token（亮/暗两套）映射进 Compose；关闭动态取色（保品牌色）；字体沿用系统默认字阶
2. 导航：五 Tab（今天/日历/行程/成长/我的）底部导航，页面先用占位实现
3. 数据层：Room 两张表（records + meta），records 即同步就绪模型（uuid 主键、updated_at、软删、author）
4. 类型化记录模型：12 种记录类型的 payload data class + JSON 编解码（未知字段容忍）
5. 领域逻辑：第 N 天/月龄/百天计算；当日汇总（奶量/次数/睡眠/尿便）；LWW 合并判定
6. Application 装配：数据库 + 仓库单例，设备 ID 首次生成

**不做**（后续 feature）：页面真实 UI、widget、同步网络客户端、演示数据填充、设置项。

## 非功能需求

- JVM 单元测试可跑（领域逻辑纯 Kotlin，不依赖 Android 框架）
- 加新记录类型零迁移（payload 是 JSON 列）；加字段不破旧数据（解码忽略未知键）

## 约束与依赖

- 新增依赖：Room（runtime/ktx/compiler-KSP）、kotlinx.serialization（json + 插件）——除此之外不加
- minSdk 26 自带 java.time，不需要脱糖
- 版本需与本机 Kotlin 2.2.10 匹配（KSP）

## 验收标准

1. `gradlew assembleDebug` 通过，App 可安装启动，五 Tab 可切换，亮暗色随系统
2. 25 个 token 齐全且与 Figma 色值一致（单测抽样断言亮/暗各若干）
3. 第 N 天/月龄/百天：演示数据口径（2026-08-10 生，10-17 = 第 69 天 · 2 个月 7 天；百天 = 11-17）表驱动测试通过
4. 当日汇总：表驱动测试（8 次奶粉共 780ml、睡眠合计 14.2h、尿 6 便 2）
5. LWW 判定四分支（无本地/新/旧/相等）表驱动测试通过
6. payload 往返序列化 + 未知字段容忍测试通过
