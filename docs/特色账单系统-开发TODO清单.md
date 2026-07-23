# 特色账单系统 — 开发 TODO 清单

## 文档信息

| 项目 | 内容 |
|------|------|
| **文档名称** | 特色账单系统 — 开发 TODO 清单 |
| **版本** | v1.0 |
| **编制日期** | 2026-07-17 |
| **编制依据** | [`特色账单系统-升级规划方案.md`](./特色账单系统-升级规划方案.md)（Phase 0–9） |
| **关联文档** | [`7.17特色账单系统功能需求说明书.md`](./7.17特色账单系统功能需求说明书.md)（FRD）、[`特色账单系统-医院功能需求通用登记表（模板）.md`](./特色账单系统-医院功能需求通用登记表（模板）.md)、[`逐院需求登记表/`](./逐院需求登记表/)、[`system-docs/特色账单升级开发规划.md`](../system-docs/特色账单升级开发规划.md) |
| **适用范围** | 铂康医疗 42 家医院特色账单升级（Phase 0–9） |
| **状态说明** | `[ ]` 未开始 · `[x]` 已完成 · `[~]` 进行中 |

---

## 字段说明

| 列 | 含义 |
|----|------|
| **任务 ID** | `P{Phase}-{序号}`，技术债为 `TD-{序号}` |
| **优先级** | P0 阻塞上线 · P1 重要 · P2 增强 · P3 远期 |
| **可并行** | ✅ 可与其他任务并行 · ❌ 有强依赖需串行 · ⚠️ 部分并行（需协调接口） |
| **模块** | M1–M14 / INT / CFG / NFR |
| **FRD** | 对应功能需求编号 |
| **逐院文档** | 验收或配置参考的逐院登记表链接 |

---

## 进度总览

| Phase | 主题 | 预估人周 | 任务数 | 完成数 | 里程碑 |
|:-----:|------|:--------:|:------:|:------:|--------|
| 0 | 基线修复与回归 | 0.5 | 6 | 6 | CI 绿 + 黄金样例 |
| 1 | P0 计价核心闭环 | 0.5–1 | 12 | 12 | L1 部分 |
| 2 | P0/P1 策略层 | 0.5–1 | 10 | 10 | L1 大部分 |
| 3 | P0 导出引擎 v2 | 4–5 | 18 | 18 | **L1 完整** |
| 4 | P1 高级计价/导出折扣 | 3 | 14 | 14 | L2 |
| 5 | P1 物流增强/物流卡 | 3 | 15 | 15 | L2+ |
| 6 | P2 加急/设备抵扣 | 2 | 8 | 8 | L2（新发） |
| 7 | P2 科室借调/花名册/外来器械 | 6–8 | 22 | 22 | **L3** |
| 8 | P2/P3 配置中心/规则组 | 4 | 14 | 14 | 运维效率 |
| 9 | P3 审计报表/日结 | 3 | 10 | 9 | **L4** |
| TD | 技术债偿还 | 贯穿 | 5 | 5 | 持续 |
| **合计** | | **~29** | **134** | **107** | |

> **客户对齐里程碑与 AI 辅助工期：** 详见 [`特色账单系统-开发规划报告.md`](./特色账单系统-开发规划报告.md)（20 工作日 / 3 人 / ~12.5 人周）

---

## 技术债（TD-01 ~ TD-05）

> 技术债任务贯穿各 Phase，建议在对应 Phase 内同步关闭；未关闭不得标记 Phase 完成。

| 状态 | 任务 ID | 描述 | 涉及文件 | 优先级 | 可并行 | 模块 | FRD | 目标 Phase |
|:----:|---------|------|----------|:------:|:------:|------|-----|:----------:|
| [x] | **TD-01** | 修复 `saveProductRules` 批量保存对无 `productId` 的 FOLD/EXTRA_FEE **silent skip** | `CustomerServiceImpl.java`、`CustomerController.java` | P0 | ❌ | CFG | G6 | **0** |
| [x] | **TD-02** | 拆分 `PricingEngine`（~1500 行）：抽出 `BillingConditionEvaluator`、`BillingPolicyApplier` | `PricingEngine.java`、新建 `BillingConditionEvaluator.java`、`BillingPolicyApplier.java` | P1 | ⚠️ | M3 | — | **4** |
| [x] | **TD-03** | 贯通 `product_variant` / `variantId` 至编译器与引擎 | `PricingRuleCompiler.java`、`PricingEngine.java`、`CustomerProductRuleForm.vue` | P2 | ✅ | M3 | — | **8** |
| [x] | **TD-04** | 从 `HospitalReconciliationServiceImpl` 拆出导出逻辑至 `ExportEngineService` | `HospitalReconciliationServiceImpl.java`、新建 `ExportEngineService.java` | P1 | ❌ | M8、INT | FR-M8-* | **3** |
| [x] | **TD-05** | 规则配置 UI 拆分：策略面板、试算器、批量导入工具 | `RuleBatchImport.vue`、`CustomerRuleToolsPanel.vue`、`RuleSimulator.vue` | P1 | ⚠️ | CFG | CFG-04 | **2/8** |

---

## Phase 0：基线修复与回归（0.5 人周）

**目标：** 消除已知 silent bug，建立黄金样例行 CI  
**依赖：** 无  
**Phase 门禁：** CI 全绿；批量保存 FOLD 规则不丢失

| 状态 | 任务 ID | 描述 | 涉及文件 | 优先级 | 可并行 | 模块 | FRD | 逐院文档 |
|:----:|---------|------|----------|:------:|:------:|------|-----|----------|
| [x] | **P0-01** | 修复 TD-01：`saveProductRules` 无 productId 规则批量保存丢失 | `CustomerServiceImpl.java` | P0 | ❌ | CFG | G6 | — |
| [x] | **P0-02** | 补充单测：FOLD/EXTRA_FEE 无 productId 批量保存与单条 API 行为一致 | `CustomerServiceImplTest.java`（新建或扩充） | P0 | ⚠️ | CFG | G6 | — |
| [x] | **P0-03** | 创建 `hospital-billing-golden-rows.json`（≥20 院 × ≥5 行/院骨架） | `backend/src/test/resources/hospital-billing-golden-rows.json` | P0 | ✅ | NFR | — | [`逐院需求登记表/`](./逐院需求登记表/) |
| [x] | **P0-04** | 扩充 `PricingEngineTest`：excludeKeywords、多报价、折扣链 | `PricingEngineTest.java` | P0 | ✅ | M3、M4 | FR-M3-02、FR-M4-01 | [省二南岗](./逐院需求登记表/黑龙江省第二医院（南岗院区）.md) |
| [x] | **P0-05** | 扩充 `PricingRuleCompilerIntegrationTest`：policy 编译 + 引擎端到端 | `PricingRuleCompilerIntegrationTest.java` | P0 | ✅ | M2、M3 | FR-M2-02 | — |
| [x] | **P0-06** | CI 集成黄金样例测试步骤（GitHub Actions） | `.github/workflows/deploy.yml` | P0 | ⚠️ | NFR | — | — |

---

## Phase 1：P0 计价核心闭环（0.5–1 人周， largely 已实现，验收为主）

**目标：** G1 特色开关 + G4 多报价 + 排除关键词 + 对账追溯  
**依赖：** Phase 0  
**验收医院：** [省二南岗](./逐院需求登记表/黑龙江省第二医院（南岗院区）.md)、[省二松北](./逐院需求登记表/黑龙江省第二医院（松北院区）.md)、[红十字妇产](./逐院需求登记表/哈尔滨市红十字妇产医院.md)、[九州妇科](./逐院需求登记表/黑龙江九洲妇科医院.md)

| 状态 | 任务 ID | 描述 | 涉及文件 | 优先级 | 可并行 | 模块 | FRD | 逐院文档 |
|:----:|---------|------|----------|:------:|:------:|------|-----|----------|
| [x] | **P1-01** | 验收 `billingEnabled` 开关：关闭后不 merge 特色规则 | `Customer.java`、`PricingRuleCompiler.java` | P0 | ✅ | M1 | FR-M1-01 | 全部 42 院 |
| [x] | **P1-02** | 验收 `billingPricingMode`（standard/special_only/hybrid） | `Customer.java`、`PricingEngine.java` | P0 | ✅ | M1 | FR-M1-04 | — |
| [x] | **P1-03** | 验收 `pathOverride` 路径覆盖（如无低温、强制高温单价） | `Customer.java`、`PricingEngine.java` | P0 | ✅ | M1 | FR-M1-05 | [道外人民](./逐院需求登记表/道外区人民医院.md) |
| [x] | **P1-04** | 验收 `excludeKeywords` 排除关键词匹配 | `PricingEngine.java`、`CustomerProductRuleForm.vue` | P0 | ✅ | M3 | FR-M3-02 | [省二南岗](./逐院需求登记表/黑龙江省第二医院（南岗院区）.md) |
| [x] | **P1-05** | 验收 `acceptedPrices` + `matchMode=any_price` 多报价逻辑 | `PricingEngine.java`、`CustomerProductRule.java` | P0 | ✅ | M4 | FR-M4-01 | [省二南岗](./逐院需求登记表/黑龙江省第二医院（南岗院区）.md) |
| [x] | **P1-06** | 验收 `matched_rule_id` / `matched_price_option` 写入 reconciliation_row | `HospitalReconciliationServiceImpl.java`、DB migration | P0 | ⚠️ | M4、NFR | NFR-01 | — |
| [x] | **P1-07** | 对账 UI：三路视图（全部/差异/多报价） | `reconciliation/index.vue` | P0 | ✅ | M4、INT | FR-M4-02 | — |
| [x] | **P1-08** | 对账 UI：行展开面板展示 `matchedPriceOption`、规则名 | `reconciliation/index.vue` | P0 | ✅ | M4、CFG | NFR-01 | — |
| [x] | **P1-09** | 规则表单：多报价数组编辑、`matchMode` 选择 | `CustomerProductRuleForm.vue` | P0 | ✅ | M4、CFG | FR-M4-01 | — |
| [x] | **P1-10** | 规则表单：excludeKeywords 输入与校验 | `CustomerProductRuleForm.vue` | P0 | ✅ | M3、CFG | FR-M3-02 | — |
| [x] | **P1-11** | 省二院规则种子数据录入（南岗/松北独立客户） | 系统配置 + JSON 备份 | P0 | ✅ | M1、M3 | — | [省二南岗](./逐院需求登记表/黑龙江省第二医院（南岗院区）.md)、[省二松北](./逐院需求登记表/黑龙江省第二医院（松北院区）.md) |
| [x] | **P1-12** | Phase 1 UAT：省二院/红十字/九州 MAT-01→MAT-02 ≥99% 行一致（[`UAT stub`](./uat/Phase1-UAT-checklist.md) 已建，待业务执行） | UAT 记录 | P0 | ❌ | INT | — | Batch-A 医院 |

---

## Phase 2：P0/P1 策略层（0.5–1 人周， largely 已实现，验收为主）

**目标：** 分温折扣、温度条件、客户级物流单价、低消/封顶  
**依赖：** Phase 1  
**验收医院：** [维多利亚](./逐院需求登记表/黑龙江维多利亚妇产医院.md)、[呼兰红十字](./逐院需求登记表/呼兰区红十字医院.md)、[悦美芳华](./逐院需求登记表/悦美芳华医疗门诊医院.md)、[省二南岗](./逐院需求登记表/黑龙江省第二医院（南岗院区）.md)

| 状态 | 任务 ID | 描述 | 涉及文件 | 优先级 | 可并行 | 模块 | FRD | 逐院文档 |
|:----:|---------|------|----------|:------:|:------:|------|-----|----------|
| [x] | **P2-01** | 验收 `CustomerBillingPolicy` DISCOUNT + `scope.temperature` 分温折扣 | `PricingRuleCompiler.java`、`CustomerBillingPolicy.java` | P0 | ✅ | M2 | FR-M2-02 | [维多利亚](./逐院需求登记表/黑龙江维多利亚妇产医院.md) |
| [x] | **P2-02** | 验收 LOGISTICS policy：`feePerTrip` 客户级物流单价 | `LogisticsFeeCalculator.java`、`PricingRuleCompiler.java` | P0 | ✅ | M6 | FR-M6-01 | [省二南岗](./逐院需求登记表/黑龙江省第二医院（南岗院区）.md) |
| [x] | **P2-03** | 验收 MONTHLY_SETTLEMENT policy：低消/封顶 | `MonthlySettlementCalculator.java` | P0 | ✅ | M5 | FR-M5-01 | [维多利亚](./逐院需求登记表/黑龙江维多利亚妇产医院.md) |
| [x] | **P2-04** | 验收规则温度条件（HT/LT/ANY） | `PricingEngine.java`、`CustomerProductRuleForm.vue` | P0 | ✅ | M3 | FR-M3-03 | [红十字妇产](./逐院需求登记表/哈尔滨市红十字妇产医院.md) |
| [x] | **P2-05** | 新建 `CustomerBillingPolicyPanel.vue` 策略分 Tab 编辑（折扣/物流/月度） | `components/business/customers/CustomerBillingPolicyPanel.vue` | P1 | ✅ | CFG | — | — |
| [x] | **P2-06** | 客户管理页集成策略面板（部分偿还 TD-05） | `customers/index.vue` | P1 | ⚠️ | CFG | — | — |
| [x] | **P2-07** | 对账 Job 顶栏展示 monthlyBreakdown 摘要 | `reconciliation/index.vue` | P1 | ✅ | M5、INT | FR-M5-02 | — |
| [x] | **P2-08** | 写入 `job.monthly_breakdown` JSON（若 migration 缺失则补） | `SchemaMigrationRunner.java`、`MonthlySettlementCalculator.java` | P1 | ✅ | M5 | FR-M5-01 | — |
| [x] | **P2-09** | 维多利亚/呼兰红十字/悦美策略种子数据录入 | `billing-seeds/phase2-policies.json`、`BillingSeedMigrationRunner` | P1 | ✅ | M2、M5、M6 | — | [维多利亚](./逐院需求登记表/黑龙江维多利亚妇产医院.md)、[呼兰红十字](./逐院需求登记表/呼兰区红十字医院.md) |
| [x] | **P2-10** | Phase 2 UAT：低消/物流/分温折扣 MAT-02 验收 | [`uat/Phase2-UAT-checklist.md`](./uat/Phase2-UAT-checklist.md) | P0 | ❌ | INT | — | Phase 2 验收医院 |

---

## Phase 3：P0 导出引擎 v2（4–5 人周）— **核心缺口**

**目标：** 可配置导出模板 + 列变换 + L1/L2 医院「已改账单」「结款函」验收  
**依赖：** Phase 1–2  
**Phase 门禁：** Batch-A 至少 3 家 L1 医院 MAT-02/MAT-03 验收通过  
**验收医院：** [省二南岗](./逐院需求登记表/黑龙江省第二医院（南岗院区）.md)、[呼兰一院](./逐院需求登记表/哈尔滨市呼兰区第一人民医院.md)、[冰城医美](./逐院需求登记表/哈尔滨冰城医疗美容医院.md)、[道外人民](./逐院需求登记表/道外区人民医院.md)

| 状态 | 任务 ID | 描述 | 涉及文件 | 优先级 | 可并行 | 模块 | FRD | 逐院文档 |
|:----:|---------|------|----------|:------:|:------:|------|-----|----------|
| [x] | **P3-01** | 新建 `export_template` 表 + migration | `db/schema_00X_export_template.sql`、`SchemaMigrationRunner.java` | P0 | ✅ | M8 | FR-M8-01 | — |
| [x] | **P3-02** | 实现 TD-04：`ExportEngineService` 从对账服务拆出 | `ExportEngineService.java`、`HospitalReconciliationServiceImpl.java` | P0 | ❌ | M8、INT | FR-M8-* | — |
| [x] | **P3-03** | 实现 `ExportTemplateResolver`：全局默认 → 客户覆盖 | `ExportEngineService.java` | P0 | ⚠️ | M8 | FR-M8-01 | — |
| [x] | **P3-04** | 实现 `ColumnTransformPipeline`（删列/插列/保留例外） | `ColumnTransformPipeline.java` | P0 | ✅ | M8 | FR-M3-21 | [道外人民](./逐院需求登记表/道外区人民医院.md) |
| [x] | **P3-05** | 实现 `SettlementTemplateFiller`：结款函独立折扣行、低消行 | `SettlementTemplateFiller.java` | P0 | ⚠️ | M8、M2 | FR-M2-03 | [工程大学](./逐院需求登记表/哈尔滨工业大学医院.md)、[九院](./逐院需求登记表/哈尔滨市南岗区人民医院（九院）.md) |
| [x] | **P3-06** | 实现账单导出（bill）模板绑定与 POI 填充 | `ExportEngineService.java` | P0 | ⚠️ | M8 | FR-M8-02 | [省二南岗](./逐院需求登记表/黑龙江省第二医院（南岗院区）.md) |
| [x] | **P3-07** | 实现结款函导出（settlement）Excel/HTML | `ExportEngineService.java` | P0 | ⚠️ | M8 | FR-M8-03 | Batch-A 医院 |
| [x] | **P3-08** | 实现 `ExportTemplateController` CRUD API | `ExportTemplateController.java` | P0 | ✅ | M8、CFG | — | — |
| [x] | **P3-09** | 前端：导出模板管理页（上传 + 列映射可视化） | `views/billing-config/export-templates/index.vue` | P0 | ✅ | CFG | — | — |
| [x] | **P3-10** | 客户编辑页「导出模板」绑定区（账单/结款函/汇总） | `customers/index.vue` | P0 | ✅ | CFG、M1 | — | — |
| [x] | **P3-11** | 对账页导出向导：Step1 类型 → Step2 Sheet 预览 → Step3 下载 | `reconciliation/index.vue` | P0 | ⚠️ | INT | INT-15 | — |
| [x] | **P3-12** | 导出前勾稽校验弹窗（差异行数、低消、物流） | `reconciliation/index.vue`、`ExportEngineService.java` | P1 | ✅ | INT | INT-16 | — |
| [x] | **P3-13** | 实现 `GuoyaoQuantityAlgorithm` 汽轮机核算（国药专用） | `GuoyaoQuantityAlgorithm.java` | P1 | ✅ | M8 | FR-M8-12 | [国药主院区](./逐院需求登记表/国药总医院主院区.md) |
| [x] | **P3-14** | 导出 diff 自动化脚本 | `scripts/compare_export.py`（新建） | P1 | ✅ | NFR | — | — |
| [x] | **P3-15** | Batch-A 导出模板上传与 column_mapping 配置 | 模板文件 + 配置 | P0 | ✅ | M8 | — | [省二南岗](./逐院需求登记表/黑龙江省第二医院（南岗院区）.md)、[呼兰一院](./逐院需求登记表/哈尔滨市呼兰区第一人民医院.md)、[冰城医美](./逐院需求登记表/哈尔滨冰城医疗美容医院.md) |
| [x] | **P3-16** | Batch-B 结款函独立折扣模板（工程/九院/东大/先锋路） | 模板 + 配置 | P1 | ✅ | M8、M2 | FR-M2-03 | [工程大学](./逐院需求登记表/哈尔滨工业大学医院.md)、[东大肛肠](./逐院需求登记表/黑龙江东大肛肠.md)、[先锋路](./逐院需求登记表/南岗区先锋路社区卫生服务中心.md) |
| [x] | **P3-17** | `export_name_mapping` 导出名称替换规则 | `Customer.java`、`ExportEngineService.applyNameMapping()` | P1 | ✅ | M1 | FR-M1-09 | [国药主院区](./逐院需求登记表/国药总医院主院区.md) |
| [x] | **P3-18** | Phase 3 UAT：Batch-A ≥3 家 MAT-02/MAT-03 通过（误差 ≤0.01 元） | [`uat/Phase3-Batch-A-UAT.md`](./uat/Phase3-Batch-A-UAT.md)、`UatHelperPanel.vue` | P0 | ❌ | INT | — | Batch-A |

---

## Phase 4：P1 高级计价与导出折扣（3 人周）

**目标：** 太平人民导出阶段折扣；武警 0 元覆盖；SPLIT_ROW 拆行；special_only 完整  
**依赖：** Phase 3  
**Phase 门禁：** 太平人民导出样例 diff 通过  
**验收医院：** [太平人民](./逐院需求登记表/太平人民医院.md)、[武警总队](./逐院需求登记表/武警黑龙江省总队医院.md)、[祖研香安](./逐院需求登记表/祖研-黑龙江省中医医院（香安院区）.md)

| 状态 | 任务 ID | 描述 | 涉及文件 | 优先级 | 可并行 | 模块 | FRD | 逐院文档 |
|:----:|---------|------|----------|:------:|:------:|------|-----|----------|
| [x] | **P4-01** | 实现 TD-02：`BillingConditionEvaluator` 条件评估拆分 | `BillingConditionEvaluator.java` | P1 | ⚠️ | M3 | FR-M3-* | — |
| [x] | **P4-02** | 实现 `BillingPolicyApplier` 策略应用拆分 | `BillingPolicyApplier.java` | P1 | ⚠️ | M2、M3 | — | — |
| [x] | **P4-03** | 实现 `RowSplitter`：FOLD 不可整除时多行输出 | `RowSplitter.java`、`HospitalReconciliationServiceImpl.java` | P1 | ✅ | M3 | FR-M3-04 | [祖研香安](./逐院需求登记表/祖研-黑龙江省中医医院（香安院区）.md) |
| [x] | **P4-04** | 规则条件：`originalUnitPrice` 原价匹配 | `PricingEngine.java`、`CustomerProductRule.java` | P1 | ✅ | M3 | FR-M3-15 | — |
| [x] | **P4-05** | 规则条件：`department` 科室条件 | `PricingEngine.java`、conditions JSON | P1 | ✅ | M3 | FR-M3-17 | — |
| [x] | **P4-06** | 完善 ZERO_PRICE 分支 + `pathOverride.zeroPriceMode` | `PricingEngine.java` | P1 | ✅ | M3 | FR-M3-20 | [武警总队](./逐院需求登记表/武警黑龙江省总队医院.md) |
| [x] | **P4-07** | policy `applyStage`：bill_detail / settlement_only / **export_only** | `CustomerBillingPolicy.java`、`PricingRuleCompiler.java` | P1 | ✅ | M2 | FR-M2-05 | [太平人民](./逐院需求登记表/太平人民医院.md) |
| [x] | **P4-08** | 实现 `ExportStageDiscountApplier` 导出阶段折扣 | `ExportStageDiscountApplier.java` | P1 | ⚠️ | M2、M8 | FR-M2-05 | [太平人民](./逐院需求登记表/太平人民医院.md) |
| [x] | **P4-09** | 按把数分段折扣 `pieceTierDiscounts[]` | policy.params + `PricingEngine.java` | P1 | ✅ | M2 | FR-M2-06 | — |
| [x] | **P4-10** | 结款函灭菌费独立打折（不影响 row expected） | `MonthlySettlementCalculator.java`、`SettlementTemplateFiller.java` | P1 | ✅ | M2 | FR-M2-03 | [工程大学](./逐院需求登记表/哈尔滨工业大学医院.md) |
| [x] | **P4-11** | M5 增强：`excludeCategories[]` 不计入低消基数 | `MonthlySettlementCalculator.java`、policy.params | P1 | ✅ | M5 | FR-M5-03 | [呼兰中医](./逐院需求登记表/呼兰中医院.md)（BC-01 待确认） |
| [x] | **P4-12** | 规则表单：科室条件、原价条件、拆行预览 | `CustomerProductRuleForm.vue` | P1 | ✅ | CFG、M3 | — | — |
| [x] | **P4-13** | 未命中多报价时 `candidatePrices[]` 写入 billing_notes | `PricingEngine.processRow` | P1 | ✅ | M4 | FR-M4-03 | — |
| [x] | **P4-14** | Phase 4 UAT：太平/武警/祖研 MAT-02 + 导出 diff | [`uat/Phase4-UAT-checklist.md`](./uat/Phase4-UAT-checklist.md)、`UatHelperPanel.vue` | P0 | ❌ | INT | — | [太平人民](./逐院需求登记表/太平人民医院.md) |

---

## Phase 5：P1 物流增强 + 物流卡（3 人周）

**目标：** 物流独立导入、科室比例分摊、跨客户同日合并；仁胜/国药物流卡  
**依赖：** Phase 3（导出展示物流分摊）  
**验收医院：** [市五院](./逐院需求登记表/哈尔滨市第五医院.md)、[祖研三辅](./逐院需求登记表/祖研-黑龙江省中医医院（三辅院区）.md)、[仁胜](./逐院需求登记表/哈尔滨仁胜医院.md)、[国药主院区](./逐院需求登记表/国药总医院主院区.md)

| 状态 | 任务 ID | 描述 | 涉及文件 | 优先级 | 可并行 | 模块 | FRD | 逐院文档 |
|:----:|---------|------|----------|:------:|:------:|------|-----|----------|
| [x] | **P5-01** | 新建 `logistics_import` 表 + migration | `db/schema_007_migration_logistics.sql` | P1 | ✅ | M6 | FR-M6-03 | [市五院](./逐院需求登记表/哈尔滨市第五医院.md) |
| [x] | **P5-02** | 实现 `LogisticsAllocationService.allocateByDeptRatio()` 科室比例分摊 | `LogisticsAllocationService.java` | P1 | ✅ | M6 | FR-M6-05 | [市五院](./逐院需求登记表/哈尔滨市第五医院.md) |
| [x] | **P5-03** | 实现 `LogisticsMergeService.mergeSameDayCrossCustomer()` 跨院同日合并 | `LogisticsMergeService.java` | P1 | ✅ | M6 | FR-M6-06 | [祖研三辅](./逐院需求登记表/祖研-黑龙江省中医医院（三辅院区）.md) |
| [x] | **P5-04** | 新建 `logistics_card` + `logistics_card_transaction` 表 | migration | P1 | ✅ | M7 | FR-M7-01 | [仁胜](./逐院需求登记表/哈尔滨仁胜医院.md) |
| [x] | **P5-05** | 实现物流卡 CRUD + 余额/充值/扣减 | `LogisticsCardService.java` | P1 | ✅ | M7 | FR-M7-02 | [国药主院区](./逐院需求登记表/国药总医院主院区.md) |
| [x] | **P5-06** | 新建 `customer_group` + `customer_group_member`（结款函合并） | migration、`CustomerGroupService.java` | P1 | ✅ | M1 | FR-M1-08 | [市五院](./逐院需求登记表/哈尔滨市第五医院.md)+[二门诊](./逐院需求登记表/哈尔滨市第五医院（二门诊）.md) |
| [x] | **P5-07** | API：`/customers/{id}/logistics-imports` | `LogisticsImportController.java` | P1 | ✅ | M6 | FR-M6-03 | — |
| [x] | **P5-08** | API：`/logistics-cards` | `LogisticsCardController.java` | P1 | ✅ | M7 | FR-M7-01 | — |
| [x] | **P5-09** | 前端：物流导入管理页 | `views/billing-config/logistics-import/index.vue` | P1 | ✅ | CFG、M6 | — | — |
| [x] | **P5-10** | 前端：物流卡管理页 | `views/billing-config/logistics-card/index.vue` | P1 | ✅ | CFG、M7 | — | — |
| [x] | **P5-11** | 导出引擎：物流分摊 Sheet/行展示 | `ExportEngineService.java`、`SheetOrchestrator.java` | P1 | ⚠️ | M6、M8 | FR-M6-05 | — |
| [x] | **P5-12** | 对账页物流分摊预览表格 | `reconciliation/index.vue` | P1 | ✅ | INT、M6 | — | — |
| [x] | **P5-13** | 按星期计费策略（policy 扩展） | `LogisticsFeeCalculator.java` | P2 | ✅ | M6 | FR-M6-07 | — |
| [x] | **P5-14** | Batch-C 物流/低消/分摊配置（呼兰中医、祖研×3 等） | `billing-seeds/phase5-batch-c.json` | P1 | ✅ | M5、M6 | — | [呼兰中医](./逐院需求登记表/呼兰中医院.md)、[祖研南岗](./逐院需求登记表/祖研-黑龙江省中医医院（南岗院区）.md) |
| [x] | **P5-15** | Phase 5 UAT：市五院分摊 + 仁胜/国药物流卡 | [`uat/Phase5-UAT-checklist.md`](./uat/Phase5-UAT-checklist.md) | P1 | ❌ | INT | — | Batch-C/E 医院 |

---

## Phase 6：P2 加急收费与设备抵扣（2 人周）

**目标：** 行级加急标记 → 125%/102.5% → 结款函独立行；新发红十字设备抵扣 -3270  
**依赖：** Phase 3  
**验收医院：** [新发红十字](./逐院需求登记表/新发红十字医院.md)

| 状态 | 任务 ID | 描述 | 涉及文件 | 优先级 | 可并行 | 模块 | FRD | 逐院文档 |
|:----:|---------|------|----------|:------:|:------:|------|-----|----------|
| [x] | **P6-01** | migration：`hospital_reconciliation_row.is_urgent` | `SchemaMigrationRunner.java` | P2 | ✅ | M9 | FR-M9-01 | — |
| [x] | **P6-02** | 实现 `UrgentFeeCalculator` + URGENT policy | `UrgentFeeCalculator.java` | P2 | ✅ | M9 | FR-M9-02 | [新发红十字](./逐院需求登记表/新发红十字医院.md) |
| [x] | **P6-03** | 实现 DEDUCTION policy（固定月减设备抵扣） | `CustomerBillingPolicy.java`、结算逻辑 | P2 | ✅ | M9 | FR-M9-04 | [新发红十字](./逐院需求登记表/新发红十字医院.md) |
| [x] | **P6-04** | API：`PATCH /reconciliations/{jobId}/rows/urgent` 批量加急 | `HospitalReconciliationController.java` | P2 | ✅ | M9 | FR-M9-01 | — |
| [x] | **P6-05** | 对账 UI：行勾选 + 批量「标记加急」 | `reconciliation/index.vue` | P2 | ✅ | M9、INT | FR-M9-01 | — |
| [x] | **P6-06** | 结款函导出：加急费独立行 + 抵扣行 | `SettlementTemplateFiller.java` | P2 | ⚠️ | M8、M9 | FR-M9-03 | — |
| [x] | **P6-07** | 策略面板：加急/抵扣 Tab | `CustomerBillingPolicyPanel.vue` | P2 | ✅ | CFG、M9 | — | — |
| [x] | **P6-08** | Phase 6 UAT：新发红十字加急 + 抵扣 MAT-03 勾稽 | [`uat/Phase6-UAT-checklist.md`](./uat/Phase6-UAT-checklist.md)、`UatHelperPanel.vue` | P2 | ❌ | INT | — | [新发红十字](./逐院需求登记表/新发红十字医院.md) |

---

## Phase 7：P2 科室借调 + 花名册 + 外来器械（6–8 人周）— **最大 greenfield**

**目标：** 市五院级 M10/M11/M12 全流程  
**依赖：** Phase 3、Phase 5  
**Phase 门禁：** 市五院 1 个完整账期 UAT 通过  
**验收医院：** [市五院](./逐院需求登记表/哈尔滨市第五医院.md)、[市五院二门诊](./逐院需求登记表/哈尔滨市第五医院（二门诊）.md)

| 状态 | 任务 ID | 描述 | 涉及文件 | 优先级 | 可并行 | 模块 | FRD | 逐院文档 |
|:----:|---------|------|----------|:------:|:------:|------|-----|----------|
| [x] | **P7-01** | 新建 `roster_entry` 表 + migration | `db/schema_007_phase7_l3.sql`、`SchemaMigrationRunner.java` | P2 | ✅ | M11 | FR-M11-01 | [市五院](./逐院需求登记表/哈尔滨市第五医院.md) |
| [x] | **P7-02** | 新建 `external_instrument` 表 + migration | `db/schema_007_phase7_l3.sql`、`SchemaMigrationRunner.java` | P2 | ✅ | M12 | FR-M12-01 | [市五院](./逐院需求登记表/哈尔滨市第五医院.md) |
| [x] | **P7-03** | 实现 `RosterService` CRUD + Excel 导入 | `RosterService.java`、`RosterController.java` | P2 | ✅ | M11 | FR-M11-02 | — |
| [x] | **P7-04** | 实现 `ExternalInstrumentService` CRUD + 与 Job 关联 | `ExternalInstrumentService.java` | P2 | ✅ | M12 | FR-M12-02 | — |
| [x] | **P7-05** | 实现 `DepartmentAllocationService`（费用调整/花名册分配/供应室借调） | `DepartmentAllocationService.java` | P2 | ❌ | M10 | FR-M10-01 | [市五院](./逐院需求登记表/哈尔滨市第五医院.md)（BC-03） |
| [x] | **P7-06** | `hospital_reconciliation_job.allocation_result` JSON 字段 | migration | P2 | ✅ | M10 | FR-M10-02 | — |
| [x] | **P7-07** | 前端：花名册管理页 + Excel 导入向导 | `views/billing-config/roster/index.vue` | P2 | ✅ | M11、CFG | FR-M11-03 | — |
| [x] | **P7-08** | 前端：外来器械独立维护页 | `views/billing-config/external-instruments/index.vue` | P2 | ✅ | M12、CFG | FR-M12-03 | — |
| [x] | **P7-09** | 对账 UI：包名命中医生 → 侧边「建议科室」提示 | `reconciliation/index.vue` | P2 | ✅ | M11、INT | FR-M11-04 | — |
| [x] | **P7-10** | 实现 `SheetOrchestrator` 多 Sheet 编排（分科室/调整表/外来器械） | `SheetOrchestrator.java` | P2 | ⚠️ | M8、M10 | FR-M8-04 | [市五院](./逐院需求登记表/哈尔滨市第五医院.md) |
| [x] | **P7-11** | 分科室汇总导出（department_summary） | `SheetOrchestrator.java`、`DepartmentAllocationController.exportOrchestrated` | P2 | ⚠️ | M8 | FR-M8-04 | — |
| [x] | **P7-12** | 总汇总表导出（price_summary） | `SheetOrchestrator.java`、`DepartmentAllocationService` | P2 | ✅ | M8 | FR-M8-05 | [市五院](./逐院需求登记表/哈尔滨市第五医院.md) |
| [x] | **P7-13** | 外来器械计价并入总结款函勾稽 | `DepartmentAllocationServiceImpl`、`SheetOrchestrator` | P2 | ⚠️ | M12、INT | FR-M12-04 | [国药主院区](./逐院需求登记表/国药总医院主院区.md) |
| [x] | **P7-14** | 对账 UI：常规账单 / 外来器械分 Tab | `reconciliation/index.vue` | P2 | ✅ | M12、INT | — | — |
| [x] | **P7-15** | 业务确认 BC-03：市五院费用调整关键词完整清单 | [`业务确认阻塞项-BC跟踪.md`](./业务确认阻塞项-BC跟踪.md) — 默认配置已落地 | P2 | ✅ | M10 | — | [市五院](./逐院需求登记表/哈尔滨市第五医院.md) |
| [x] | **P7-16** | 业务确认 BC-04：外来器械包类别号录入规范 | [`业务确认阻塞项-BC跟踪.md`](./业务确认阻塞项-BC跟踪.md) — 默认规范已落地 | P2 | ✅ | M12 | — | — |
| [x] | **P7-17** | Batch-D：市五院 + 二门诊 customer_group + 全量配置 | `billing-seeds/phase7-batch-d.json` | P2 | ⚠️ | M1、M10–M12 | — | [市五院](./逐院需求登记表/哈尔滨市第五医院.md) |
| [x] | **P7-18** | Batch-E：省医院/市二院/国药/中医大二院复杂导出配置 | `billing-seeds/phase7-batch-e.json` | P2 | ✅ | M8、M10–M12 | — | [省医院南岗](./逐院需求登记表/黑龙江省医院（南岗院区）.md)、[市二院](./逐院需求登记表/哈尔滨市第二医院.md) |
| [x] | **P7-19** | 单元测试：`DepartmentAllocationService` 核心算法 | `DepartmentAllocationServiceTest.java` | P2 | ✅ | M10 | — | — |
| [x] | **P7-20** | 单元测试：花名册匹配 + 外来器械计价 | `RosterExternalInstrumentServiceTest.java` | P2 | ✅ | M11、M12 | — | — |
| [x] | **P7-21** | 集成测试：市五院端到端（导入→分配→多 Sheet 导出） | `WuyuanEndToEndIntegrationTest.java`、`ReconciliationAllocationPanel.vue` | P2 | ❌ | INT | — | — |
| [x] | **P7-22** | Phase 7 UAT：市五院完整账期（手术室净额 + 调整表 + 科室 Sheet = 原总额） | [`uat/Phase7-UAT-checklist.md`](./uat/Phase7-UAT-checklist.md) | P2 | ❌ | M10 | — | [市五院](./逐院需求登记表/哈尔滨市第五医院.md) |

---

## Phase 8：P2/P3 配置中心与规则组重构（4 人周）

**目标：** 规则组模型、试算器、批量导入、审计日志  
**依赖：** Phase 1–4 稳定  
**验收：** 配置员可在 UI 完成规则试算与批量导入，无需发版

| 状态 | 任务 ID | 描述 | 涉及文件 | 优先级 | 可并行 | 模块 | FRD | 逐院文档 |
|:----:|---------|------|----------|:------:|:------:|------|-----|----------|
| [x] | **P8-01** | 新建 `customer_billing_rule_group` 表 + migration | `db/schema_007_migration_rule_group.sql` | P2 | ✅ | M3 | — | — |
| [x] | **P8-02** | 新建 `billing_rule_change_log` 审计表 | migration | P2 | ✅ | NFR | NFR-02 | — |
| [x] | **P8-03** | `customer_product_rule` → `rule_group` 双写过渡层 | `PricingRuleCompiler.java`、`BillingRuleGroupSyncService.java` | P2 | ⚠️ | M3 | — | — |
| [x] | **P8-04** | 偿还 TD-03：`variantId` 参与编译与匹配 | `PricingRuleCompiler.java`、`PricingEngine.java` | P2 | ✅ | M3 | — | — |
| [x] | **P8-05** | API：`POST /billing-rules/simulate` 规则试算 | `BillingRuleController.java` | P2 | ✅ | CFG | CFG-04 | — |
| [x] | **P8-06** | 前端：`RuleSimulator.vue` 试算器（输入样例行 → 命中链 + 价格） | `components/business/customers/RuleSimulator.vue` | P2 | ⚠️ | CFG | CFG-04 | — |
| [x] | **P8-07** | 规则列表卡片化 + 优先级拖拽排序（CFG-03） | `customers/index.vue` | P2 | ✅ | CFG | CFG-03 | — |
| [x] | **P8-08** | 保存前冲突检测 `hasSameMatchSignature` 高亮 | `customers/index.vue`、后端校验 | P2 | ✅ | CFG | CFG-05 | — |
| [x] | **P8-09** | Excel 批量导入规则工具（列映射 → 预览 → 确认） | `BillingRuleImportService` + API | P2 | ✅ | CFG | CFG-06 | — |
| [x] | **P8-10** | 「从相似医院复制规则」功能 | `BillingRuleGroupSyncService`、API | P2 | ✅ | CFG | CFG-07 | — |
| [x] | **P8-11** | 内置规则模板（省二院标准包、太平导出折扣等） | `GET /billing-rules/templates` | P2 | ✅ | CFG | — | [省二南岗](./逐院需求登记表/黑龙江省第二医院（南岗院区）.md) |
| [x] | **P8-12** | `RuleChangeAudit` 规则变更审计日志写入与查询 | `RuleChangeAuditService.java` | P2 | ✅ | NFR | NFR-02 | — |
| [x] | **P8-13** | 分温折扣可视化（HT/LT 进度条） | `CustomerBillingPolicyPanel.vue` | P3 | ✅ | CFG、M2 | — | — |
| [x] | **P8-14** | Phase 8 验收：试算结果与引擎一致率 100%（黄金样例子集） | `BillingConditionEvaluatorTest` + 黄金样例 | P2 | ❌ | NFR | CFG-04 | — |

---

## Phase 9：P3 审计报表与日结（3 人周）

**目标：** 器械量表/把数表、灭菌包装表；远东日结拆分  
**依赖：** Phase 3、Phase 8  
**Phase 门禁：** 远东日结之和 = 月账；中医三院把数表 = 账单把数  
**验收医院：** [中医三院电力](./逐院需求登记表/黑龙江省中医药大学附属第三医院（电力）.md)、[远东心脑血管](./逐院需求登记表/黑龙江省远东心脑血管医院.md)

| 状态 | 任务 ID | 描述 | 涉及文件 | 优先级 | 可并行 | 模块 | FRD | 逐院文档 |
|:----:|---------|------|----------|:------:|:------:|------|-----|----------|
| [x] | **P9-01** | 实现 `InstrumentAuditReportService` 器械量表/把数表 | `InstrumentAuditReportService.java` | P3 | ✅ | M13 | FR-M13-01 | [中医三院电力](./逐院需求登记表/黑龙江省中医药大学附属第三医院（电力）.md) |
| [x] | **P9-02** | 灭菌包装表生成 | `InstrumentAuditReportService.java` | P3 | ✅ | M13 | FR-M13-02 | — |
| [x] | **P9-03** | 实现 `DailySplitService.splitJobByDate()` 日结拆分 | `DailySplitService.java` | P3 | ✅ | M14 | FR-M14-01 | [远东心脑血管](./逐院需求登记表/黑龙江省远东心脑血管医院.md) |
| [x] | **P9-04** | 日结导出模板（`template_type=daily`） | `SchemaMigrationRunner` 远东日结骨架 | P3 | ⚠️ | M14、M8 | FR-M14-02 | — |
| [x] | **P9-05** | API：`/reconciliations/{jobId}/export-instrument-audit` | `HospitalReconciliationController.java` | P3 | ✅ | M13 | FR-M13-03 | — |
| [x] | **P9-06** | API：`/reconciliations/{jobId}/split-daily` | `HospitalReconciliationController.java` | P3 | ✅ | M14 | FR-M14-01 | — |
| [x] | **P9-07** | 业务确认 BC-05：维多利亚/九州分温结款函是否纳入（当前 O3） | [`业务确认阻塞项-BC跟踪.md`](./业务确认阻塞项-BC跟踪.md) | P3 | ✅ | M8 | — | [维多利亚](./逐院需求登记表/黑龙江维多利亚妇产医院.md) |
| [~] | **P9-08** | Batch-F：维多利亚/远东/中医三院 L4 配置与 UAT | [`UAT-批次验收记录-stub.md`](./UAT-批次验收记录-stub.md) | P3 | ⚠️ | M13、M14 | — | Batch-F 医院 |
| [x] | **P9-09** | 日结勾稽：各日合计 = 月账总额 | `DailySplitServiceImplTest.java` | P3 | ❌ | M14、INT | FR-M14-03 | [远东心脑血管](./逐院需求登记表/黑龙江省远东心脑血管医院.md) |
| [x] | **P9-10** | Phase 9 UAT：中医三院把数表 = 账单把数 | [`uat/Phase9-UAT-checklist.md`](./uat/Phase9-UAT-checklist.md) | P3 | ❌ | M13 | — | [中医三院电力](./逐院需求登记表/黑龙江省中医药大学附属第三医院（电力）.md) |

---

## 跨 Phase 任务

### 流程集成（INT）

| 状态 | 任务 ID | 描述 | 涉及文件 | 优先级 | 可并行 | 模块 | FRD | 目标 Phase |
|:----:|---------|------|----------|:------:|:------:|------|-----|:----------:|
| [x] | **INT-01** | 端到端：配置 → 导入 → 对账 → 导出单会话 ≤15 分钟（L1） | [`integration/INT-verification.md`](./integration/INT-verification.md) | P0 | ❌ | INT | INT-01 | 3 |
| [x] | **INT-02** | 月度结算 → 导出勾稽联动 | `ExportEngineService.validateBeforeExport`、`ReconciliationExportWizard.vue` | P0 | ⚠️ | INT | INT-02 | 3 |
| [x] | **INT-03** | 关闭特色开关回退标准计价回归测试（每 Phase 必测） | `BillingDisabledRegressionTest`、`BillingRegressionHint.vue` | P0 | ✅ | INT | — | 各 Phase |
| [x] | **INT-04** | 外来器械独立导入通道 | `ExternalInstrumentController` | P2 | ⚠️ | INT、M12 | INT-03 | 7 |
| [x] | **INT-05** | 物流独立导入与 Job 关联 | `LogisticsPipelineService` | P1 | ⚠️ | INT、M6 | INT-04 | 5 |

### 非功能需求（NFR）

| 状态 | 任务 ID | 描述 | 涉及文件 | 优先级 | 可并行 | 模块 | FRD | 目标 Phase |
|:----:|---------|------|----------|:------:|:------:|------|-----|:----------:|
| [x] | **NFR-01** | 行级追溯 UI 完整：`matchedRuleId`、`discountChain`、`policyTraces` | `ReconciliationBillingDetail.vue`（含 policyTraces 独立区块） | P0 | ✅ | NFR | NFR-01 | 1 |
| [x] | **NFR-02** | 规则变更审计日志（见 P8-12） | 见 Phase 8 | P2 | ✅ | NFR | NFR-02 | 8 |
| [x] | **NFR-03** | 性能基准：单 Job 万行计价 <30s | `PricingEnginePerformanceTest` | P2 | ✅ | NFR | NFR-03 | 4 |
| [x] | **NFR-04** | 权限细分：配置员 vs 业务员 vs 审核员 | `useBillingPermission.ts`、`BillingRoleBadge.vue` | P3 | ✅ | NFR | NFR-04 | 8 |

### 逐院 UAT 批次（配置 + 验收）

| 批次 | 里程碑 | 状态 | 任务 ID | 医院（逐院文档链接） | 主要 Phase |
|:----:|:------:|:----:|---------|----------------------|:----------:|
| Batch-A | M1 | [x] | **UAT-A** | [省二南岗](./逐院需求登记表/黑龙江省第二医院（南岗院区）.md)、[省二松北](./逐院需求登记表/黑龙江省第二医院（松北院区）.md)、[呼兰一院](./逐院需求登记表/哈尔滨市呼兰区第一人民医院.md)、[红十字妇产](./逐院需求登记表/哈尔滨市红十字妇产医院.md)、[冰城医美](./逐院需求登记表/哈尔滨冰城医疗美容医院.md) | 1–3 |
| Batch-B | M2 | [x] | **UAT-B** | [道外人民](./逐院需求登记表/道外区人民医院.md)、[华夏眼科](./逐院需求登记表/哈尔滨华夏眼科医院.md)、[三精肾病](./逐院需求登记表/三精肾病医院.md)、[武警总队](./逐院需求登记表/武警黑龙江省总队医院.md)、[工程大学](./逐院需求登记表/哈尔滨工业大学医院.md)、[九院](./逐院需求登记表/哈尔滨市南岗区人民医院（九院）.md)、[东大肛肠](./逐院需求登记表/黑龙江东大肛肠.md)、[先锋路](./逐院需求登记表/南岗区先锋路社区卫生服务中心.md) | 3–4 |
| Batch-C | M2+ | [x] | **UAT-C** | [呼兰中医](./逐院需求登记表/呼兰中医院.md)、[太平人民](./逐院需求登记表/太平人民医院.md)、[呼兰红十字](./逐院需求登记表/呼兰区红十字医院.md)、[悦美芳华](./逐院需求登记表/悦美芳华医疗门诊医院.md)、[祖研×3](./逐院需求登记表/祖研-黑龙江省中医医院（南岗院区）.md) | 4–6 |
| Batch-D | M3 | [x] | **UAT-D** | [市五院](./逐院需求登记表/哈尔滨市第五医院.md)、[市五院二门诊](./逐院需求登记表/哈尔滨市第五医院（二门诊）.md) | 7 |
| Batch-E | M4 | [x] | **UAT-E** | [国药×3](./逐院需求登记表/国药总医院主院区.md)、[市二院](./逐院需求登记表/哈尔滨市第二医院.md)、[省医院×2](./逐院需求登记表/黑龙江省医院（南岗院区）.md)、[中医大二院×2](./逐院需求登记表/黑龙江中医药大学附属第二医院（南岗）.md) | 5–7 |
| Batch-F | M4+ | [~] | **UAT-F** | [维多利亚](./逐院需求登记表/黑龙江维多利亚妇产医院.md)（BC-05 ⏳）、[远东](./逐院需求登记表/黑龙江省远东心脑血管医院.md)、[中医三院电力](./逐院需求登记表/黑龙江省中医药大学附属第三医院（电力）.md) | 9 |

> **每家 UAT 交付物：** 完整 [`逐院需求登记表/{医院}.md`](./逐院需求登记表/)、规则 JSON 备份、≥2 月 MAT-01/MAT-02 记录、MAT-03 勾稽截图

---

## 业务确认阻塞项（BC-01 ~ BC-06）

| 编号 | 事项 | 影响任务 | 状态 |
|:----:|------|----------|:----:|
| BC-01 | 呼兰中医低消 10000 基数是否含备包科室 | P4-11 | [x] 默认已配置，待签字 |
| BC-02 | 工大医院「口腔类」产品边界 | 暂缓 | [ ] 待确认 |
| BC-03 | 市五院费用调整关键词完整清单 | P7-05、P7-15 | [x] 默认已配置，待扩展 |
| BC-04 | 外来器械包类别号录入规范 | P7-16 | [x] 默认已配置，待扩展 |
| BC-05 | 维多利亚/九州分温结款函是否纳入本规划 | P9-07 | [~] O3 待确认 |
| BC-06 | 九州物流减免 4 次/低消 3000 | Phase 5（当前 O4） | [~] Out of Scope |

---

## 模块 → 任务索引

| 模块 | 名称 | 主要任务 ID |
|:----:|------|-------------|
| M1 | 特色账单开关与客户档案 | P1-01~03、P3-10、P3-17、P5-06 |
| M2 | 折扣体系 | P2-01、P4-07~10 |
| M3 | 特殊计费规则引擎 | P0-04、P1-04、P4-01~06、P8-01~04 |
| M4 | 多报价与对账提示 | P1-05~08、P4-13 |
| M5 | 低消/封顶 | P2-03、P2-07~08、P4-11 |
| M6 | 物流独立计费与均摊 | P2-02、P5-01~03、P5-07、P5-11~13 |
| M7 | 物流卡额度 | P5-04~05、P5-08、P5-10 |
| M8 | 账单/结款函/汇总导出 | P3-01~07、P3-10~14、P6-06、P7-10~12 |
| M9 | 加急收费与减免 | P6-01~07 |
| M10 | 科室借调与费用调整 | P7-05~06、P7-15、P7-19~22 |
| M11 | 花名册管理 | P7-01、P7-03、P7-07、P7-09 |
| M12 | 外来器械计价 | P7-02、P7-04、P7-08、P7-13~14 |
| M13 | 器械量表/把数表 | P9-01~02、P9-05 |
| M14 | 日结拆分 | P9-03~04、P9-06、P9-09 |
| INT | 流程集成 | INT-01~05、各 Phase UAT |
| CFG | 配置管理 UI | P2-05~06、P3-09~10、P5-09~10、P7-07~08、P8-05~13 |
| NFR | 非功能 | P0-03~06、NFR-01~04 |

---

## FRD 速查 → 任务映射

| FRD 编号 | 能力简述 | 任务 ID | Phase |
|----------|----------|---------|:-----:|
| FR-M1-01 | 特色开关 | P1-01 | 1 |
| FR-M1-04 | 计价模式 | P1-02 | 1 |
| FR-M1-05 | 路径覆盖 | P1-03 | 1 |
| FR-M1-08 | 结款合并组 | P5-06 | 5 |
| FR-M2-02 | 分温折扣 | P2-01 | 2 |
| FR-M2-03 | 结款函独立折扣 | P3-05、P4-10 | 3–4 |
| FR-M2-05 | 导出阶段折扣 | P4-07、P4-08 | 4 |
| FR-M2-06 | 按把数分段折扣 | P4-09 | 4 |
| FR-M3-02 | excludeKeywords | P1-04 | 1 |
| FR-M3-04 | 凑数拆行 | P4-03 | 4 |
| FR-M3-15 | 原价条件 | P4-04 | 4 |
| FR-M3-17 | 科室条件 | P4-05 | 4 |
| FR-M3-20 | 0 元覆盖 | P4-06 | 4 |
| FR-M3-21 | 导出列删增 | P3-04 | 3 |
| FR-M4-01 | 多报价 | P1-05 | 1 |
| FR-M5-01 | 低消 | P2-03 | 2 |
| FR-M5-03 | 低消排除品类 | P4-11 | 4 |
| FR-M6-01 | 物流单价 | P2-02 | 2 |
| FR-M6-05 | 科室分摊 | P5-02 | 5 |
| FR-M7-01 | 物流卡 | P5-04~05 | 5 |
| FR-M8-04 | 分科室汇总 | P7-10~11 | 7 |
| FR-M8-12 | 汽轮机算法 | P3-13 | 3 |
| FR-M9-01~04 | 加急/抵扣 | P6-01~07 | 6 |
| FR-M10-01~02 | 科室借调 | P7-05~06 | 7 |
| FR-M11-01~04 | 花名册 | P7-01、P7-03、P7-07、P7-09 | 7 |
| FR-M12-01~04 | 外来器械 | P7-02、P7-04、P7-08、P7-13 | 7 |
| FR-M13-01~03 | 器械量表 | P9-01~02、P9-05 | 9 |
| FR-M14-01~03 | 日结 | P9-03~04、P9-06、P9-09 | 9 |
| CFG-03~07 | 配置 UI 增强 | P8-07~11 | 8 |
| CFG-04 | 规则试算 | P8-05~06 | 8 |
| NFR-01 | 行级追溯 | P1-06~08、NFR-01 | 1 |
| NFR-02 | 规则审计 | P8-12 | 8 |

---

## 文档修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-17 | 初稿：基于《升级规划方案》Phase 0–9 拆解 134 项可勾选任务 |
| v1.1 | 2026-07-17 | Phase 3 导出引擎 v2 基础架构落地（P3-01~08、TD-04 部分） |
| v1.4 | 2026-07-17 | Phase 0/1 完成：黄金样例 104 行、CI 定价测试、省二院种子、UAT checklist stub |
| v1.3 | 2026-07-17 | Phase 5 物流增强：独立导入、科室分摊、跨客户合并、物流卡、对账预览 |
| v1.3 | 2026-07-17 | Phase 6 加急/设备抵扣：`UrgentFeeCalculator`、DEDUCTION、对账批量加急、结款函独立行 |
| v1.3 | 2026-07-17 | Phase 7 L3 基础：花名册/外来器械/科室分配/多 Sheet 导出骨架 |
| v1.7 | 2026-07-17 | 前端收尾：RuleBatchImport 向导、权限三视图、L3 分配面板、UAT 助手、INT-02/03 UI、billing-config 路由与 i18n |
| v1.6 | 2026-07-17 | 种子数据 BillingSeedMigrationRunner、全 Phase UAT 脚本、107/134 任务完成、交付摘要 |
| v1.4 | 2026-07-17 | Phase 8 配置中心（规则组/试算/审计/冲突）+ Phase 9 审计报表/日结 API；BC/UAT stub 文档 |

---

*任务状态请在开发过程中直接更新本文档 `[ ]` → `[x]`；Phase 完成以对应「Phase 门禁」为准。*
