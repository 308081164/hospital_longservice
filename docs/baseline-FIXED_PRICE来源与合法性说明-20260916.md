# baseline FIXED_PRICE 来源与合法性说明

| 项 | 内容 |
|----|------|
| **编制日期** | 2026-09-16 |
| **核查对象** | 当前 baseline 中 **56 条** `FIXED_PRICE` 规则（2026-09-17 权威对齐删除 19 条后），分布于 **11 家**医院 |
| **关联文档** | `docs/Fixed校正价规则全库清单-按医院分类-20260916.md`（附录明细）、`docs/FIXED_PRICE与特殊收费Excel逐条配对表-20260917.md`（**逐条 Excel 行号配对**）、`docs/计费规则迁移与验收规范.md`、`docs/电力校正价来源与合法性核查报告-20260916.md` |
| **核查方法** | baseline 全量扫描；归档种子/ manifest 溯源；G4 Excel↔manifest 对账报告交叉引用；P0 废止种子对照；`scripts/fixed_price_excel_pairing_audit.py` 逐条配对 |

---

## 摘要结论（Executive Summary）

1. **56 条 FIXED_PRICE 与已删除的 85 条「校正价」是不同集合**：当前规则面 **零**规则名含「校正价」；D 类 `PDF/期待价30` 已删（2026-09-16）。
2. **权威来源口径（2026-09-17 更新）**：**仅用户指定 `特殊收费(4).xlsx` 为权威**；baseline 中每条规则须在 `(4)` 有对应行，否则删除（见 [`特殊收费(4)权威对齐删除清单-20260917.md`](特殊收费(4)权威对齐删除清单-20260917.md)）。
3. **分级统计（2026-09-17 权威对齐后）**：
   - **与 `(4)` EXACT 配对**：**56 条**（100%）
   - **无 Excel 行（已删除）**：**18 条**（总工会 14 + GUOYAO-MAIN 驱血带 + GUOYAO-2 高温纸塑袋 + 祖研 2）
   - **D 类 P0/PDF 期待价同族**：**0 条**
4. **G0 副本**：`docs/source/特殊收费(1).xlsx` 与用户 `(4)` MD5 一致，已同步。

---

## 一、权威来源定义（项目规范）

依据 `docs/计费规则迁移与验收规范.md`：

| 层级 | 来源类型 | 是否算「权威」 | 说明 |
|------|----------|----------------|------|
| **G0** | 用户指定 `特殊收费(4).xlsx`；仓库副本 `docs/source/特殊收费(1).xlsx` 须与其逐行一致 | ✅ 最高 | 2026-09-17：不以旧专报/zgh Excel 保留无行规则 |
| **G4** | openpyxl 解析 Excel → 与 manifest/baseline **逐条**比对 PASS | ✅ 工程验收权威 | 29 家（2026-09-02 报告）全量段落对账 |
| **G5** | 路径 A 严格 Excel 对账（raw vs proc） | ✅ 运行正确性 | 算价与处理后账单一致，不替代 Excel 授权 |
| **种子归档** | `phase-special-charge-*` 等增量种子 | ⚠️ 溯源线索 | 证明「何时、由谁写入」，不等于客户已确认 |
| **P0 期待价校正** | `legacy-2026-obsolete-correction-price/phase-batch-p0.json` | ❌ 已废止 | 2026-09-08 全删 + 2026-09-16 用户决策再次确认废止 |
| **PDF/OCR 价表** | `phase-s3-pdf-align`、`phase-s7-bokang-pdf-ocr` 等 | ❌ 非 Excel 权威 | G4 报告明确「不列差异、不建议保留」为正式依据 |
| **baseline 单存** | 仅因写入 baseline 即声称合法 | ❌ 不足 | 须 G4 或客户书面确认 |

**FIXED_PRICE 工程惯例**（与合法性无关但必须满足）：`skipPackaging=true` + `skipDiscount=true`。当前 76 条 **全部符合**。

---

## 二、非法「校正价」 vs 合法 FIXED_PRICE 判别标准

| 维度 | 非法校正价（已删除体系） | 合法 FIXED_PRICE（可保留候选） |
|------|--------------------------|--------------------------------|
| **规则命名** | `校正价*`、`电力校正价*`、`PDF校正价*` | 院级业务语义名（如 `总工会清宫包固定价35`） |
| **业务来源** | 原始账单 vs 处理后账单 **反推期待价**（P0） | 《特殊收费》Excel **逐条建模** |
| **Excel 行** | 特殊收费 Excel **无对应行** | G4 对账 **有对应行**或用户更新版 Excel 已确认 |
| **种子文件** | `phase-batch-p0.json`（obsolete-correction-price） | `phase-special-charge-*`、`phase-special-v8-rules-*` |
| **2026-09-08 处置** | 85 条 **删除** | 不在删除清单内（或已用 Excel 规则 **替换** P0 同名条目） |
| **命名伪装** | 改名 `电力校正价*` 仍属同族 | 不得用改名规避 `CorrectionPriceRedundancyAuditTest` |
| **PDF/期待价** | `PDF/期待价30` 与 P0 `校正价30.0` **同关键词同价** | 须单独客户确认或补录 Excel |

**与 ZY3 电力校正价的区别**：电力四条已删除，因其 **无 Excel 行**且来自 P0 反推。当前 76 条中 **69 条**有 G4 PASS 或等价 Excel 种子；**7 条**缺口见第五节。

---

## 三、12 家医院逐院说明

> 规则明细见 `docs/Fixed校正价规则全库清单-按医院分类-20260916.md` 附录。

| # | 医院 | CODE | 条数 | 主要来源 | 首入仓线索（种子/commit） | G4 覆盖 | 合法性结论 |
|---|------|------|------|----------|---------------------------|---------|------------|
| 1 | 博尚医院 | BOSHANG-YY | 3 | 特殊收费 Excel | `phase-special-v8-rules-20260814.json`；baseline 迁移 `2cc87abf`（2026-09-07） | ✅ PASS | **已证实**（旋切器 66/44/线 66） |
| 2 | 国药总医院第二院区（电机厂） | GUOYAO-2 | 22 | 特殊收费 Excel 电机厂段 | excel17-align | ✅ PASS | **已证实**（2026-09-17 删误建模 `电机厂高温纸塑袋` 3.5） |
| 3 | 国药总医院主院区 | GUOYAO-MAIN | 1 | 特殊收费 Excel row136 | phase5 + Excel | ✅ EXACT | **已证实**（2026-09-17 删 `驱血带`；保留 10mm30度镜 28 元） |
| 4 | 黑龙江总工会医院 | HL-ZGH | 0 | — | — | — | **2026-09-17 删 14 条 FIXED_PRICE**；保留 5 条 EXTRA_FEE |
| 5 | 哈尔滨市第五医院 | HRB-WY | 8 | 特殊收费 Excel 市五院段 | `phase-special-v8-rules-20260814` + excel17-align | ✅ PASS | **已证实**（止血带/布袋/手套等 8 条；**注意**：P0 曾另有 6 条 `校正价*` 已删，与现行 8 条 **关键词不同**） |
| 6 | 哈尔滨市第五医院（二门诊） | HRB-WY-EM | 3 | 特殊收费 Excel | excel17-align 驱血带 W 三档 | ✅ PASS | **已证实** |
| 7 | 哈尔滨市胸科医院 | HRB-XK-YY | 2 | 特殊收费 Excel | `phase-special-charge-2-sync-20260902.json` | ✅ PASS | **已证实** |
| 9 | 黑龙江九洲妇科医院 | JIUZHOU-FK | 1 | 特殊收费 Excel | excel17-align | ✅ PASS | **已证实**（方盘 5.5） |
| 10 | 哈尔滨基准生物科技有限公司 | JZSW-BIO | 1 | 特殊收费 Excel | excel17-align | ✅ PASS | **已证实**（氩氦刀 150） |
| 11 | 黑龙江省社会康复医院 | SHKF-YY | 14 | 特殊收费 Excel 指定盒名 | `phase-special-charge-17-sync-20260830` + `phase-shkf-oral-box-pricing-20260730` | ✅ PASS | **已证实**（14 条 exact_token 盒名 16.5/22/44） |
| 12 | 祖研-黑龙江省中医医院（三辅院区） | ZUYAN-SF | 2 | Excel 美容科排针 | charge-17-sync | ✅ PASS | **已证实**（2026-09-17 删针线包、export 探针刨刀） |

### ZUYAN-SF 四条拆分

| 规则名 | 价格 | 来源 | G4 | 分级 | 说明 |
|--------|------|------|-----|------|------|
| 祖研三辅美容科排针≤20固定16.5 | 16.5 | Excel + charge-17-sync | ✅ PASS | **A** | G4 明确覆盖 |
| 针线包现价 | 2.5 | 运营文档 / `医院特色计价规则清单.md` | G4 未单列 | **B** | `any_price` 命中 2.5 或 25；需客户确认 |
| 祖研三辅export 探针刨刀16.5 | 16.5 | `phase-bill-wave4c-close-20260729` | G4 未单列（export 规则） | **B** | 仅 `exportApply=true` 生效；非 Excel 常规定价 |
| PDF/期待价30 | 30.0 | `phase-s3-pdf-align-20260722` | ❌ 明确为 Excel 外 | **D** | 与废止 P0 `校正价30.0`（纱布条）**同价同词**；仅改名 |

---

## 四、代表医院深读（3–5 家）

### 4.1 HL-ZGH（14 条）—— Excel 补库典范

- **背景**：2026-09-02 前 Excel 总工会段未落库，生产手工规则 `skipPackaging=false` 导致固定价 +8 元包材（清宫包 35→43）。
- **处置**：`phase-special-charge-zgh-fixed-price-20260902.json` 按 Excel ①-⑩ 补齐 14 条，全部 `skipPackaging+skipDiscount`。
- **G4**：PASS；报告注明 14 条来自「用户侧更新版 Excel，用户确认来源」。
- **与非法校正价**：无 P0 `校正价*` 重叠；关键词为人流包/清宫包等业务词。

### 4.2 SHKF-YY（14 条）—— Excel 指定完整包名

- G4：**14 条指定盒名固定价逐条在库**，exact_token 与 Excel 名称（含空格变体）一致。
- 种子：`phase-special-charge-17-sync`。
- P0 仅有 1 条 `校正价2.5`（与现行 14 条 **无关**），已废止。

### 4.3 GUOYAO-2（23 条）—— 电机厂 W 码体系

- G4 PASS：棉球/纱布 W 三档 25/30/35 全覆盖。
- 多数规则 2026-08-31 excel17-align 批次细化（按 W 码拆条）。
- P0 曾有 `校正价8.0`（电机厂），已删；现行 23 条来自 Excel 电机厂段，**非** P0 残留。

### 4.4 GUOYAO-MAIN（2 条）—— 未进 G4-29

- 来源：`phase5-batch-c.json`（2026-07-17 汽轮机/国药主院区接入）。
- `10mm30度镜固定价` 28 元、`驱血带固定价` 13 元——见 `一期优先医院特色导出规则清单.md`。
- P0 `校正价35.0`（辅料包）已删，**不等于**现行两条。
- **缺口**：不在 2026-09-02 G4 29 家清单；建议补跑 G4 或取得客户书面确认。

### 4.5 ZUYAN-SF · `PDF/期待价30` —— 唯一 D 类

- P0 种子（废止）：`校正价30.0`，关键词 `纱布条`，价格 30.0。
- 现行：`PDF/期待价30`，同关键词同价；种子 `phase-s3-pdf-align-20260722`。
- G4 报告口径：「PDF 价表、P0 校正价不列差异」= **承认其在库但非 Excel 权威**。
- **建议**：按 2026-09-16 电力校正价治理口径，**删除或要求客户补录 Excel**。

---

## 五、75 条规则分级汇总

> **逐条 Excel 行号、原文摘录**：见 [`FIXED_PRICE与特殊收费Excel逐条配对表-20260917.md`](FIXED_PRICE与特殊收费Excel逐条配对表-20260917.md)（可复跑 `scripts/fixed_price_excel_pairing_audit.py`）。

| 分级 | 含义 | 条数 | 可称「合法」？ |
|------|------|------|----------------|
| **A1** | 仓库 `特殊收费(1)`/`(4)` 逐条 EXACT 配对 | **56** | ✅ 用户可直接在 Excel 核对 |
| **A2** | 用户侧另一版 Excel（总工会 ①-⑩，仓库副本未同步） | **14** | ✅ 有专报，但须同步进 `docs/source/` |
| **B** | 仓库 Excel **无对应行**（种子/文档/export/疑误建模） | **5** | ⚠️ 待审阅或删除 |
| **D** | P0/PDF 期待价同族 | **0** | ❌（`PDF/期待价30` 已删） |

### A1 类（56 条）—— 在仓库 Excel 可直接看到

博尚 3、电机厂 22、市五院 8、二门诊 3、胸科 2、社会康复 14、九洲 1、基准生物 1、祖研三辅美容科排针 1、**国药主院 10mm30度镜 1**（row136）。

### A2 类（14 条）—— 总工会，仅用户侧 Excel

HL-ZGH 人流包/清宫包/棉球纱布 W 码等；仓库 `(1)`/`(4)` 总工会段仅有 5 行镜头「标准价+8」，无固定价段。来源：`测试用例/billing-seed-zgh-fixed-price-20260902.md`。**逐条审阅**：[`总工会14条FIXED_PRICE审阅说明-20260917.md`](总工会14条FIXED_PRICE审阅说明-20260917.md)。

### B 类（5 条）—— 仓库 Excel 无行

| 医院 | 规则名 | 依据 | 建议 |
|------|--------|------|------|
| GUOYAO-2 | 电机厂高温纸塑袋（3.5） | 院级 G4 PASS，但 Excel 无固定价行 | 核对是否误建模，或删 |
| GUOYAO-MAIN | 国药主院驱血带固定价 | phase5-batch-c | 补 Excel 或删 |
| ZUYAN-SF | 针线包现价 | 运营文档 | 补 Excel 或删 |
| ZUYAN-SF | 祖研三辅export 探针刨刀16.5 | export 种子 | 确认是否保留 |

---

## 六、与已删除非法校正价的关系（总述）

| 问题 | 答案 |
|------|------|
| 75 条是否 = 85 条删除校正价的改名残留？ | **否**。名称零重叠；D 类 `PDF/期待价30` 已删 |
| 为何 HRB-WY / ZUYAN-SF / GUOYAO 在 P0 名单却仍有 FIXED_PRICE？ | P0 写入的是 **`校正价*`**；现行规则来自 **Excel 不同条目**或 **后期 Excel 同步**；P0 已删，Excel 规则保留 |
| FIXED_PRICE 与「客户校正价」UI 徽章 | 前端凡 `pricingPath=fixed` 可显示「客户校正价」徽章，**与规则合法性无关**（见呼兰分析报告） |
| 能否因 `skipPackaging+skipDiscount` 合规即称合法？ | **不能**。工程惯例 ≠ 业务授权 |

---

## 七、诚实结论与审阅建议

### 7.1 可以怎么表述

- ✅ 「仓库 `特殊收费(1)` 中可逐条核对 **56 条** FIXED_PRICE；总工会 **14 条**在用户侧另一版 Excel。」
- ✅ 「工程验收口径下 **70 条**有外部 Excel 文字（含总工会专报）；**5 条**无仓库 Excel 行。」
- ✅ 「12 家医院 FIXED_PRICE **均非** `校正价*` 命名；`CorrectionPriceRedundancyAuditTest` 通过。」

### 7.2 不应怎么表述

- ❌ 「用户打开特殊收费 Excel 能找到 70 条 FIXED_PRICE。」（仓库副本仅 **56** 条）
- ❌ 「75 条 FIXED_PRICE 全部合法。」
- ❌ 「G4 PASS = 每条规则都能在 Excel 里找到对应行。」（院级 PASS、总工会用户侧 Excel、疑误建模均可能）

### 7.3 建议动作（按优先级）

| 优先级 | 动作 | 对象 |
|--------|------|------|
| P0 | ~~删除~~ **已删除** `ZUYAN-SF`·`PDF/期待价30` | D 类 |
| P0.5 | **同步总工会固定价段**进 `docs/source/特殊收费(1).xlsx`（G0） | A2 类 14 条 |
| P1 | 审阅 **5 条 B 类**（含 `GUOYAO-2` 高温纸塑袋 3.5）补 Excel 或删除 | 见第五节 B 表 |
| P2 | `GUOYAO-MAIN` 驱血带补 G4 或书面确认 | 仍在 B 类 |
| P3 | 更新 `校正价规则审阅清单.docx` | 文档 |

---

## 附录 A：扫描命令

```bash
# 统计 baseline FIXED_PRICE
python3 -c "
import json; from pathlib import Path
n=0; h=set()
for f in Path('backend/src/main/resources/billing-rules/baseline').glob('*.json'):
    if f.name=='index.json': continue
    d=json.loads(f.read_text())
    c=sum(1 for r in d.get('productRules',[]) if r.get('ruleType')=='FIXED_PRICE' and r.get('isActive',True))
    if c: h.add(f.stem); n+=c
print(f'{n} rules, {len(h)} hospitals')
"

# G4 报告路径
# 测试用例/excel-manifest-parity-audit-20260902.md
```

## 附录 B：关键种子索引

| 医院 | 种子文件 |
|------|----------|
| HL-ZGH | `archive/legacy-2026/phase-special-charge-zgh-fixed-price-20260902.json` |
| SHKF-YY | `archive/legacy-2026/phase-special-charge-17-sync-20260830.json` |
| GUOYAO-2 | `archive/legacy-2026/phase-special-v8-rules-20260814.json` |
| GUOYAO-MAIN | `phase5-batch-c.json` |
| ZUYAN-SF PDF | `archive/legacy-2026/phase-s3-pdf-align-20260722.json` |
| P0（废止） | `archive/legacy-2026-obsolete-correction-price/phase-batch-p0.json` |

---

**一句话结论**：75 条中仓库 Excel 可直接核对 **56 条**；总工会 **14 条**在用户侧另一版 Excel；**5 条**无 Excel 行待审阅。不得说「打开特殊收费 Excel 就有 70 条固定价」——详见 [`FIXED_PRICE与特殊收费Excel逐条配对表-20260917.md`](FIXED_PRICE与特殊收费Excel逐条配对表-20260917.md)。
