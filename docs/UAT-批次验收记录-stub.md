# UAT 批次验收记录

> 各 Phase 详细步骤见 [`docs/uat/`](./uat/) · 自动化：`scripts/uat_mat_smoke.py`、`scripts/compare_export.py`

## Batch-A（M1）— Phase 1–3

| 医院 | MAT-01 | MAT-02 | MAT-03 | 开发交付 | 业务签字 |
|------|:------:|:------:|:------:|:--------:|:--------:|
| 省二南岗 | 脚本就绪 | 脚本就绪 | 脚本就绪 | ✅ | ⏳ |
| 省二松北 | 脚本就绪 | 脚本就绪 | 脚本就绪 | ✅ | ⏳ |
| 呼兰一院 | 脚本就绪 | 脚本就绪 | 脚本就绪 | ✅ | ⏳ |
| 红十字妇产 | 脚本就绪 | 脚本就绪 | — | ✅ | ⏳ |
| 冰城医美 | 脚本就绪 | 脚本就绪 | 脚本就绪 | ✅ | ⏳ |

**Checklist：** [`uat/Phase1-UAT-checklist.md`](./uat/Phase1-UAT-checklist.md)、[`uat/Phase3-Batch-A-UAT.md`](./uat/Phase3-Batch-A-UAT.md)

## Batch-B（M2）— Phase 3–4

| 医院 | 导出 v2 | 结款函独立折扣 | 开发交付 |
|------|:-------:|:--------------:|:--------:|
| 道外人民 | ✅ | — | ✅ |
| 华夏眼科 / 三精肾病 | ✅ | — | ✅ |
| 武警总队 | ✅ | — | ✅ |
| 工程/九院/东大/先锋路 | ✅ | ✅ | ✅ |

**Checklist：** [`uat/Phase4-UAT-checklist.md`](./uat/Phase4-UAT-checklist.md)

## Batch-C（M2+）— Phase 4–6

| 医院 | 要点 | 开发交付 |
|------|------|:--------:|
| 呼兰中医 | 低消+物流种子 | ✅ |
| 太平/呼兰红十字/悦美 | Phase2/4 种子 | ✅ |
| 祖研×3 | 物流合并组 | ✅ |
| 新发红十字 | Phase6 UAT | ✅ |

## Batch-D（M3）— Phase 7

| 医院 | 要点 | 开发交付 |
|------|------|:--------:|
| 市五院 + 二门诊 | L3 分配+多 Sheet | ✅ |

**Checklist：** [`uat/Phase7-UAT-checklist.md`](./uat/Phase7-UAT-checklist.md)

## Batch-E（M4）— Phase 5–7

| 医院 | 要点 | 开发交付 |
|------|------|:--------:|
| 国药×3 / 市二院 / 省医院×2 / 中医大二院×2 | Batch-E 种子 | ✅ |

## Batch-F（M4+）— Phase 9

| 医院 | 器械量表/日结 | 开发交付 | 业务签字 |
|------|:-------------:|:--------:|:--------:|
| 维多利亚 | — | ⏳ BC-05 O3 | ⏳ |
| 远东心脑血管 | 日结 API + 模板 | ✅ | ⏳ |
| 中医三院电力 | 把数表 API | ✅ | ⏳ |

**Checklist：** [`uat/Phase9-UAT-checklist.md`](./uat/Phase9-UAT-checklist.md)

## 验收门禁（自动化）

| 门禁 | 验证方式 |
|------|----------|
| P8-14 试算=引擎 | `BillingConditionEvaluatorTest` + 黄金样例 |
| P9-09 日结=月账 | `DailySplitServiceImplTest` |
| P9-10 把数=账单 | UAT 脚本 + API |
| 导出 diff | `compare_export.py --batch manifest.json` |
