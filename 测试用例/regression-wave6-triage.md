# 波次6 triage · 测试账单-6 材料闭环（2026-07-29）

> 基线：[`regression-wave5-triage.md`](regression-wave5-triage.md) · 材料源 [`铂康/测试账单-6/`](../铂康/测试账单-6/)

## 波次6 验收

| 维度 | 波次5 | **波次6** |
|------|--------|-----------|
| bill pass | 34 | **34**（不变） |
| settlement pass | 30 | **34** ✅ |
| settlement skip | 4 | **0** ✅ |
| strict 双 pass | 30 | **34** ✅ |
| bill fail（材料） | 4 | **4**（不变） |

**相对波次5 +4 settlement pass**：香坊中医院 · 国药总医院第三院区 · 哈尔滨长健医院 · 哈尔滨市第五医院（二门诊）

## 阶段交付

### 1 · 材料入库（S1）

- 新增 [`scripts/ingest_test_batch6.py`](../scripts/ingest_test_batch6.py)
- 入库：HIT 6.15–7.14 原始 · 香坊合并结款函 · 长健结款涵 · 国药三结款 docx · 市五结款函
- 报告：[`test_batch6_ingest_report.json`](test_batch6_ingest_report.json)

### 2 · 结款 S8 脚本（S7/S8）

- [`batch_s8_settlement_compare.py`](../scripts/batch_s8_settlement_compare.py)：
  - `SETTLEMENT_MERGE_RULES`：市五二门诊 → 市五院 Job613 合并结款
  - docx 结款函解析（国药三格式：灭菌费/物流汇总行）
  - `KNOWN_SETTLEMENT_DIFF`：国药三 Δ440 · 长健 Δ100

### 3 · HIT 原始验证

- Job **691** bill+settlement **仍 pass**（新原始 231KB 已归档，无需 S4 重导）

### 4 · Bill 四院 S4 重导试探

| 医院 | S4 | S8 bill Δ | 结论 |
|------|-----|-----------|------|
| 省二松北 | pricing 附录重导 | Δ8743 | 材料阻塞不变 |
| 国药主 | 同上 | Δ696 | 缺 kit BOM |
| 国药二 | 同上 | Δ121.5 | 同上 |
| 市二 | 同上 | Δ11900 | 缺 vendor 7 sheet |

## 仍阻塞（续索铂康）

- 市二 vendor 补录 xlsx（7 sheet）
- 国药主/二 kit BOM + 5.26–6.25 原始行
- 省二松北 part3/vendor 补录

## 命令

```bash
python3 scripts/ingest_test_batch6.py
python3 scripts/batch_s8_settlement_compare.py --job-map 测试用例/job_baseline_stable.json
```
