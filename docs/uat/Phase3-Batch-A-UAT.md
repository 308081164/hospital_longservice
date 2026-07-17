# Phase 3 Batch-A UAT 记录（P3-18）

| 项目 | 内容 |
|------|------|
| **里程碑** | Batch-A ≥3 家 MAT-02/MAT-03，误差 ≤0.01 元 |
| **状态** | ✅ 脚本+自动化就绪 — ⏳ 待业务样例 xlsx 签字 |
| **工具** | `scripts/compare_export.py`、`scripts/uat_mat_smoke.py` |

## 验收医院

| 医院 | 编码 | MAT-02 账单 | MAT-03 结款函 | 备注 |
|------|------|:-----------:|:-------------:|------|
| 省二南岗 | ERYY-NG | 脚本就绪 | 脚本就绪 | `sheng_er_bill` |
| 呼兰一院 | HULAN-RM | 脚本就绪 | 脚本就绪 | 呼兰一院账单骨架 |
| 冰城医美 | BINGCHENG-YM | 脚本就绪 | 脚本就绪 | 冰城医美账单骨架 |

## 执行步骤

1. 对账 Job 审核通过后，在对账页使用 **导出向导**（export-v2）。
2. 导出前确认勾稽弹窗：差异行数、物流费、低消调整。
3. 与黄金样例 xlsx 对比：

```bash
python scripts/compare_export.py golden/省二南岗-账单-期望.xlsx exports/省二南岗-账单-实际.xlsx --tolerance 0.01

# 批量对比（manifest 示例见 scripts/export-diff-manifest.example.json）
python scripts/compare_export.py --batch golden/batch-a-manifest.json
```

4. 结款函：灭菌费合计 + 物流 + 低消调整 = 结款函总额（截图存档）。

```bash
python scripts/uat_mat_smoke.py --settlement golden/省二南岗-MAT03.xlsx exports/省二南岗-MAT03.xlsx
```

## 业务签字栏

| 医院 | 验收人 | 日期 | 结论 |
|------|--------|------|------|
| 省二南岗 | | | |
| 呼兰一院 | | | |
| 冰城医美 | | | |
