# 哈尔滨市呼兰区第一人民医院 — MAT-15 价格单

- 源文件（扫描 PDF）：[`哈尔滨市呼兰区第一人民医院价格单_000144.pdf`](./哈尔滨市呼兰区第一人民医院价格单_000144.pdf)  
- 铂康副本：`铂康/特殊价格单/哈尔滨市呼兰区第一人民医院价格单_000144.pdf`
- **OCR 全文**：[`OCR_价格单_000144.txt`](./OCR_价格单_000144.txt)（pymupdf 渲染 + tesseract `chi_sim+eng`；脚本 `scripts/ocr_special_price_pdf.py`）
- **结构化价目**：[`PDF价目_OCR结构化.json`](./PDF价目_OCR结构化.json)

## 系统已配置

- **账单明细 7 折**：`HULAN-RM` · `呼兰7折` / `呼兰 0.7 折扣`（`phase-hulan-heu-hit-20260722.json`、`phase1-batch-a-extra.json`）
- **PDF 固定价（OCR）**：`billing-seeds/phase-user-20260722-er-hulan.json` → `productRules`（敷料/纸塑袋/外来器械/保护套等；幂等按规则名插入）
- **外来器械关键词**：`phase-s7-sanjing-hulan-wailai-keywords-20260723.json` 将骨电钻/植入物改为 **外来器械（…）** 全短语（备案，待 M12）

## 说明

- PDF 为扫描件，个别数字已人工对照 OCR 校正；物流 **1.76 元/公里**、加急 **25%** 等仍在登记表备注，尚未全部落为 LOGISTICS 策略。
- 若某月对账与 PDF 行不一致，以 **`{月}期待价格校正清单.csv`** 为准补规则。
