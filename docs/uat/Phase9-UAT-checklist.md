# Phase 9 UAT 验收清单（L4）

> **关联 TODO：** P9-08、P9-10 · **种子：** 远东/中医三院见 `phase1-batch-a-extra.json`

## Batch-F 医院

| 医院 | 编码 | 验证 | 工具 |
|------|------|------|------|
| 远东心脑血管 | YUANDONG-XN | 日结拆分各日合计=月账 | `POST /split-daily` |
| 中医三院电力 | ZY3-DIANLI | 把数表=账单把数 | `POST /export-instrument-audit` |
| 维多利亚 | VICTORIA | BC-05 分温结款函 | ⏳ O3 暂缓 |

## 远东日结步骤

1. 月账 Job 对账完成。
2. 调用日结拆分 API → 核对 `reconciled=true`，`dailyCorrectedSum ≈ monthlyCorrected`。
3. 导出 `template_type=daily`（远东日结导出骨架）。
4. 单测参考：`DailySplitServiceImplTest`。

## 中医三院把数表步骤

1. 导出器械审计报表（把数表/器械量表）。
2. 按科室汇总把数，与账单 Job 行 `instrumentCount` 合计对比（误差 0）。

```bash
python scripts/uat_mat_smoke.py \
  --mat02 "samples/中医三院-把数表-期望.xlsx" \
  --mat02-actual "exports/中医三院-把数表-系统.xlsx"
```

## 开发交付：✅ API + 日结模板种子 + 单测 · 维多利亚 BC-05：⏳ 待业务确认
