# 流程集成（INT）验证清单

> 关联 [`特色账单系统-开发TODO清单.md`](./特色账单系统-开发TODO清单.md) INT-01~05

| ID | 描述 | 验证方式 | 状态 |
|----|------|----------|:----:|
| INT-01 | 配置→导入→对账→导出单会话 ≤15 分钟（L1） | [`docs/uat/Phase1-UAT-checklist.md`](./uat/Phase1-UAT-checklist.md) 步骤 1–3 计时跑通 | ✅ |
| INT-02 | 月度结算→导出勾稽联动 | `ExportEngineService.validateBeforeExport` + 结款函 MAT-03 脚本 | ✅ |
| INT-03 | 关闭特色开关回退标准计价 | `BillingDisabledRegressionTest` + Phase1 UAT 步骤 2.7 | ✅ |
| INT-04 | 外来器械独立导入通道 | `POST .../external-instruments/import` + Phase7 UAT | ✅ |
| INT-05 | 物流独立导入与 Job 关联 | `LogisticsPipelineService` + `LogisticsImportController` + Phase5 UAT | ✅ |

## INT-01 15 分钟跑通脚本（L1）

1. **0–3 min：** 客户管理确认 Batch-A 医院种子与规则。
2. **3–8 min：** 上传 MAT-01 → 创建 Job → 对账完成。
3. **8–12 min：** 三路视图复核差异行；试算器抽 3 行 spot check。
4. **12–15 min：** 导出向导 → 勾稽弹窗 → 下载 MAT-02 → `compare_export.py` 冒烟。

## INT-06（文档化扩展项）

跨 Job 规则版本对比：使用 `RuleChangeAudit` 查询 + 对账 Job `ruleId` 字段追溯（运维手册项，无代码阻塞）。
