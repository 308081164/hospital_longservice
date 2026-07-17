# Phase 2 UAT 验收清单

> **关联 TODO：** P2-10 · **种子：** `billing-seeds/phase2-policies.json`  
> **目标：** 低消 / 物流 / 分温折扣 MAT-02 验收

## 验收医院

| 医院 | 编码 | 验证点 | MAT-02 | 验收人 |
|------|------|--------|:------:|--------|
| 黑龙江维多利亚妇产医院 | VICTORIA | 高温5折/低温7折 + 低消8000 | 业务签字 | |
| 呼兰区红十字医院 | HULAN-HSZ | 低消1500 | 业务签字 | |
| 悦美芳华医疗门诊医院 | YUEMEI-FH | 低消1000 | 业务签字 | |
| 省二南岗（物流） | ERYY-NG | 物流 80.5/次 | 业务签字 | |

## 逐步执行

1. **客户策略面板** → 确认 DISCOUNT scope.temperature、MONTHLY_SETTLEMENT、LOGISTICS 策略已种子加载。
2. 导入账期数据 → 对账 → Job 顶栏查看 **monthlyBreakdown** 摘要（P2-07）。
3. 维多利亚：分别导入高温行、低温行，核对折扣链与结款调整（BC-05 分温结款函暂 O3，账单侧验收）。
4. 呼兰红十字/悦美：灭菌费合计低于低消时，Job `settlementAdjustment` 补差正确。
5. 导出结款函 → MAT-03 灭菌费 + 物流 + 低消 = 总额（误差 ≤0.01）。

```bash
python scripts/uat_mat_smoke.py \
  --mat02 "samples/维多利亚-MAT02-期望.xlsx" \
  --mat02-actual "exports/维多利亚-MAT02-系统.xlsx"
```

## 开发交付：✅ 脚本 + 种子 + 自动化就绪 · 业务签字：⏳
