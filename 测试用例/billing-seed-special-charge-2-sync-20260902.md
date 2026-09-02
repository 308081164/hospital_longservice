# 特殊收费(2) + 附一收费标准(2) 规则迁移报告（2026-09-02）

路径 B — 规则 / billing-seed 同步检查。材料：`docs/source/特殊收费(2).xlsx`、`docs/source/附一收费标准(2).xlsx`（工作副本在 `测试用例/新规则20260902/`）。

## 一、特殊收费(2) vs 特殊收费(17) 差异（逐行集合对比）

三个 sheet 中「通用特殊收费」「环氧与低温通用收费」**零差异**。「各医院特殊收费」+15/-8 行，语义归纳：

| 医院 | 变化 | 语义 |
|------|------|------|
| 电机厂医院（GUOYAO-2） | 删除①缝合针（1*5.5+2.5=8元），其余项目重编号 | 缝合针院级规则移除；通用缝合针规则（1×5.5+包材，小袋2.5）结果同为 8 元，覆盖一致 |
| 哈尔滨基准生物（JZSW-BIO） | ②氩氦刀「额外包ETO」→「额外包（ETO）」 | 仅括号样式，无规则变化 |
| 奥兰医院（序号27） | **新增医院** | 双：＜3 → 5.5×件数+标准包材；≥3 → 5.5×件数 |
| 哈尔滨市胸科医院（序号28） | **新增医院** | 手术衣（打包好的）/W9050 固定20元；小件-多少件（ETO）固定25元 |
| 哈尔滨森海医院（序号29） | **新增医院** | 双：＜3 → 5.5×件数+标准包材（≤16.5）；≥3 → 5.5×件数；胶帽：≤5 按1件算低温标准包材；＞5 → ⌈件数/5⌉×22 |

其余 24 家医院规则与 Excel17 完全一致。

## 二、附一收费标准(2) vs 系统现状（ZYY-D1）

Sheet2 为最终整理版（Sheet1 为草稿区，含编辑批注）。逐项核对：

| Excel 条目 | 系统现状 | 结论 |
|---|---|---|
| 高温 1 件纸塑袋 10/15/20/25cm → 6.39/8.79/10.39/12.79 | standardPricingOverride + 单把规则 | 一致 |
| 高温 2 件 = 袋价+4.4（13.19/10.79） | capMode=fuyi + 两件套规则 | 一致 |
| 高温 3 件及以上 4.4/把（13.2 起） | nonWoven flatPerPackagePrice=4.4/threshold=3/minCharge=13.2 | 一致 |
| 低温 1 件 30/20/10/15/25cm → 27.97/22.38/17.58/19.98/23.98 | 低温袋规则 + override bagSizes | 一致 |
| 低温套 5/10/20 件 → 70.33/131.87/239.76，余数每件 17.58 叠加 | tierPrices + remainderPerPiecePrice=17.58（15件=10件套+5件套=202.2） | 一致 |
| 敷料 20cm*20cm → 3.2 | 敷料20x20 规则 3.2 | 一致 |
| 敷料 长15cm*宽10cm → 2 | 系统仅有 Sheet1 版关键词「大（20cm*20cm*15cm）」 | **已补关键词**「长15cm*宽10cm」「15cm*10cm」（同价 2 元，纯补充） |
| 敷料大/小/中 → 27.97/19.98/23.98 | 敷料大30x30x50/敷料小20x20x15/敷料中20x20x30 | 一致 |
| 手术室 30°腹腔镜-1 → 30.38（22.38+保护盒8） | 30°腹腔镜组合价 30.38 + 低温器械保护盒 8 | 一致 |
| 外二 换药包(120布) → 21.99 | 换药包120布组合价 21.99 + 外二科室规则 | 一致 |
| 耳鼻喉 冲洗头/橄榄头 按包类型收费、器械5件算1件 | 冲洗头/橄榄头5件算1件 FOLD + 型号固定价 | 一致 |

## 三、系统能力缺口结论

**无缺口**。新规则全部由现有机制覆盖：PRICE_PER_INSTRUMENT + min/maxInstrumentCount（双分段）、FOLD threshold/foldRatio + temperature=LT（胶帽）、FIXED_PRICE + 精确关键词（手术衣/小件）、standardPricingOverride tierPrices（低温套）。未改动任何引擎代码。

## 四、迁移内容

1. 新种子 `phase-special-charge-2-sync-20260902.json`（已注册 INCREMENTAL_SEEDS + batch-patch 分发）：
   - profiles 建档：AOLAN-YY 奥兰医院、HRB-XK-YY 哈尔滨市胸科医院、SENHAI-YY 哈尔滨森海医院（hybrid，billing_enabled=1）
   - newRules 8 条（奥兰 2 / 胸科 2 / 森海 4）
   - deleteRules：GUOYAO-2「电机厂缝合针8元」（校正价8.0 为 P0 校正规则、非 Excel 来源，保留不动）
   - ruleUpdates：ZYY-D1「敷料大20x20x15」补 Sheet2 关键词
2. STRICT_KEEP_CODES 26→29（Java + Python 同步）。
3. **修复 manifest 生成器两个缺陷**：
   - 新增 `deleteRules` 硬删除处理（此前忽略，导致 Excel17 对 HL-ZGH「镜头租借公司筐加收」的删除被 reconciler 按 manifest 重建回生产库——静默回滚，本次一并修复）；
   - HARDCODED_RULES 移除 HL-ZGH「镜头租借公司筐加收」（Java 侧早已删除，Python 侧遗漏）。
4. manifest 三份重生成（hash 9ff06dce4f7cba57…），差异精确为：+3 客户、GUOYAO-2 -1 规则、HL-ZGH -1 规则。

## 五、验证结果

- 后端编译通过；规则相关测试（SpecialCharge11CoverageTest / BillingConditionEvaluatorTest / KeywordMatchModeTest）全绿。
- PricingEngineTest 11 个失败与本次改动**无关**：HEAD 基线同样失败（逐一比对失败清单完全相同），属存量问题。
- 本地 Docker 重建后落库核对：3 家新客户建档成功（规则 2/2/4 条字段值逐项正确）；GUOYAO-2 缝合针规则已删；HL-ZGH 镜头租借规则已被 reconciler 清理。
- `hospital-cli rules compare --profile local --all`：**29/29 OK，0 missing / 0 extra / 0 changed**。

## 六、待确认事项

1. **附一低温袋15cm**：系统院级规则价 20 元（7 月账单 parity 验证结果），Excel Sheet2 为 19.98 元（Sheet1 显示 20 被修订为 19.98）。实际账单口径与 Excel 口径冲突，未擅自改动，请确认以哪个为准。
2. **胸科小件 ETO**：Excel 仅限 ETO；系统温度域粒度为 LT（低温等离子+ETO 同属 LT），规则已按 LT 限定（高温不命中）。若需严格区分 ETO/等离子，需引擎升级，暂按既有「软镜300」同款约定处理。
3. 3 家新医院暂无 raw/proc 账期材料，路径 A 严格 Excel 对账将标记 SKIP，待材料到位后扩展。
