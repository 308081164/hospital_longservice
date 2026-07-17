# 非功能需求（NFR）验证

| ID | 描述 | 实现 / 验证 | 状态 |
|----|------|-------------|:----:|
| NFR-01 | 行级追溯 UI：`matchedRuleId`、`discountChain`、`policyTraces` | `ReconciliationBillingDetail.vue` + `reconciliationBillingNotes.ts` | ✅ |
| NFR-02 | 规则变更审计 | P8-12 `RuleChangeAuditService` | ✅ |
| NFR-03 | 单 Job 万行计价 <30s | `PricingEnginePerformanceTest`（CI 冒烟） | ✅ |
| NFR-04 | 权限细分：配置员 vs 业务员 vs 审核员 | 现有 RBAC 菜单级；字段级细分 **Phase 2 滚动** | ⏳ |

## NFR-03 运行方式

```bash
cd backend && mvn test -Dtest=PricingEnginePerformanceTest
```

阈值：10,000 行 × `processRow` < 30s（本地/CI）。

## NFR-01 UI 检查点

对账行展开面板应显示：

- 规则追溯区：`matchedRuleId`、规则名
- 折扣链区：`discountChain[]`
- 策略链区：`policyTraces[]`（物流/低消/分温等 policy 命中）
