# Phase 1 UAT 验收清单

> **关联 TODO：** P1-12、UAT-A · **自动化：** `scripts/uat_mat_smoke.py`、`scripts/compare_export.py`  
> **目标：** Batch-A 核心医院 MAT-01→MAT-02 行一致率 ≥99%

## 验收医院

| 医院 | 客户编码 | MAT-01 | MAT-02 | 一致率 | 验收人 | 日期 |
|------|----------|:------:|:------:|:------:|--------|------|
| 黑龙江省第二医院（南岗院区） | ERYY-NG | 业务签字 | 业务签字 | — | | |
| 黑龙江省第二医院（松北院区） | ERYY-SB | 业务签字 | 业务签字 | — | | |
| 哈尔滨市红十字妇产医院 | HRB-HSZ | 业务签字 | 业务签字 | — | | |
| 黑龙江九洲妇科医院 | JIUZHOU-FK | 业务签字 | 业务签字 | — | | |

## 逐步执行脚本

### 步骤 1：环境准备

1. 确认 `BillingSeedMigrationRunner` 已执行（`sys_setting.billing_seed_profiles_v1=true`）。
2. 登录系统 → **主数据 → 客户管理**，核对上表客户 `billingEnabled=true`、`billingPricingMode=hybrid`（九州为 standard）。
3. 打开 **规则试算器**（客户编辑页），输入黄金样例行验证命中链。

### 步骤 2：导入与对账（每家医院）

1. 导入 MAT-01 未改账单 xlsx → 创建对账 Job。
2. 选择对应医院定价规则 → 执行对账。
3. 对账 UI 切换 **全部 / 差异 / 多报价** 三路视图。
4. 展开差异行，确认可见 `matchedRuleId`、规则名、`matchedPriceOption`、折扣链、策略链（NFR-01）。
5. 省二院：验证「钉」规则不命中「空心钉」（excludeKeywords）。
6. 省二院：小腔包/钉/3.6工具包多报价 `any_price` 命中提示。
7. **回归：** 关闭 `billingEnabled` 重新对账 → 不合并特色规则（INT-03 单测覆盖）。

### 步骤 3：MAT 自动化对比

```bash
# 行数与数值汇总冒烟
python scripts/uat_mat_smoke.py \
  --mat01 "samples/省二南岗-MAT01.xlsx" \
  --mat02 "samples/省二南岗-MAT02-期望.xlsx" \
  --mat02-actual "exports/省二南岗-MAT02-系统.xlsx" \
  --tolerance 0.01

# 单元格级 diff（可选）
python scripts/compare_export.py \
  "samples/省二南岗-MAT02-期望.xlsx" \
  "exports/省二南岗-MAT02-系统.xlsx" \
  --tolerance 0.01
```

### 步骤 4：记录与签字

```
医院：
账期：
MAT-01 文件：
MAT-02 文件：
总行数 / 一致行数 / 一致率：___%
差异样例（≤5 行）：
验收结论：通过 / 不通过
备注：
```

## 开发交付状态

| 项 | 状态 |
|----|:----:|
| 逐步脚本 | ✅ |
| 种子数据 JSON + Runner | ✅ |
| 自动化冒烟脚本 | ✅ |
| 业务 MAT 样表签字 | ⏳ 待客户提供 ≥2 月样表 |
