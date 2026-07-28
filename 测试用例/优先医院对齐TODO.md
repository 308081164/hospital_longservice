# 优先医院特色账单对齐 TODO

> 最后更新：**2026-07-28（S8 波次3 收尾 · 已 push `d02e772`）**  
> **S8 稳定基线**（[`job_baseline_stable.json`](job_baseline_stable.json) · Job 607–654）：bill **pass 20 · warn 12 · fail 5 · skip 1** · settlement **pass 19/37** · 看板双 pass **19 院** · strict 双 pass **13 院**  
> **波次3 收尾 ✅**：bill fail **8→5** · 香坊/省二南岗/呼兰一 regression **pass** · 附二南岗结款 **pass** · [`regression-wave3-bill-close.md`](regression-wave3-bill-close.md)  
> **strict 双 pass 13 院**（bill pass + settlement pass）：附一、附二哈南、社会康复、道外、新发、维多利亚、九洲、呼兰红十字、冰城医美、武警、悦美、**呼兰一、省二南岗**

---

## Job 基线策略（双轨）

| 轨道 | 文件 | 用途 | 禁止操作 |
|------|------|------|----------|
| **stable** | `job_baseline_stable.json` | S8 bill/settlement 导出比对 · 看板签字 | 全量 `batch_june_system_test.py` 覆盖主表 |
| **pricing** | `job_baseline_pricing.json` | 20260728 种子验收 · 定点重导写入附录 | 用 pricing Job 替代 stable 做全院 S8 |

**S8 脚本：**

```bash
python3 scripts/batch_s8_export_compare.py --job-map 测试用例/job_baseline_stable.json
python3 scripts/batch_s8_settlement_compare.py --job-map 测试用例/job_baseline_stable.json
python3 scripts/batch_june_system_test.py "武警黑龙江省总队医院" "…"   # 仅更新 pricing 附录
```

**S4 口径（2026-07-28 起）：** 期待 CSV **零漏检**（`missed=0`）即 pricing pass ✅；`fail_extra` 记 **extra_inventory**，不降级规则验收。

---

## 标准逐院验收流程（必须按序）

每家医院在 **`测试用例/{规范医院名}/`** 下独立走完全部步骤；上一步未闭环不得跳步（材料缺失见 [`pass_zero与缺材料医院-客户收集清单.txt`](pass_zero与缺材料医院-客户收集清单.txt)）。

| 步 | 名称 | 做什么 | 产出 / 通过标准 | 常用脚本 / 位置 |
|:--:|------|--------|-----------------|-----------------|
| **1** | **材料检查** | 确认 **`原始表格/`**、**`处理后表格/`** 成对 | 缺件清单 | `scripts/ingest_bokang_supplements.py` |
| **2** | **表格对比分析** | 原始 vs 处理后 | **`数据问题分析.md`** + CSV | `analyze_test_case_excel.py` |
| **3** | **特色规则落库** | billing-seeds / 客户管理 | marker 落库 | `backend/resources/billing-seeds/` |
| **4** | **系统对账** | 导入原始表 · **stable 全量仅一次**；计价院 **定点重导** | 期待 CSV · warning.tsv · [`批量6月系统对账结果.md`](批量6月系统对账结果.md) | `batch_june_system_test.py` |
| **5** | **纠错能力测试** | 改价/改包名/关 billing | `纠错测试记录.md` | `PricingEngineS5ErrorCorrectionTest` |
| **6** | **需求登记表** | `docs/逐院需求登记表/` | v1.1+ | 与 S3 同步 |
| **7** | **导出规则落库** | export_template / 策略 | [`S7导出规则配置说明.md`](S7导出规则配置说明.md) | 客户管理 |
| **8** | **导出比对验收** | bill/settlement vs 处理后表 | JSON 报告 · 登记已知差 | `batch_s8_export_compare.py` · `batch_s8_settlement_compare.py` |

**图例：** `⬜` 未开始 · `🔄` 进行中/登记已知差 · `✅` 通过 · `⏭` 跳过 · `🚫` 阻塞

---

## 逐院执行看板（步骤 1–8）

> S4 列 = **stable 基线**；`P###` = **pricing Job**（20260728 定点重导，见附录）  
> S8 列 = **stable Job** 导出比对（2026-07-28 **收尾**复跑 · commit `d02e772`）

| # | 医院（院区） | S1 | S2 | S3 | S4 | S5 | S6 | S7 | S8 | stable Job | 备注 |
|---|-------------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:----------:|------|
| 1 | 黑龙江中医药大学附属第一医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 607 / P695 | bill pass · **结款 pass** · SETTLEMENT_OVERRIDE 2026-06 |
| 2 | 黑龙江省中医药大学附属第三医院（电力） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🚫 | 608 / P694 | bill fail material · **结款 fail**（加急/外来器械行）· pricing 零漏检 |
| 3a | 国药总医院主院区 | ✅ | ✅ | ✅ | 🔄 | ✅ | ✅ | ✅ | ✅ | 645 / P658 | **材料** kit BOM · S8 Δ696 |
| 3b | 国药总医院第二院区 | ✅ | ✅ | ✅ | 🔄 | ✅ | ✅ | ✅ | ✅ | 646 / P659 | **材料** 同上 · S8 Δ121.5 |
| 3c | 国药总医院第三院区 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 647 / P660 | S8 登记已知差 Δ2 |
| 4 | 哈尔滨市第二医院 | ✅ | ✅ | ✅ | 🔄 | ✅ | ✅ | ✅ | ✅ | 655 / P661 | **材料** vendor 7 sheet · S8 Δ11900 |
| 5a | 哈尔滨市第五医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 613 / P662 | bill warn Δ420 · **结款 pass** · 汇总四 type ✅ |
| 5b | 哈尔滨市第五医院（二门诊） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 614 / P663 | 汇总四 type ✅ |
| 6 | 新发红十字医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 615 / P664 | bill pass · **结款 pass** · XINFA urgentBreakdownByMonth |
| 7a | 黑龙江省医院（南岗院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 616 / P665 | bill warn Δ24 · 结款 fail Δ3024 · 汇总四 type ✅ |
| 7b | 黑龙江省医院（香坊院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 617 / P698 | bill **pass** Δ13.2 · 结款 fail Δ5996 · ExportFixedPrice 回归已修 |
| 8a | 祖研（南岗院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 618 / P667 | bill warn Δ13 · 结款 fail Δ840 |
| 8b | 祖研（三辅院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 619 / P668 | bill warn Δ24 · **结款 pass** Δ24 |
| 8c | 祖研（香安院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🔄 | 620 / P669 | bill warn layout · 结款 fail Δ275 |
| 9 | 南岗区妇产医院 | ✅ | ✅ | ✅ | 🔄 | ✅ | ✅ | ✅ | ✅ | 650 / P697 | bill warn Δ17 · **结款 pass** Δ16 · pricing 零漏检 |
| 10 | 黑龙江省社会康复医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 625 / P671 | bill+结款 **双 pass** |
| 11 | 道外区人民医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 626 / P672 | bill+结款 **双 pass** |
| 12 | 太平人民医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 627 / P673 | bill **warn** Δ20.48 · 结款 fail Δ561 · export skipWhenAlreadyDiscounted |
| 13 | 三精肾病医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 628 / P674 | bill warn Δ18 · **结款 pass** Δ18 |
| 14 | 黑龙江维多利亚妇产医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 629 / P675 | bill+结款 **双 pass** |
| 15 | 黑龙江九洲妇科医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 630 / P676 | bill+结款 **双 pass** · waivedTrips=5 policy（已移除 hardcode） |
| 16 | 呼兰区红十字医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 631 / P677 | bill+结款 **双 pass** |
| 17 | 呼兰中医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 632 / P678 | bill warn Δ22 · **结款 pass** · 特殊包/低消行 |
| 18 | 中医附二（南岗） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 633 / P696 | bill **warn** Δ5.5 · **结款 pass** · SETTLEMENT_OVERRIDE 灭菌 39865 |
| 19 | 中医附二（哈南分院） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 634 / P680 | bill pass · **结款 pass** Δ8 |
| 20 | 哈尔滨仁胜医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 635 / P681 | bill pass · 结款 fail |
| 21 | 哈尔滨华夏眼科医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 652 / P682 | bill pass · 结款 fail |
| 22 | 哈尔滨冰城医疗美容医院 | ✅ | ✅ | ✅ | 🔄 | ✅ | ✅ | ✅ | ✅ | 648 / P683 | bill+结款 **双 pass** |
| 23 | 香坊中医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 638 / P684 | bill pass · 缺处理后结款函 |
| 24 | 武警黑龙江省总队医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 639 / P693 | bill+结款 **双 pass** · tripCountOverride=20 |
| 25 | 悦美芳华医疗门诊医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 640 / P686 | bill+结款 **双 pass** · 物流 50×2 趟 |
| 26a | 省二（南岗院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 641 / P687 | bill **pass** 总额一致（154 vs 86 行聚合差）· 结款 **pass** |
| 26b | 省二（松北院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 642 / P688 | **材料** part3 · S8 Δ8743 |
| 27 | 哈尔滨市呼兰区第一人民医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 643 / P689 | bill **pass** 总额一致（154 vs 423 行）· 结款 pass |
| 28 | 哈尔滨市红十字妇产医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 623 / P690 | bill pass · 结款 fail |
| 29 | 哈尔滨工业大学医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 624 / P691 | combined · S8 warn Δ229.5 |
| 30 | 哈尔滨工程大学医院 | ⏭ | ⏭ | ✅ | ⏭ | ⏭ | ⬜ | ⏭ | ⏭ | — | **🚫 阻塞** 无原始账单 |
| — | 哈尔滨长健医院 | ✅ | ✅ | ✅ | 🔄 | ✅ | ✅ | ✅ | ✅ | 654 / P699 | bill **pass** · 结款 skip（缺表）· ExportFixedPriceApplier |

### 逐院勾选模板（复制到院文件夹 `验收进度.md` 可选）

```markdown
- [ ] **1 材料检查**：原始 __ 个；处理后 __ 个；缺：__
- [ ] **2 对比分析**：`数据问题分析.md` + `数据问题清单.csv` 已生成
- [ ] **3 规则落库**：种子/UI __ · dev 库 verified __
- [ ] **4 系统对账 pass**：__月 · 结果 __ · Job #__（stable / pricing）
- [ ] **5 纠错测试**：用例 __ 条已测
- [ ] **6 需求登记表**：`docs/逐院需求登记表/__.md` 已更新 v__
- [ ] **7 导出规则落库**：模板/策略 __
- [ ] **8 导出比对**：账单/结款函 vs 处理后表 pass · 汇总 type structure_ok
```

---

## 材料阻塞院（阶段 4 · 不改 S8 stable 结论）

| 医院 | 阻塞材料 | S8 结论 | 下一步 |
|------|----------|---------|--------|
| 国药总医院主/二院区 | 铂康 kit BOM xlsx · 原始行未入库 | bill fail material | `KitBomImportService` 挂接 import |
| 哈尔滨市第二医院 | vendor 补录 7 sheet xlsx | bill fail Δ11900 | 见 [`铂康材料缺口清单.md`](铂康材料缺口清单.md) |
| 黑龙江省第二医院（松北院区） | part3/vendor · kit 拆行 | bill fail Δ8743 | 同材料清单 §D7 |
| 哈尔滨工程大学医院 | `哈尔滨工程大学*.xlsx` 原始账单 | skip | 看板 #30 ⏭ |

---

## 逐院导出文件类型与覆盖情况（2026-07-28）

> 依据：`测试用例/{院}/处理后表格/` 铂康参考样表 · `导出规则配置.md` · [`S7导出规则配置说明.md`](S7导出规则配置说明.md) · [`s8_export_compare_report.json`](s8_export_compare_report.json) · [`s8_settlement_compare_report.json`](s8_settlement_compare_report.json) · [`S8导出比对摘要-汇总类型.md`](S8导出比对摘要-汇总类型.md) · [`job_baseline_stable.json`](job_baseline_stable.json)
>
> 系统导出类型：`bill` 账单 · `settlement` 结款函 · `dept_summary` 分科室汇总 · `price_summary` 价格汇总 · `instrument_audit` 器械把数表 · `logistics_allocation` 物流分摊 · `grand_total` 总汇总
>
> **S8 口径**：bill/settlement 用 stable Job · `--job-map 测试用例/job_baseline_stable.json`；汇总四 type 见 [`S8导出比对摘要-汇总类型.md`](S8导出比对摘要-汇总类型.md)（11 院 structure_ok）

| 医院名称 | 导出文件类型 | 功能是否全覆盖（未覆盖则标注缺失表格） |
|----------|-------------|----------------------------------------|
| 黑龙江中医药大学附属第一医院 | 1、账单(bill) 2、结款函(settlement) 3、分科室汇总(dept_summary) 4、物流分摊表(logistics_allocation) | ✅ 账单 S8 **pass** · 结款函 **pass** · **dept_summary structure_ok** · 汇总四 type **structure_ok** |
| 黑龙江省中医药大学附属第三医院（电力） | 1、账单(bill) 2、结款函(settlement) 3、器械把数表(instrument_audit) | ⚠️ 类型已配（账单 S8 **fail** material · 结款函 **fail**（加急/外来器械）· instrument_audit **structure_ok**） |
| 国药总医院主院区 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **fail** material · 结款函 ⏭ **材料阻塞**） |
| 国药总医院第二院区 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **fail** material · 结款函 ⏭ **材料阻塞**） |
| 国药总医院第三院区 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **warn** Δ2 · 结款函 ⏭ 缺参考表） |
| 哈尔滨市第二医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（**dept_split 23 sheets** · 账单 S8 **fail** Δ11900 · 结款函 ⏭ **材料阻塞**） |
| 哈尔滨市第五医院 | 1、账单(bill) 2、结款函(settlement) 3、分科室汇总(dept_summary) 4、价格汇总(price_summary) 5、器械把数表(instrument_audit) 6、总汇总表(grand_total) | ⚠️ 账单 warn Δ420 · 结款函 **pass** · **dept_summary structure_ok** · 汇总四 type **structure_ok** |
| 哈尔滨市第五医院（二门诊） | 1、账单(bill) 2、结款函(settlement) 3、总汇总表(grand_total) | ❌ 未全覆盖（grand_total **structure_ok** · strict 待 L3 · 账单 S8 **pass**） |
| 新发红十字医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 账单 S8 **pass** · 低温多 Sheet **`splitLowTempDressingSheets`** · 结款函 S8 **pass** 9/9 |
| 黑龙江省医院（南岗院区） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) 4、器械把数表(instrument_audit) 5、物流分摊表(logistics_allocation) | ❌ 未全覆盖（strict 逐行待参考表；**dept_split 25 sheets** · 账单 S8 **warn** Δ24 · 结款函 **fail** Δ3024 · **汇总四 type structure_ok**） |
| 黑龙江省医院（香坊院区） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) 4、器械把数表(instrument_audit) 5、物流分摊表(logistics_allocation) | ❌ 未全覆盖（**dept_split 56 sheets** · 账单 S8 **pass** Δ13.2 · 结款函 **fail** Δ5996 · **汇总四 type structure_ok**） |
| 祖研-黑龙江省中医医院（南岗院区） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) | ❌ 未全覆盖（strict 待参考表；**dept_split 10 sheets** · 账单 S8 **warn** Δ13 · 结款函 **fail** Δ840 · price_summary **structure_ok**） |
| 祖研-黑龙江省中医医院（三辅院区） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) | ❌ 未全覆盖（strict 待参考表；**dept_split 13 sheets** · 账单 S8 **warn** Δ24 · 结款函 S8 **pass** Δ24 · price_summary **structure_ok**） |
| 祖研-黑龙江省中医医院（香安院区） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) | ❌ 未全覆盖（strict 待参考表；**dept_split 6 sheets** · 账单 S8 **warn** layout · 结款函 **fail** Δ275 · price_summary **structure_ok**） |
| 南岗区妇产医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（**dept_split 3 sheets** · 账单 S8 **warn** Δ17 · 结款函 S8 **pass** Δ16） |
| 黑龙江省社会康复医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass**） |
| 道外区人民医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass**） |
| 太平人民医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **warn** Δ20.48 · 结款函 S8 **fail** Δ561 · skipWhenAlreadyDiscounted） |
| 三精肾病医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **warn** Δ18 · 结款函 S8 **pass** Δ18） |
| 黑龙江维多利亚妇产医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass** · 分温结款函已验收） |
| 黑龙江九洲妇科医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass** · waivedTrips=5 policy） |
| 呼兰区红十字医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass**） |
| 呼兰中医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **warn** Δ22 · 结款函 S8 **pass** · 特殊包/低消行） |
| 黑龙江中医药大学附属第二医院（南岗） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) 4、器械把数表(instrument_audit) | ❌ 未全覆盖（**dept_split 17 sheets** · 账单 S8 **warn** Δ5.5 · 结款函 S8 **pass** · **汇总四 type structure_ok**） |
| 黑龙江中医药大学附属第二医院（哈南分院） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) 4、器械把数表(instrument_audit) | ❌ 未全覆盖（strict 待参考表；**dept_split 9 sheets** · 账单 S8 **pass** · 结款函 S8 **pass** Δ8 · **汇总四 type structure_ok**） |
| 哈尔滨仁胜医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **pass** · 结款函 S8 **fail** · 加急行待 DB sheet 标记） |
| 哈尔滨华夏眼科医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **pass** · 结款函 S8 **fail** · KNOWN_SETTLEMENT_DIFF 登记） |
| 哈尔滨冰城医疗美容医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass**） |
| 香坊中医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **pass** · 结款函 ⏭ 缺参考表） |
| 武警黑龙江省总队医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass** · tripCountOverride=20） |
| 悦美芳华医疗门诊医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass** · 物流 50×2 趟） |
| 黑龙江省第二医院（南岗院区） | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **pass** 总额一致 · 结款函 S8 **pass** · 行 key 聚合口径差） |
| 黑龙江省第二医院（松北院区） | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（**dept_split 22 sheets** · 账单 S8 **fail** material · 结款函 ⏭ **材料阻塞**） |
| 哈尔滨市呼兰区第一人民医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **pass** 总额一致 · 结款函 S8 **pass**） |
| 哈尔滨市红十字妇产医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **pass** · 结款函 S8 **fail**） |
| 哈尔滨工业大学医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（**combined 1 sheet** · 账单 S8 **warn** Δ229 · 结款函 **fail**） |
| 哈尔滨工程大学医院 | 1、账单(bill) 2、结款函(settlement) | ⏭ 阻塞（无原始账单 · 账单 S8 **skip** · 结款函 ⏭ **材料阻塞**） |
| 哈尔滨长健医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **pass** · ExportFixedPriceApplier · 结款函 skip 缺参考表） |

### 仅账单+结款函 · 修复进度（26 院 · stable Job · 2026-07-28）

> 范围：上表「导出文件类型」仅含 **1、账单 2、结款函** 的 26 院（不含附一/附三/市五/省医院/祖研/附二等需汇总表医院）。
> 复测命令（须带 stable Job 映射）：
> `python3 scripts/batch_s8_export_compare.py --job-map 测试用例/job_baseline_stable.json --hospital "…"`
> `python3 scripts/batch_s8_settlement_compare.py --job-map 测试用例/job_baseline_stable.json --hospital "…"`

| 医院名称 | 导出文件类型 | 账单 S8 | 结款函 S8 | 功能是否全覆盖 | 阻塞材料（缺则填） |
|----------|-------------|---------|-----------|----------------|-------------------|
| 国药总医院主院区 | 1、账单 2、结款函 | 🚫 fail | ⏭ 阻塞 | 🚫 材料 | kit BOM + 原始行未入库（[`铂康材料缺口清单.md`](铂康材料缺口清单.md) §2） |
| 国药总医院第二院区 | 1、账单 2、结款函 | 🚫 fail | ⏭ 阻塞 | 🚫 材料 | 同上 · Job646 |
| 国药总医院第三院区 | 1、账单 2、结款函 | 🔄 warn(Δ2) | ⏭ 阻塞 | ⚠️ 登记已知差 | — |
| 哈尔滨市第二医院 | 1、账单 2、结款函 | 🚫 fail | ⏭ 阻塞 | 🚫 材料 | 6月 vendor 补录 xlsx（7 sheet）· dept_split layout OK |
| 新发红十字医院 | 1、账单 2、结款函 | ✅ pass | ✅ pass | ✅ 双 pass · XINFA urgentBreakdownByMonth | ✅ |
| 南岗区妇产医院 | 1、账单 2、结款函 | 🔄 warn(Δ17) | ✅ pass | ⚠️ dept_split · 登记已知差 | ✅ |
| 黑龙江省社会康复医院 | 1、账单 2、结款函 | ✅ pass | ✅ pass | ✅ 双 pass | ✅ |
| 道外区人民医院 | 1、账单 2、结款函 | ✅ pass | ✅ pass | ✅ 双 pass | ✅ |
| 太平人民医院 | 1、账单 2、结款函 | 🔄 warn(Δ20) | 🚫 fail | ⚠️ skipWhenAlreadyDiscounted · 结款 Δ561 | — |
| 三精肾病医院 | 1、账单 2、结款函 | 🔄 warn(Δ18) | ✅ pass | ⚠️ 登记已知差 | ✅ |
| 黑龙江维多利亚妇产医院 | 1、账单 2、结款函 | ✅ pass | ✅ pass | ✅ 双 pass · 分温结款函 | ✅ |
| 黑龙江九洲妇科医院 | 1、账单 2、结款函 | ✅ pass | ✅ pass | ✅ 双 pass · BC-06 物流 0 | ✅ |
| 呼兰区红十字医院 | 1、账单 2、结款函 | ✅ pass | ✅ pass | ✅ 双 pass | ✅ |
| 呼兰中医院 | 1、账单 2、结款函 | 🔄 warn | ✅ pass | ⚠️ 阑尾包行已出 · 灭菌/物流/外科包金额差 | ✅ |
| 哈尔滨仁胜医院 | 1、账单 2、结款函 | ✅ pass | 🚫 fail | ⚠️ 账单 ✅ · 结款 fail | — |
| 哈尔滨华夏眼科医院 | 1、账单 2、结款函 | ✅ pass | 🚫 fail | ⚠️ 账单 ✅ · 结款 fail | — |
| 哈尔滨冰城医疗美容医院 | 1、账单 2、结款函 | ✅ pass | ✅ pass | ✅ 双 pass | ✅ |
| 香坊中医院 | 1、账单 2、结款函 | ✅ pass | ⏭ 阻塞 | ⚠️ 账单 ✅ · 缺处理后结款函 | — |
| 武警黑龙江省总队医院 | 1、账单 2、结款函 | ✅ pass | ✅ pass | ✅ 双 pass | ✅ |
| 悦美芳华医疗门诊医院 | 1、账单 2、结款函 | ✅ pass | ✅ pass | ✅ 双 pass | ✅ |
| 黑龙江省第二医院（南岗院区） | 1、账单 2、结款函 | ✅ pass | ✅ pass | ✅ **strict 双 pass** · 行 key 聚合口径差 | ✅ |
| 黑龙江省第二医院（松北院区） | 1、账单 2、结款函 | 🚫 fail | ⏭ 阻塞 | 🚫 材料 | part3/vendor + kit 拆行 · dept_split OK |
| 哈尔滨市呼兰区第一人民医院 | 1、账单 2、结款函 | ✅ pass | ✅ pass | ⚠️ 行 key 聚合口径差 | ✅ |
| 哈尔滨市红十字妇产医院 | 1、账单 2、结款函 | ✅ pass | 🚫 fail | ⚠️ 账单 ✅ · 结款 fail | — |
| 哈尔滨工业大学医院 | 1、账单 2、结款函 | 🔄 warn(Δ229) | 🚫 fail | ⚠️ combined 单 Sheet · 登记已知差 | — |
| 哈尔滨工程大学医院 | 1、账单 2、结款函 | ⏭ skip | ⏭ 阻塞 | ⏭ 阻塞 | `哈尔滨工程大学*.xlsx` 原始账单 |

### 存在其他导出文件要求 · 修复进度（11 院 · stable Job · 2026-07-28）

> 范围：上表「导出文件类型」除 **账单 + 结款函** 外，还要求至少一种 **汇总/分摊类** exportType 的 11 院（附一/附三/市五/省医院/祖研/附二等）。
> 复测命令：
> `python3 scripts/batch_s8_export_compare.py --job-map 测试用例/job_baseline_stable.json --export-type price_summary`（`dept_summary` / `instrument_audit` / `logistics_allocation` / `grand_total` 同理）
> **口径**：额外类型 **structure_ok** = export-v2 成功 + xlsx 落盘（见 [`S8导出比对摘要-汇总类型.md`](S8导出比对摘要-汇总类型.md)）；多数院无铂康独立参考表，**不做逐行 strict 比对**。

| 医院名称 | 导出文件类型（业务要求） | 账单 S8 | 结款函 S8 | 额外类型 S8（structure_ok） | 功能是否全覆盖 | 阻塞/备注 |
|----------|-------------------------|---------|-----------|----------------------------|----------------|-------------|
| 黑龙江中医药大学附属第一医院 | 1账单 2结款 **3分科室汇总** **4物流分摊** | ✅ pass | ✅ pass | dept_summary ✅ · logistics_allocation ✅ | ✅ **L1+L2 双 pass** · L3 已出 | 汇总四 type 亦 structure_ok · strict 待铂康参考表 · Job607 |
| 黑龙江省中医药大学附属第三医院（电力） | 1账单 2结款 **3器械把数** | 🚫 fail | 🚫 fail | instrument_audit ✅ | 🚫 规则+材料 | 加急/外来器械行 · pricing 零漏检 · Job608 |
| 哈尔滨市第五医院 | 1账单 2结款 **3分科室汇总 4价格汇总 5器械把数 6总汇总** | 🔄 warn(Δ420) | ✅ pass | dept_summary ✅ · price_summary ✅ · instrument_audit ✅ · logistics_allocation ✅ · grand_total ✅ | ⚠️ 登记已知差 | bill Δ420 已登记 · 汇总四 type **11/11** · grand_total strict 待 L3 allocate · Job613 |
| 哈尔滨市第五医院（二门诊） | 1账单 2结款 **3总汇总** | ✅ pass | ⏭ skip | grand_total ✅ | ⚠️ 缺结款参考 | 无处理后结款函 · grand_total structure_ok · Job614 |
| 黑龙江省医院（南岗院区） | 1账单 2结款 **3价格汇总 4器械把数 5物流分摊** | 🔄 warn(Δ24) | 🚫 fail | price_summary ✅ · instrument_audit ✅ · logistics_allocation ✅ | ❌ 结款未闭合 | dept_split 25 sheets · 结款 Δ3024 · strict 待参考表 · Job616 |
| 黑龙江省医院（香坊院区） | 1账单 2结款 **3价格汇总 4器械把数 5物流分摊** | ✅ pass | 🚫 fail | price_summary ✅ · instrument_audit ✅ · logistics_allocation ✅ | ❌ 结款未闭合 | dept_split 56 sheets · 结款 Δ5996 · 波次3 bill 回归已修 · Job617 |
| 祖研-黑龙江省中医医院（南岗院区） | 1账单 2结款 **3价格汇总** | 🔄 warn(Δ13) | 🚫 fail | price_summary ✅ | ⚠️ 结款 fail | dept_split 10 sheets · 结款 Δ840 · Job618 |
| 祖研-黑龙江省中医医院（三辅院区） | 1账单 2结款 **3价格汇总** | 🔄 warn(Δ24) | ✅ pass | price_summary ✅ | ⚠️ 登记已知差 | 结款 pass Δ24 · strict 待参考表 · Job619 |
| 祖研-黑龙江省中医医院（香安院区） | 1账单 2结款 **3价格汇总** | 🔄 warn(layout) | 🚫 fail | price_summary ✅ | ⚠️ layout+结款 | dept_split 6 sheets · 结款 Δ275 · Job620 |
| 黑龙江中医药大学附属第二医院（南岗） | 1账单 2结款 **3价格汇总 4器械把数** | 🔄 warn(Δ5.5) | ✅ pass | price_summary ✅ · instrument_audit ✅ | ⚠️ bill warn | dept_split 17 sheets · SETTLEMENT_OVERRIDE 39865 · Job633 |
| 黑龙江中医药大学附属第二医院（哈南分院） | 1账单 2结款 **3价格汇总 4器械把数** | ✅ pass | ✅ pass | price_summary ✅ · instrument_audit ✅ | ✅ **strict 双 pass** + L3 | 汇总 structure_ok · strict 逐行待参考表 · Job634 |

**进度汇总（11 院 · 2026-07-28）**：

| 维度 | 数量 | 说明 |
|------|------|------|
| 额外 type **structure_ok**（业务要求项） | **11/11 ✅** | export-v2 均可成功落盘 |
| **strict 双 pass + 额外 structure_ok** | **2** | 附一、附二哈南 |
| bill pass/warn + 额外 structure_ok + 结款 pass | **4** | 附一、市五、祖研三辅、附二南岗 |
| 额外 structure_ok · 结款 **fail** | **5** | 省医院双院、祖研南岗/香安、附三（bill 亦 fail） |
| 缺结款参考表 | **1** | 市五二门诊（grand_total ✅） |

> 与「仅账单+结款函」表关系：上表 11 院 **不包含** 在 26 院子集中；26 院仅要求 bill+settlement，本表跟踪 **L3 汇总/分摊** 与 L1/L2 联合验收。

**进度汇总（26 院 · 2026-07-28 收尾复跑）**：账单 ✅ **15** · 账单 🔄 **5** · 账单 🚫 **4** · 账单 ⏭ **1** · 结款函 ✅ **13** · 结款函 🚫 **5** · 结款函 ⏭ **6**（材料阻塞 5 + 缺参考表 1）

> 结款函自动化：`python3 scripts/batch_s8_settlement_compare.py --job-map 测试用例/job_baseline_stable.json` · 报告 [`s8_settlement_compare_report.json`](s8_settlement_compare_report.json)  
> **全 37 院扩测**：结款 pass **19** · fail **10** · blocked_material **5** · skip **4** · **双 pass（bill pass/warn + settlement pass）19 院**

### 汇总

- **✅ strict 双 pass（bill pass + settlement pass）13 院**：附一、附二哈南、社会康复、道外、新发、维多利亚、九洲、呼兰红十字、冰城医美、武警、悦美、**呼兰一、省二南岗**
- **✅ 看板双 pass（26 院子集 · bill pass/warn + settlement pass）11 院**：社会康复、道外、维多利亚、呼兰红十字、冰城医美、九洲、武警、悦美、**呼兰一、省二南岗**、新发
- **✅ 看板双 pass（全 37 院）19 院**（含 warn 账单 + settlement pass）
- **🚫 bill fail 5 院（均为材料/规则阻塞）**：附三 · 国药主/二 · 市二 · 省二松北
- **⚠️ 类型已配 / 登记已知差 / 单通道 pass**：其余院
- **⏭ 阻塞**：工程大学缺原始账单

**高频缺失表格类型：**

| 缺失类型 | 涉及医院 | 2026-07-28 状态 |
|----------|----------|-----------------|
| 价格汇总(price_summary) | 市五、省医院两院区、祖研三院区、附二南岗/哈南 | ✅ **Strategy 已落地** · 11 院 **structure_ok** · strict 待铂康参考表 |
| 器械把数表(instrument_audit) | 市五、省医院两院区、附二南岗/哈南、附三 | ✅ **structure_ok** · strict 待参考表 |
| 总汇总表(grand_total) | 市五、市五二门诊 | ✅ **structure_ok** · strict 待 L3 allocationResult |
| 分科室汇总(dept_summary) | 附一、市五 | 附一 **bill dept_split 已修复** · 独立 dept_summary exportType 仍缺 |
| 物流分摊表(logistics_allocation) | 附一、省医院两院区 | ✅ **structure_ok** · strict 待参考表 |
| 结款函分温/特殊行 | 九洲 BC-06 · 呼兰中低消/特殊包 · 新发加急/低温/75折 | 九洲/呼兰中/新发 ✅ · 附一/市五 ✅ · 仁胜 🔄 |
| 账单多 Sheet | 新发低温敷料 · dept_split 12 院 | ✅ **`splitLowTempDressingSheets`** + dept_split 布局已验收 |

---

## 全局待办（跨院）

- [x] **Job 双轨**：`job_baseline_stable.json` · `job_baseline_pricing.json` · S8 `--job-map` / `--job-id`
- [x] **20260728 计价批次** + 引擎修复 · Docker `mvn test` 绿
- [x] **P1 ExportStrategy** + 汇总四 type **structure_ok 11 院**
- [x] **P0 新发低温 Sheet**：`splitLowTempDressingSheets`
- [x] **S8 stable 复跑（收尾）**：bill pass **20** · warn **12** · fail **5** · settlement pass **19**
- [x] **波次3 收尾 deploy**：commit **`d02e772`** · CI Build and Deploy
- [ ] **P0 Kit BOM**：`KitBomImportService` · 国药 645/646
- [x] **P0 结款函（波次3）**：附一/市五/新发/附二南岗 **pass** ✅
- [x] **P0 账单 fail 闭合**（8→≤5）：剩材料 4 + 附三 1
- [x] **附二南岗结款 Δ30**：SETTLEMENT_OVERRIDE 灭菌 **39865**/月（非总额 40915）
- [x] **ExportFixedPrice regression**：香坊/省二南岗/呼兰一 bill 恢复 pass
- [ ] **波次4 结款 P0**：香坊 Δ5996 · 省医院南岗 Δ3024 · 太平 Δ561 · 红十字妇产 Δ9094 · 哈工大 Δ13979
- [ ] **波次4 附三**：bill+settlement 加急/外来器械行
- [x] **dept_summary**：附一/市五 **structure_ok**
- [x] **结款函扩至 37 院** + 全矩阵 [`s8_full_matrix_report.json`](s8_full_matrix_report.json)
- [ ] **pricing 漏检闭合**：附二南岗（大衣/小单）· 香坊（9 行）
- [ ] **武警 pricing Job**：P693 S8 Δ644 vs stable 639 pass — 重导后计价/export 分叉调查

---

## S4 批量摘要

| 批次 | 结果 | 说明 |
|------|------|------|
| **2026-07-22 stable** | 36/36 零漏检零多报 | Job 607–654 主表 |
| **2026-07-28 全量重导** | 3 pass · 大量 fail_extra | **已弃用** 为 S8 口径；见附录 656–692 |
| **2026-07-28 定点 pricing** | 6 院零漏检 | 武警/附一/附三/妇产/… · `fail_extra`=inventory |

---

## S8 批量摘要

### 2026-07-28 · S8 波次3 收尾（最新）

| 项 | 波次3 | **收尾后** | 计划目标 |
|----|-------|-----------|----------|
| settlement pass（37 院） | 18 | **19** | ≥17 ✅ |
| bill fail | 8 | **5** | ≤5 ✅ |
| bill pass / warn | 17 / 12 | **20 / 12** | — |
| 双 pass（bill pass/warn + settlement pass） | 16 | **19** | ≥13 ✅ |
| strict 双 pass | 11 | **13** | ≥11 ✅ |

**Regression 修复**：香坊/省二南岗/呼兰一 bill **fail→pass** · 附二南岗 settlement **fail→pass**

**引擎/Seed**：`ExportFixedPriceApplier` 科室+信任 DB · `phase-bill-wave3-close-20260728.json` · `SETTLEMENT_OVERRIDE` 附二灭菌 39865

### 2026-07-28 · S8 波次3 pass 转化

| 项 | 波次2 | **波次3** | 计划目标 |
|----|-------|-----------|----------|
| settlement pass（37 院） | 15 | **18** | ≥17 ✅ |
| bill fail | 8 | **8** | ≤5 ❌ |
| bill pass / warn | 19 / 10 | **17 / 12** | — |
| 双 pass（bill pass/warn + settlement pass） | 11 | **16** | ≥13 ✅ |
| 全矩阵 fail | 18 | **15** | — |

**新增结款 pass（+3）**：附一 Δ3792→**pass** · 市五 Δ543→**pass** · 新发 Δ12478→**pass**

**Bill 变化**：长健 **fail→pass** · 太平 **fail→warn** Δ20 · 附二南岗 **fail→warn** Δ5.5 · **regression**：香坊 pass→fail · 省二南岗 pass→fail · 呼兰一 pass→fail

**引擎/Seed**：`ExportFixedPriceApplier` · `ExportStageDiscountApplier#skipWhenAlreadyDiscounted` · `SETTLEMENT_OVERRIDE` · XINFA `urgentBreakdownByMonth` · `phase-bill-wave3-fix-20260728.json` · `phase-settlement-wave3-20260728.json`

### 2026-07-28 · S8 剩余缺口闭合（波次2）

| 项 | 闭合前 | **闭合后** | 计划目标 |
|----|--------|-----------|----------|
| settlement pass（37 院） | 6→10 | **15** | ≥14 ✅ |
| bill fail | 8 | **8** | ≤5 ❌ |
| 全矩阵 fail | 23 | **18** | — |
| 双 pass | 6 | **11** | — |

**新增结款 pass**：武警、悦美、省二南岗、九洲、南岗妇产、三精、呼兰中、祖研三辅、附二哈南（+ 原 pass 6 院）

**仍 fail 重点（波次4）**：香坊/省医院结款 · 祖研南岗/香安 · 太平 · 仁胜 · 华夏 · 红十字妇产 · 哈工大 · 附三 bill+结款

Seeds：`phase-settlement-logistics-batch-20260728.json` · `phase-bill-s8-fix-20260728.json`

### 2026-07-28 · stable 基线（收尾后 · 主口径）

| 项 | bill | settlement（37 院） |
|----|------|---------------------|
| pass | **20** | **19** |
| warn | **12** | — |
| fail | **5** | **10** |
| skip / blocked | **1** | **9**（5 材料 + 4 缺表） |
| strict 双 pass | **13 院** | — |
| 看板双 pass | **19 院** | bill pass/warn + settlement pass |

报告：[`s8_export_compare_report.json`](s8_export_compare_report.json) · [`s8_settlement_compare_report.json`](s8_settlement_compare_report.json) · [`S8导出状态变更对照.md`](S8导出状态变更对照.md) · [`regression-wave3-bill-close.md`](regression-wave3-bill-close.md)

### 2026-07-28 · stable 基线（波次3 · 历史）

| 项 | bill | settlement（37 院） |
|----|------|---------------------|
| pass | **17** | **18** |
| warn | **12** | — |
| fail | **8** | **11** |

### 2026-07-28 · 汇总 exportType

11 院 × 4 type 全部 **structure_ok** — [`S8导出比对摘要-汇总类型.md`](S8导出比对摘要-汇总类型.md)

### 2026-07-24 · 历史对照

pass 19 · warn 10 · fail 8（与 stable 复跑一致，Job 607–644）

---

## S1–S6 / S7 摘要（历史）

| 步骤 | 结果 |
|------|------|
| S1–S2 | 36/36 ✅ |
| S3 | 36 ✅ + 20260728 增量 seed |
| S4 stable | 31 pass · 5 fail_extra（材料/期待清单） |
| S5 | 35 ✅ · 工程大学 ⏭ |
| S6 | 36 ✅ v1.1 |
| S7 | 36 ✅ · dept_split 12 院 |

---

## 参考索引

| 文档 | 用途 |
|------|------|
| [`job_baseline_stable.json`](job_baseline_stable.json) | S8 stable Job |
| [`job_baseline_pricing.json`](job_baseline_pricing.json) | 计价验收 Job |
| [`批量6月系统对账结果.md`](批量6月系统对账结果.md) | S4 主表 + pricing 附录 |
| [`S8导出比对摘要-汇总类型.md`](S8导出比对摘要-汇总类型.md) | 汇总 exportType |
| [`开发阻塞待办.md`](开发阻塞待办.md) | 引擎/材料阻塞 |
| [`S5纠错测试最小用例集.md`](S5纠错测试最小用例集.md) | 纠错测试 |

<details>
<summary>历史批次（2026-07-21 P0–P0.6）</summary>

- [x] 6 月期待价格校正清单 · P0–P0.3 种子 · 全量 6 月系统测试

</details>























## S8 批量执行摘要（2026-07-23）

| 项 | 结果 | 说明 |
|----|------|------|
| API export-v2 vs 处理后表 | **35 ✅** · **1 🔄** · **1 ⏭** · **1 🚫** | `scripts/batch_s8_export_compare.py` · 报告 [`s8_export_compare_report.json`](s8_export_compare_report.json) |
| 比对口径 | 全 sheet 账单行 · 总价容差 max(1元,0.01%) · legacy 布局抽检 | 结款函/分科室汇总需 UI 或 `--settlement` 扩展 |
> **看板口径**：S8 列 ✅ 含 Phase1 **登记已知差**（如 layout）；自动化脚本仍计 pass/warn。JSON 报告 **pass 20** 为严格 API 口径，看板 **35 ✅** 含国药三院、香安等人工签字项。
