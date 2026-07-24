# 账单导出样式对比

## 根因（修复前）

- 对账页 **导出向导** 调用 `POST .../export-v2`，走 `StandardBillExportStrategy` 等 v2 策略。
- v2 账单是**从零生成的简表**：第 1 行标题、第 4 行表头（列名含「单号」而非「发货单号」），无「发货单汇总表-显示包装材料」、无账期 B4、无边框/列宽/科室汇总行布局。
- 若再降级到 `generateSimpleExcel`，数值会以**字符串**写入，Excel 会出现绿色「以文本形式存储的数字」警告。
- 正确样式在 **legacy POI 模板管线**（`generateBillExportBytes` → `createProgrammaticBillTemplate` / 物理 `bill.xlsx`），与 `export-template-bill` 一致。

## 修复

- `export-v2` 的 **bill / settlement** 改走 legacy 模板管线（与 `export-template-bill` 相同），香坊中医院与通用客户同路径。

## 对比文件

| 文件 | 说明 |
|------|------|
| `reference-妇幼6月.xlsx`（上级目录复制） | 用户提供的正确样例 |
| `修复前_standard_bill_v2_示意.xlsx` | 修复前 v2 简表布局示意 |
| `修复后_legacy模板_示意.xlsx` | 修复后 legacy 布局示意 |
| `../香坊中医院/处理后表格/6月__香坊中医院6月账单.xlsx` | 仓库内历史正确产出，与 reference 同结构 |

## 与 reference 的结构对齐点

- 扩展名：`.xlsx`（非 CSV）
- Sheet 名：科室名
- C1：发货单汇总表-显示包装材料
- B4：从:… 至:… 账期
- D8：医院/方案显示名
- 第 9 行表头：发货日期、**发货单号**、类型、包类别号、包名、包数、单价、总价
- 第 10 行：科室名 + 包数合计 + 总价公式
- 数据行：数值型包数/单价，总价 `=J*I` 或 SUM
