# Request · App 基础骨架（主题 + Room 数据层 + 导航）

> 原样录入。

## 用户输入（2026-10-17）

> 继续

（指按上轮确认的顺序继续 App 侧 V1.0 开发：步骤 1 = 主题骨架 + Room 数据层）

## 关联上下文

- [PRD](../../产品需求文档.md) FR-20：数据层从第一天按同步就绪模型建表（uuid/updated_at/deleted/author）
- [UI设计说明](../../UI设计说明.md) §2：25 个颜色 token（亮/暗两套）、字体阶梯
- 后端已完成（[0001](../0001-biz-cloud-sync-backend/design.md)）：单表 records（type + payload JSON）+ 游标 + LWW——App 数据模型应与之镜像，同步客户端零转换
