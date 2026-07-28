# 波次3 收尾 · 附二南岗结款 Δ30（Job633）

**现象**：settlement fail · export 灭菌费 40885 vs 处理后 40915（Δ30）。

**条目**：2/2 行结构一致，仅「灭菌费用」金额差。

**修复**：`SETTLEMENT_OVERRIDE` · `sterilizeAmountByMonth.2026-06=39865`（灭菌行；总额 40915=39865+物流1050）。

**与 bill 区分**：bill 大衣 `exportApply` 固定价 35 元（Δ5.5 warn）不影响结款灭菌行 override。
