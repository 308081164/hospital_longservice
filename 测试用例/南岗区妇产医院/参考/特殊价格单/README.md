# 南岗区妇产医院 — MAT-15 价格单

- 源文件（扫描 PDF）：[`哈尔滨市南岗区妇产医院价格单_000145.pdf`](./哈尔滨市南岗区妇产医院价格单_000145.pdf)
- 铂康副本：`铂康/特殊价格单/哈尔滨市南岗区妇产医院价格单_000145.pdf`（与测试目录 PDF 为同一价单）
- **2026-07-23 复核**：铂康目录与本文档 PDF 一致；规则已落在 `phase-ng-fuchan-pdf-ocr-20260723.json`，**未**在 `phase-s7` 重复落库
- **OCR 全文**：[`OCR_价格单_000145.txt`](./OCR_价格单_000145.txt)（pymupdf 渲染 + tesseract `chi_sim+eng`）
- **结构化价目**：[`PDF价目_OCR结构化.json`](./PDF价目_OCR结构化.json)

## 系统已配置

- **6 月期待价 / P0 校正规则**（`NG-FUCHAN`）：纱布 **2.3**、取环器/宫颈钳 **8**、弯盘 **16**、扩棒 **24**、盆2盘1杯1(腹腔镜) **32**、宫腔镜 **170.5**  
  - 种子：`phase-batch-p0.json`、`phase-s3-pdf-align-20260722.json`  
  - 增量补录：`phase-ng-fuchan-gongqiangjing-20260723.json`（marker `billing_seed_ng_fuchan_gongqiangjing_20260723_v1` / `…_v2`）
- **PDF 套包固定价（OCR）**：`billing-seeds/phase-ng-fuchan-pdf-ocr-20260723.json` → marker `billing_seed_ng_fuchan_pdf_ocr_20260723_v1`

## 说明

- PDF 为扫描件，主表序号 1–14 经多尺度 OCR + 人工对照；合同付款页（第 2 页）噪声较大，价目以第 1 页及表续「小治疗/小纱布」为准。
- **6 月期待价格校正清单.csv** 为零差异；若某月出现新包名与 PDF 不一致，以该月 `{月}期待价格校正清单.csv` 为准补规则。
- 价目表 **269.5 元（52 件套）**、**序号 13 单价 4 元** 等待账单包名确认后再落库。
