# 三精肾病医院 — MAT-15 价格单

- 源文件：[`哈尔滨市三精肾脏病医院价格单_000146.pdf`](./哈尔滨市三精肾脏病医院价格单_000146.pdf)
- 铂康副本：`铂康/特殊价格单/哈尔滨市三精肾脏病医院价格单_000146.pdf`
- **OCR 全文**：[`OCR_价格单_000146.txt`](./OCR_价格单_000146.txt)
- **结构化价目**：[`PDF价目_OCR结构化.json`](./PDF价目_OCR结构化.json)

## 系统已配置

- **6 月套包价**（`SANJING-SB`）：`phase-batch-p0.json`、`phase-s3-pdf-align-20260722.json`（插管包、内瘘包、腹透包等）
- **PDF 外来器械**：`phase-s7-bokang-pdf-ocr-20260723.json` → marker `billing_seed_s7_bokang_pdf_ocr_20260723_v1`；关键词补丁 `phase-s7-sanjing-hulan-wailai-keywords-20260723.json`（**外来器械（钉盒）** / **（骨电钻）**）

## 说明

- PDF 第 4 页及以后 OCR 噪声大，价目以附表一/六为主。
- 「内瘘器械包（一）」在 6 月清单出现 99 与 102 两种期待价：**按器械件数区分**（66件→99、68件→102）；增量种子 `phase-sanjing-neilou-instrument-count-fix-20260727.json`。
