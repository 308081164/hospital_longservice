# Phase 6 UAT 验收清单

> **关联 TODO：** P6-08 · **医院：** 新发红十字（XINFA-HSZ）

## 验证项

| 项 | 操作 | 期望 |
|----|------|------|
| 加急标记 | 对账行勾选 → 批量「标记加急」 | `is_urgent=1`，单价 ×125% |
| 加急减免 | 策略 URGENT reducedRate | 结款函 102.5% |
| 设备抵扣 | DEDUCTION -3270 | 结款函独立抵扣行 |
| 固定价 | 穿刺器帽 | 22 元 |

## 步骤

1. 确认种子 `phase1-batch-a-extra.json` 中 XINFA-HSZ 策略已加载。
2. 导入账期 → 对账 → 标记部分行加急。
3. 导出结款函 → 勾稽：灭菌费 + 加急费 + 物流 − 抵扣 = 总额。
4. 截图存档 MAT-03。

```bash
python scripts/compare_export.py samples/新发-MAT03-期望.xlsx exports/新发-MAT03-系统.xlsx --settlement-total-col 9
```

## 开发交付：✅ · 业务签字：⏳
