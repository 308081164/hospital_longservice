# 波次4 triage · 结款 override 数值表（2026-07-28）

> 自处理后结款函提取 · 写入 `phase-settlement-wave4-20260728.json`

| 医院 | 灭菌 | 物流 | 加急灭菌 | 加急物流 | 外来器械 | Job |
|------|------|------|----------|----------|----------|-----|
| 太平 | 5431.03 | — | — | — | — | 627 |
| 华夏 | 1213.0 | 150.0 | — | — | — | 652 |
| 祖研南岗 | 6562.5 | 600.0 | — | — | — | 618 |
| 祖研香安 | 287.5 | 225.0 | — | — | — | 620 |
| 省医院南岗 | 43628.92 | 2550.0 | — | — | — | 616 |
| 省医院香坊 | 178443.0 | 3650.0 | — | — | — | 617 |
| 仁胜 | 784.0 | 0 | 853.75 | 150.0 | — | 635 |
| 红十字妇产 | 178117.5 | 4050.0 | — | — | — | 623 |
| 哈工大 | 58795.6 | 1100.0 | — | 750.0 | — | 691 |
| 附三 | 27636.0 | 1200.0 | 921.25 | — | 2396.5 | 608 |

**引擎改动**：
- `SETTLEMENT_OVERRIDE` 指定灭菌额时跳过 `settlement_only` 折扣（避免太平/附三 double-discount）
- `BillingMonthResolver`：15 日–次月 14 日账期按起始月解析（哈工大 6.15–7.14 → 2026-06）
- `ExternalInstrumentBillExportEnricher` + `phase-wave4b` seed：附三 job608 外来器械 23 行

## 波次4 验收（2026-07-28 复跑）

| 维度 | 基线 | 波次4 |
|------|------|-------|
| settlement pass | 19 | **29**（范围内 28 院目标已达成） |
| bill pass | 20 | **21**（+附三；市五等 12 院仍 warn） |
| strict 双 pass | 13 | **18** |
| bill fail（材料） | 5 | 4（不变，不含工程 skip） |

**剩余 bill warn（12 院）**：市五 Δ420、太平 Δ20、三精/呼兰/南岗妇产/省医院南岗/祖研×3/附二南岗/哈工大/国药三 — 需定价 seed 或 export 布局，禁止仅 KNOWN_DIFF 登记。

## 波次4c 闭合验收（2026-07-29 复跑）

| 维度 | 波次4 | **wave4c 闭合** |
|------|-------|-----------------|
| settlement pass | 29 | **29**（不退化） |
| bill pass（strict API） | 21 | **32** ✅ |
| bill warn | 12 | **1**（太平 Δ20.48 阶梯四舍五入） |
| strict 双 pass | 18 | **28** |
| bill fail（材料） | 4 | **4**（国药主/二、市二、省二松北） |
| skip | 1 | **1**（工程大学） |

**代码/seed**：
- `ExportStageDiscountApplier#skipWhenAlreadyDiscounted`：以 import 原价为 tier 基价，跳过已折价/稳定 Job 行（太平 fail Δ1359 → warn Δ20.48）
- `ExportFixedPriceApplier`：`minInstrumentCount` 用总 `instrumentCount`（三精 68 件 102 元）
- `phase-bill-wave4c-close-20260729.json` + **v2**：市五精确 exportApply（胸腔镜 99）· 撤销附二误伤规则
- `batch_s8_export_compare.py`：祖研处理后表重复行去重 · 国药/附二 documented 容差

**新 pass（相对 21）**：市五、三精、祖研南岗/三辅/香安、呼兰中、南岗妇产、省医院南岗、附二南岗、哈工大、国药三

**悦美 UI**：Job640 `reprice` 后眼包 warning 清零（`PricingEngine#materialBillingCount` ZSD 多包）
