# baseline FIXED_PRICE 来源与合法性说明

| 项 | 内容 |
|----|------|
| **编制日期** | 2026-09-16 |
| **核查对象** | 当前 baseline 中 **76 条** `FIXED_PRICE` 规则，分布于 **12 家**医院（32 家 baseline 医院中的子集） |
| **关联文档** | `docs/Fixed校正价规则全库清单-按医院分类-20260916.md`（附录明细）、`docs/计费规则迁移与验收规范.md`、`docs/电力校正价来源与合法性核查报告-20260916.md` |
| **核查方法** | baseline 全量扫描；归档种子/ manifest 溯源；G4 Excel↔manifest 对账报告交叉引用；P0 废止种子对照；Git 种子文件首提交时间 |

---

## 摘要结论（Executive Summary）

1. **76 条 FIXED_PRICE 与已删除的 85 条「校正价」是不同集合**：当前规则面 **零**规则名含「校正价」；76 条绝大多数来自《特殊收费》Excel 逐条建模或后续 Excel 同步种子，**不是** P0「期待价格校正」反推体系的直接残留。
2. **权威来源口径**（项目规范）：`docs/计费规则迁移与验收规范.md` 规定 G4 Excel↔manifest 逐条 PASS 为「有 Excel 依据」的正式验收标准；客户书面确认、整月账单对账 PASS 可作为补充依据，**不能**仅凭 baseline 存在或种子归档即称「合法」。
3. **分级统计**（见第五节）：
   - **A 已 Excel/G4 证实**：70 条（92%）
   - **B 有种子或客户文档、未纳入 G4-29 或待书面确认**：5 条（7%）
   - **C 来源不明**：0 条
   - **D 疑似 P0/PDF 期待价反推（与废止校正价同族）**：1 条（1%）
4. **诚实裁决**：**不能**宣称「76 条全部合法」——至少 **1 条**（`ZUYAN-SF` · `PDF/期待价30`）与已废止 P0 `校正价30.0` 同签名，应待客户审阅或删除；**3 条**（`GUOYAO-MAIN`×2、`JIAYI-YL`×1）未进 G4-29 全量对账，仅有 Phase5/四诊所接入种子与客户侧文档；**2 条**（`ZUYAN-SF` 针线包/export 规则）有运营文档但 G4 未逐条覆盖。

---

## 一、权威来源定义（项目规范）

依据 `docs/计费规则迁移与验收规范.md`：

| 层级 | 来源类型 | 是否算「权威」 | 说明 |
|------|----------|----------------|------|
| **G0** | `docs/source/特殊收费(1).xlsx`（及用户确认的同版副本） | ✅ 最高 | 迁移前须确认 Excel 版本 |
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
| 2 | 国药总医院第二院区（电机厂） | GUOYAO-2 | 23 | 特殊收费 Excel 电机厂段 | `phase-special-v8-rules-20260814` + `phase-keyword-match-mode-excel17-align-20260831`；棉球/纱布 W 三档与总工会同形态 | ✅ PASS | **已证实**（含高温纸塑袋 3.5、敷料 W 码 25/30/35） |
| 3 | 国药总医院主院区 | GUOYAO-MAIN | 2 | Phase5 客户接入 | `phase5-batch-c.json`（`b23e3c20`，2026-07-17）；`一期优先医院特色导出规则清单.md` 记载 28/13 元 | ❌ **未纳入 G4-29** | **待审阅**（有种子+清单，无 G4 逐条 PASS；P0 `校正价35.0` 已删且与现行规则无关） |
| 4 | 黑龙江总工会医院 | HL-ZGH | 14 | 特殊收费 Excel ①-⑩ | `phase-special-charge-zgh-fixed-price-20260902.json`；专报 `测试用例/billing-seed-zgh-fixed-price-20260902.md` | ✅ PASS | **已证实**（用户侧更新版 Excel + 20260902 补库报告） |
| 5 | 哈尔滨市第五医院 | HRB-WY | 8 | 特殊收费 Excel 市五院段 | `phase-special-v8-rules-20260814` + excel17-align | ✅ PASS | **已证实**（止血带/布袋/手套等 8 条；**注意**：P0 曾另有 6 条 `校正价*` 已删，与现行 8 条 **关键词不同**） |
| 6 | 哈尔滨市第五医院（二门诊） | HRB-WY-EM | 3 | 特殊收费 Excel | excel17-align 驱血带 W 三档 | ✅ PASS | **已证实** |
| 7 | 哈尔滨市胸科医院 | HRB-XK-YY | 2 | 特殊收费 Excel | `phase-special-charge-2-sync-20260902.json` | ✅ PASS | **已证实** |
| 8 | 佳医医疗 | JIAYI-YL | 1 | 四诊所接入 | `phase-4clinics-special-rules-20260809.json`；`客户收费规则与系统规则对比分析-20260819.md` 记「已确认」 | ❌ **未纳入 G4-29** | **待审阅**（客户表确认孔巾/眼包敷料 4 元，缺 G4/整月严格对账） |
| 9 | 黑龙江九洲妇科医院 | JIUZHOU-FK | 1 | 特殊收费 Excel | excel17-align | ✅ PASS | **已证实**（方盘 5.5） |
| 10 | 哈尔滨基准生物科技有限公司 | JZSW-BIO | 1 | 特殊收费 Excel | excel17-align | ✅ PASS | **已证实**（氩氦刀 150） |
| 11 | 黑龙江省社会康复医院 | SHKF-YY | 14 | 特殊收费 Excel 指定盒名 | `phase-special-charge-17-sync-20260830` + `phase-shkf-oral-box-pricing-20260730` | ✅ PASS | **已证实**（14 条 exact_token 盒名 16.5/22/44） |
| 12 | 祖研-黑龙江省中医医院（三辅院区） | ZUYAN-SF | 4 | **混合** | 见下表 | ⚠️ **部分** | **1 条无依据 / 2 条待审阅 / 1 条已证实** |

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

## 五、76 条规则分级汇总

| 分级 | 含义 | 条数 | 占比 | 可称「合法」？ |
|------|------|------|------|----------------|
| **A** | G4 Excel↔manifest PASS 或等价 Excel 同步种子 + 专报 | **70** | 92% | ✅ 可称「有 Excel 权威依据」 |
| **B** | 有归档种子 + 客户/运营文档，**未** G4-29 或 G4 未逐条 | **5** | 7% | ⚠️ 仅可称「待客户/G4 确认」，不可称已合法 |
| **C** | 来源不明 | **0** | 0% | ❌ |
| **D** | P0/PDF 期待价反推同族（与废止校正价同签名） | **1** | 1% | ❌ 应删除或补 Excel |

### A 类（70 条）—— 院别条数

| 医院 | 条数 |
|------|------|
| GUOYAO-2 | 23 |
| HL-ZGH | 14 |
| SHKF-YY | 14 |
| HRB-WY | 8 |
| BOSHANG-YY | 3 |
| HRB-WY-EM | 3 |
| ZUYAN-SF（仅美容科排针≤20） | 1 |
| HRB-XK-YY | 2 |
| JIUZHOU-FK | 1 |
| JZSW-BIO | 1 |

### B 类（5 条）

| 医院 | 规则名 | 依据 | 缺口 |
|------|--------|------|------|
| GUOYAO-MAIN | 国药主院10mm30度镜固定价 | phase5-batch-c + 一期优先清单 | 无 G4 |
| GUOYAO-MAIN | 国药主院驱血带固定价 | 同上 | 无 G4 |
| JIAYI-YL | 佳医敷料纸塑4元 | phase-4clinics + 客户对比分析「已确认」 | 无 G4、缺整月材料 |
| ZUYAN-SF | 针线包现价 | 医院特色计价规则清单 | G4 未单列 |
| ZUYAN-SF | 祖研三辅export 探针刨刀16.5 | export 种子 | 非 Excel 常规定价 |

### D 类（1 条）

| 医院 | 规则名 | P0 同源条目 | 建议 |
|------|--------|-------------|------|
| ZUYAN-SF | PDF/期待价30 | `校正价30.0`（纱布条→30） | 删除或补录 Excel + 客户勾选 |

---

## 六、与已删除非法校正价的关系（总述）

| 问题 | 答案 |
|------|------|
| 76 条是否 = 85 条删除校正价的改名残留？ | **否**。名称零重叠；仅 **1 条**（PDF/期待价30）与 P0 **同签名** |
| 为何 HRB-WY / ZUYAN-SF / GUOYAO 在 P0 名单却仍有 FIXED_PRICE？ | P0 写入的是 **`校正价*`**；现行规则来自 **Excel 不同条目**或 **后期 Excel 同步**；P0 已删，Excel 规则保留 |
| FIXED_PRICE 与「客户校正价」UI 徽章 | 前端凡 `pricingPath=fixed` 可显示「客户校正价」徽章，**与规则合法性无关**（见呼兰分析报告） |
| 能否因 `skipPackaging+skipDiscount` 合规即称合法？ | **不能**。工程惯例 ≠ 业务授权 |

---

## 七、诚实结论与审阅建议

### 7.1 可以怎么表述

- ✅ 「当前 baseline **70 条** FIXED_PRICE 已通过 2026-09-02 G4 Excel↔manifest 对账或等价 Excel 补库报告证实。」
- ✅ 「12 家医院 FIXED_PRICE **均非** `校正价*` 命名；`CorrectionPriceRedundancyAuditTest` 通过。」
- ✅ 「总工会、社会康复、电机厂等固定价来自《特殊收费》Excel 逐条建模，有专报可追溯。」

### 7.2 不应怎么表述

- ❌ 「76 条 FIXED_PRICE 全部合法。」
- ❌ 「FIXED_PRICE 与校正价无关。」（UI 徽章、PDF/期待价渠道仍可能同族）
- ❌ 「baseline 有记录 = 客户已确认。」

### 7.3 建议动作（按优先级）

| 优先级 | 动作 | 对象 |
|--------|------|------|
| P0 | **删除或冻结** `ZUYAN-SF` · `PDF/期待价30` | 1 条 D 类 |
| P1 | 对 `GUOYAO-MAIN`（2）、`JIAYI-YL`（1）补跑 **G4** 或取得客户书面确认 | 3 条 B 类 |
| P2 | 确认 `ZUYAN-SF` 针线包/export 规则是否保留或并入 Excel | 2 条 B 类 |
| P3 | 更新 `校正价规则审阅清单.docx`：勾选 baseline 已收录的 FIXED_PRICE（非校正价命名） | 文档 |

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
| JIAYI-YL | `archive/legacy-2026/phase-4clinics-special-rules-20260809.json` |
| ZUYAN-SF PDF | `archive/legacy-2026/phase-s3-pdf-align-20260722.json` |
| P0（废止） | `archive/legacy-2026-obsolete-correction-price/phase-batch-p0.json` |

---

**一句话结论**：76 条 baseline FIXED_PRICE **绝大多数（70 条）有 G4/Excel 权威依据**；**5 条待审阅**、**1 条（PDF/期待价30）应视同已废止校正价同族处理**；不得笼统宣称全部合法。
