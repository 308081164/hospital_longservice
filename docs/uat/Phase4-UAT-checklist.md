# Phase 4 UAT 验收清单

> **关联 TODO：** P4-14 · **验收医院：** 太平人民 / 武警总队 / 祖研香安

## 验证矩阵

| 医院 | 编码 | 能力 | 步骤 |
|------|------|------|------|
| 太平人民 | TAIPING-RM | 导出阶段折扣 `export_only` | 导入原价 → 对账 → 导出账单 diff |
| 武警总队 | WUJING* | ZERO_PRICE 0元覆盖 | 无纺布20/纸塑袋8/过氧化氢35 |
| 祖研香安 | ZUYAN-XA | FOLD 排针拆行 | 10盘/11-20盘阶梯 |

*武警编码以系统客户档案为准；规则见 Phase 5 种子或手工配置。

## 执行脚本

1. 太平：对账完成后导出，对比手写折扣价（单把1位小数、2把及以上2位小数）。
   ```bash
   python scripts/compare_export.py samples/太平-MAT02-期望.xlsx exports/太平-MAT02-系统.xlsx
   ```
2. 武警：0元行 `pathOverride.zeroPriceMode` 或 FIXED_PRICE 命中，对账 status=unchanged。
3. 祖研：排针包 FOLD 规则，行数可能增加（RowSplitter）。

## 开发交付：✅ · 业务 MAT 签字：⏳
