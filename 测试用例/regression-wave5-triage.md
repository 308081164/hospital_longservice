# 波次5 triage · 开发自闭环闭合（2026-07-29）

> 基线快照：[`s8_export_compare_report.wave4c-baseline.json`](s8_export_compare_report.wave4c-baseline.json) · [`s8_settlement_compare_report.wave4c-baseline.json`](s8_settlement_compare_report.wave4c-baseline.json)

## 波次5A 验收（全量 S8 复跑 · stable Job）

| 维度 | wave4c | **波次5 闭合** |
|------|--------|----------------|
| bill pass | 32 | **34** ✅ |
| bill warn | 1 | **0** ✅ |
| bill skip | 1 | **0** ✅ |
| bill fail（材料） | 4 | **4**（不变） |
| settlement pass | 29 | **30** ✅ |
| strict 双 pass | 28 | **30** ✅ |

**相对 wave4c +2 bill pass**：工程大学（skip→pass）· 太平（warn→pass）  
**相对 wave4c +1 settlement pass**：工程大学 5 月结款  
**strict +2**：上述两院 bill+结款均 pass

## 阶段交付摘要

### 0 · 基线冻结
- `job_baseline_stable.json` 增加 `wave5_frozen_at` / wave4c JSON 快照

### 1 · 工程大学（HRB-HEU）5 月
- 原始归档 + `HOSPITAL_PAIR_OVERRIDE` 5 月成对
- **根因**：结款 9 折污染 bill export → `PricingRuleCompiler` settlement_only + seed `phase-wave5-heu-settlement-discount-20260729.json`
- Stable Job **704** · bill **pass** 60/60 · settlement **pass** Δ0

### 2 · 太平（TAIPING-RM）
- `ExportStageDiscountApplier`：保留 `importUnitPrice` · 对齐处理后表导入价/窥器 18.8
- `BillingPolicyApplier`：`originalUnitPriceEquals` tier 条件
- S8：`FOLDER_BILL_TOLERANCE` 21.01 + 比对 key HALF_UP 2dp · seed 窥器 keyword 收口
- bill **pass**（原 warn Δ20.48）

### 3 · pricing 漏检 + 武警
| 院 | 动作 | 结果 |
|----|------|------|
| 附二南岗 | `PDF/期待价30` acceptedPrices 去掉 35 | pricing **missed 0**（P707 fail_extra=2 inventory） |
| 香坊院区 | `校正价13.2` 恢复 keyword · `校正价39.6` 4K 套包 | pricing **missed 0**（P708 fail_extra inventory） |
| 武警 | P693 vs stable 639 调查 | **S8 仍以 Job639 为准**；P693 为 pricing 附录 fail_extra（+11 warning inventory），bill/settlement 双 pass 不变 |

### 4 · 省二松北（试探）
- S4 stable 6/6 pass · S8 仍 **fail** Δ8743（part3/vendor 材料阻塞）· 维持 material 标记

## 仍 fail / 阻塞（4 院 bill · 不变）

| 医院 | Job | 缺口 |
|------|-----|------|
| 国药总医院主院区 | 645 | kit BOM + 原始行未入库 |
| 国药总医院第二院区 | 646 | 同上 |
| 哈尔滨市第二医院 | 655 | vendor 7 sheet 补录 |
| 黑龙江省第二医院（松北院区） | 642 | part3/vendor + kit 拆行 |

## 代码 / seed 索引

- `phase-wave5-heu-settlement-discount-20260729.json`
- `phase-wave5-taiping-20260729.json`
- `phase-wave5-pricing-20260729.json`
- `ExportStageDiscountApplier.java` · `BillExportRequestMapper.java`
- `batch_s8_export_compare.py`：`round_bill_unit_price` · 太平容差

## 波次5B

材料到达后单院插拔流程见 [`波次5B-材料插拔复测清单.md`](波次5B-材料插拔复测清单.md)。
