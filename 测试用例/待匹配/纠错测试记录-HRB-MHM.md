# 哈尔滨美涵美医疗美容（HRB-MHM）— 纠错能力测试（S5）

> 更新 · 2026-07-23 · 材料在 `测试用例/待匹配/` · 规范见 [`../S5纠错测试最小用例集.md`](../S5纠错测试最小用例集.md)

| 项目 | 值 |
|------|-----|
| **客户编码** | HRB-MHM |
| **S4 基线** | 待匹配 · 种子 `phase-hrb-mhm-xizhizhen-20260723.json` |

## 用例执行表

| # | 场景ID | 类型 / 包名（黄金行） | 故意错误 | 期望 status | 期望明细 | 自动化 | UI/Job | 结果 |
|---|--------|----------------------|----------|-------------|----------|--------|--------|:----:|
| 1 | EC-PRICE | `额外包(纸塑袋)` · 吸脂针(型号20cm以下)-5件 | `unitPrice=8.8`（应为 **8**） | `warning` | fold 后单价不符 | `PricingEngineTest#meihanmeiLiposuctionNeedleBelow20cmFoldsFiveToOne`（正确价对照） | 待匹配 4 月账单 | ✅引擎 · ⬜UI |
| 2 | EC-PACK | 吸脂针(型号20cm**以上**)-7件 | 改包名为无「20cm」关键字 | `warning` 或 unchanged+错误 fold | 折算规则未命中 | `PricingEngineTest#meihanmeiLiposuctionNeedleAbove20cmBillsByInstrumentCount` | 待测 | ✅引擎 · ⬜UI |
| 3 | EC-BILLING-OFF | 任意行 | 关 billing | `unchanged` | 保留原价 | `PricingEngineS5ErrorCorrectionTest#ecBillingOff_keepsOriginalPrice_unchanged` | 待测 | ✅引擎 · ⬜UI |

**结论：** 吸脂针 fold 规则已有单测；UI Job 待院目录迁入主验收清单后补跑。
