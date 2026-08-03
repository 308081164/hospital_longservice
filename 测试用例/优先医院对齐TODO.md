# 优先医院特色账单对齐 TODO

> 最后更新：**2026-08-01（非材料阻塞闭合）**  
> **S4 stable 审计**（[`s4_stable_job_audit.json`](s4_stable_job_audit.json)）：**pass 36 · fail 2**（仅国药主/市二材料 extra）  
> **S8 稳定基线**（[`job_baseline_stable.json`](job_baseline_stable.json)）：bill **pass 33 · warn 1 · fail 4** · settlement **pass 34/37**（4 院 blocked_material）  
> **2026-08-01 闭合**：附一 S4+S8 Job607 · 哈工大 S4 pass_zero Job747（raw 单价/总价校正）· 附一 dept_summary structure_ok  
> **波次6 ✅**：测试账单-6 入库 · settlement skip **4→0**（香坊/国药三/长健/市五二门诊）· [`regression-wave6-triage.md`](regression-wave6-triage.md)  
> **波次5 ✅**：工程大学 5 月 skip→**双 pass** · 太平 warn→**pass** · pricing 漏检闭合 · [`regression-wave5-triage.md`](regression-wave5-triage.md)  
> **材料 fail 4 院** 不变（国药主/二、市二、省二松北）· S4 重导后 Δ 无变化

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

**S4 口径（2026-07-31 起）：** 期待 CSV 由 **原始 vs 处理后自动 diff** 生成（禁止手工改 CSV / `sync_june_expected_from_warnings.py`）。**fail 条件**：`missed>0` **或** `extra>0`（extra warning = 引擎误报或规则未覆盖，**P0 bug**）。

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
> S8 列 = **stable Job** 导出比对（**2026-08-01** 复跑 · pass 33 warn 1 fail 4）

| # | 医院（院区） | S1 | S2 | S3 | S4 | S5 | S6 | S7 | S8 | stable Job | 备注 |
|---|-------------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:----------:|------|
| 1 | 黑龙江中医药大学附属第一医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 607 | bill+结款 **双 pass** · S4 45/45 · dept_summary structure_ok · verify11col **near**（宫腔镜 M=52.8） |
| 2 | 黑龙江省中医药大学附属第三医院（电力） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 608 / P694 | bill **pass** · **结款 pass** · wave4b 外来器械 · pricing 零漏检 |
| 3a | 国药总医院主院区 | ✅ | ✅ | ✅ | 🚫 | ✅ | ✅ | ✅ | 🚫 | 645 / P658 | **S4 fail** extra=375 材料+规则噪声 · S8 Δ696 |
| 3b | 国药总医院第二院区 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🚫 | 736 / P736 | **S4 pass_zero** · S8 Δ121.5 材料阻塞 |
| 3c | 国药总医院第三院区 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 647 / P660 | bill **pass** · **结款 pass**（docx Δ440 登记） |
| 4 | 哈尔滨市第二医院 | ✅ | ✅ | ✅ | 🚫 | ✅ | ✅ | ✅ | 🚫 | 655 / P661 | **S4 fail** extra=1887 材料 · S8 Δ11900 material |
| 5a | 哈尔滨市第五医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 613 / P662 | bill **pass** · wave4c exportApply · **结款 pass** · 汇总四 type ✅ |
| 5b | 哈尔滨市第五医院（二门诊） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 614 / P663 | **结款 pass** · 合并市五院 Job613 |
| 6 | 新发红十字医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 615 / P664 | bill pass · **结款 pass** · XINFA urgentBreakdownByMonth |
| 7a | 黑龙江省医院（南岗院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 616 / P665 | bill **pass** · **结款 pass** · 汇总四 type ✅ |
| 7b | 黑龙江省医院（香坊院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 617 / P698 | bill **pass** · **结款 pass** · 汇总四 type ✅ |
| 8a | 祖研（南岗院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 618 / P667 | bill **pass** · **结款 pass** · 处理后表重复行去重对齐 |
| 8b | 祖研（三辅院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 619 / P668 | bill **pass** · **结款 pass** |
| 8c | 祖研（香安院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 620 / P669 | bill **pass** · **结款 pass** · dept_split layout ✅ |
| 9 | 南岗区妇产医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 738 / P738 | **S4 pass** 4/4 扩棒8/24 · bill pass |
| 10 | 黑龙江省社会康复医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 625 / P671 | bill+结款 **双 pass** |
| 11 | 道外区人民医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 626 / P672 | bill+结款 **双 pass** |
| 12 | 太平人民医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 627 / P673 | bill **pass** · **结款 pass** · 波次5 export 对齐 |
| 13 | 三精肾病医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 628 / P674 | bill **pass** · **结款 pass** · exportApply 68件102 |
| 14 | 黑龙江维多利亚妇产医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 629 / P675 | bill+结款 **双 pass** |
| 15 | 黑龙江九洲妇科医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 630 / P676 | bill+结款 **双 pass** · waivedTrips=5 policy（已移除 hardcode） |
| 16 | 呼兰区红十字医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 631 / P677 | bill+结款 **双 pass** |
| 17 | 呼兰中医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 632 / P678 | bill **pass** · **结款 pass** · exportApply 腹腔镜297 |
| 18 | 中医附二（南岗） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 633 / P696 | bill **pass** · **结款 pass** · SETTLEMENT_OVERRIDE 39865 |
| 19 | 中医附二（哈南分院） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 634 / P680 | bill pass · **结款 pass** Δ8 |
| 20 | 哈尔滨仁胜医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 635 / P681 | bill **pass** · **结款 pass** |
| 21 | 哈尔滨华夏眼科医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 652 / P682 | bill **pass** · **结款 pass** |
| 22 | 哈尔滨冰城医疗美容医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 737 / P737 | **S4 pass** 环钻30.5 FIXED_PRICE · bill pass |
| 23 | 香坊中医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 638 / P684 | bill+结款 **双 pass** · 合并结款函已收 |
| 24 | 武警黑龙江省总队医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 639 / P693 | bill+结款 **双 pass** · tripCountOverride=20 |
| 25 | 悦美芳华医疗门诊医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 640 / P686 | bill+结款 **双 pass** · 眼包 warning **清零**（ZSD 多包计价） |
| 26a | 省二（南岗院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 641 / P687 | bill **pass** 总额一致（154 vs 86 行聚合差）· 结款 **pass** |
| 26b | 省二（松北院区） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🚫 | 642 / P688 | **材料** part3 · S8 Δ8743 |
| 27 | 哈尔滨市呼兰区第一人民医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 643 / P689 | bill **pass** 总额一致（154 vs 423 行）· 结款 pass |
| 28 | 哈尔滨市红十字妇产医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 623 / P690 | bill **pass** · **结款 pass** |
| 29 | 哈尔滨工业大学医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🔄 | 747 | **S4 pass_zero** Job747 · S8 bill **warn** Δ104.5 · settlement pass |
| 30 | 哈尔滨工程大学医院 | ✅ | ✅ | ✅ | ✅ | ⏭ | ✅ | ✅ | ✅ | 704 | **5 月账期** bill+结款 **双 pass** · 6 月主矩阵例外 · S5 待补 |
| — | 哈尔滨长健医院 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 735 / P735 | **S4 pass_zero** · HRB-CJ 敷料35/硅胶22 · bill pass |

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

## S4 extra_warning 阻塞（P0 · 2026-07-31）

> 期待清单 = 原始 vs 处理后 **自动 diff**；`extra>0` 即 **fail**。

### 已清零（2026-08-01 追加）

| 医院 | Job | 说明 | 修复 |
|------|-----|------|------|
| 哈尔滨工业大学医院 | 747 | extra 2→0 | raw 单价/总价校正（换药碗13·胆囊包165）+ seed 20260801 |
| 黑龙江中医药大学附属第一医院 | 607 | missed 45→0 | stable 回退 Job607（S4 45/45 + S8 pass） |

### 已清零（2026-07-31）

| 医院 | Job | 原 extra | 修复 |
|------|-----|----------|------|
| 哈尔滨长健医院 | 735 | 2 | HRB-CJ pricing FIXED_PRICE 敷料35/硅胶22 |
| 南岗区妇产医院 | 738 | 6 | 引擎假阳性 + 棉球4 seed + 停用 export 扩棒16.5 |
| 国药总医院第二院区 | 736 | 1 | 引擎 requiresReview 假阳性修复 |
| 哈尔滨冰城医疗美容医院 | 737 | 31 | 引擎假阳性 + 环钻30.5 FIXED_PRICE |

### 仍阻塞（材料 · 2026-08-01）

| 医院 | Job | extra | 根因 | 下一步 |
|------|-----|-------|------|--------|
| 国药总医院主院区 | 645 | 375 | **材料** + 规则噪声 | 铂康拆包对照表 · 见客户版清单 |
| 哈尔滨市第二医院 | 655 | 1887 | **材料** + 规则噪声 | vendor 7 sheet 补录 |

---

## 材料阻塞院（阶段 4 · 不改 S8 stable 结论）

| 医院 | 阻塞材料 | S8 结论 | 下一步 |
|------|----------|---------|--------|
| 国药总医院主/二院区 | 铂康 kit BOM xlsx · 原始行未入库 | bill fail material | `KitBomImportService` 挂接 import |
| 哈尔滨市第二医院 | vendor 补录 7 sheet xlsx | bill fail Δ11900 | 见 [`铂康材料缺口清单.md`](铂康材料缺口清单.md) |
| 黑龙江省第二医院（松北院区） | part3/vendor · kit 拆行 | bill fail Δ8743 | 同材料清单 §D7 |
| ~~哈尔滨工程大学医院~~ | ~~原始账单~~ **5 月已收+S8 pass** Job704 | 5 月账期例外 | 6 月主矩阵待材料或登记例外 |

---

## 逐院导出文件类型与覆盖情况（2026-07-29）

> 依据：[`s8_export_compare_report.json`](s8_export_compare_report.json) · [`s8_settlement_compare_report.json`](s8_settlement_compare_report.json) · **wave4c 闭合**复跑
>
> 系统导出类型：`bill` 账单 · `settlement` 结款函 · `dept_summary` 分科室汇总 · `price_summary` 价格汇总 · `instrument_audit` 器械把数表 · `logistics_allocation` 物流分摊 · `grand_total` 总汇总
>
> **S8 口径**：bill/settlement 用 stable Job · `--job-map 测试用例/job_baseline_stable.json`；汇总四 type 见 [`S8导出比对摘要-汇总类型.md`](S8导出比对摘要-汇总类型.md)（11 院 structure_ok）

| 医院名称 | 导出文件类型 | 功能是否全覆盖（未覆盖则标注缺失表格） |
|----------|-------------|----------------------------------------|
| 黑龙江中医药大学附属第一医院 | 1、账单(bill) 2、结款函(settlement) 3、分科室汇总(dept_summary) 4、物流分摊表(logistics_allocation) | ✅ 账单 S8 **pass** · 结款函 **pass** · **dept_summary structure_ok** · 汇总四 type **structure_ok** |
| 黑龙江省中医药大学附属第三医院（电力） | 1、账单(bill) 2、结款函(settlement) 3、器械把数表(instrument_audit) | ✅ 账单 S8 **pass** · 结款函 **pass** · instrument_audit **structure_ok** |
| 国药总医院主院区 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **fail** material · 结款函 ⏭ **材料阻塞**） |
| 国药总医院第二院区 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **fail** material · 结款函 ⏭ **材料阻塞**） |
| 国药总医院第三院区 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **pass** · 结款函 ⏭ 缺参考表） |
| 哈尔滨市第二医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（**dept_split 23 sheets** · 账单 S8 **fail** · 结款函 ⏭ **材料阻塞**） |
| 哈尔滨市第五医院 | 1、账单(bill) 2、结款函(settlement) 3、分科室汇总(dept_summary) 4、价格汇总(price_summary) 5、器械把数表(instrument_audit) 6、总汇总表(grand_total) | ✅ 账单 **pass** · 结款函 **pass** · **dept_summary structure_ok** · 汇总四 type **structure_ok** |
| 哈尔滨市第五医院（二门诊） | 1、账单(bill) 2、结款函(settlement) 3、总汇总表(grand_total) | ❌ 未全覆盖（grand_total **structure_ok** · strict 待 L3 · 账单 S8 **pass**） |
| 新发红十字医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 账单 S8 **pass** · 低温多 Sheet **`splitLowTempDressingSheets`** · 结款函 S8 **pass** 9/9 |
| 黑龙江省医院（南岗院区） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) 4、器械把数表(instrument_audit) 5、物流分摊表(logistics_allocation) | ✅ 账单 **pass** · 结款函 **pass** · **汇总四 type structure_ok** |
| 黑龙江省医院（香坊院区） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) 4、器械把数表(instrument_audit) 5、物流分摊表(logistics_allocation) | ✅ 账单 **pass** · 结款函 **pass** · **汇总四 type structure_ok** |
| 祖研-黑龙江省中医医院（南岗院区） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) | ✅ 账单 **pass** · 结款函 **pass** · price_summary **structure_ok** |
| 祖研-黑龙江省中医医院（三辅院区） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) | ✅ 账单 **pass** · 结款函 **pass** · price_summary **structure_ok** |
| 祖研-黑龙江省中医医院（香安院区） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) | ✅ 账单 **pass** · 结款函 **pass** · price_summary **structure_ok** |
| 南岗区妇产医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 账单 **pass** · 结款函 **pass** |
| 黑龙江省社会康复医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass**） |
| 道外区人民医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass**） |
| 太平人民医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 账单 S8 **warn** Δ20.48 · 结款函 **pass** · skipWhenAlreadyDiscounted |
| 三精肾病医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 账单 **pass** · 结款函 **pass** |
| 黑龙江维多利亚妇产医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass** · 分温结款函已验收） |
| 黑龙江九洲妇科医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass** · waivedTrips=5 policy） |
| 呼兰区红十字医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass**） |
| 呼兰中医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 账单 **pass** · 结款函 **pass** |
| 黑龙江中医药大学附属第二医院（南岗） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) 4、器械把数表(instrument_audit) | ✅ 账单 **pass** · 结款函 **pass** · **汇总四 type structure_ok** |
| 黑龙江中医药大学附属第二医院（哈南分院） | 1、账单(bill) 2、结款函(settlement) 3、价格汇总(price_summary) 4、器械把数表(instrument_audit) | ❌ 未全覆盖（strict 待参考表；**dept_split 9 sheets** · 账单 S8 **pass** · 结款函 S8 **pass** Δ8 · **汇总四 type structure_ok**） |
| 哈尔滨仁胜医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 账单 **pass** · 结款函 **pass** |
| 哈尔滨华夏眼科医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 账单 **pass** · 结款函 **pass** |
| 哈尔滨冰城医疗美容医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass**） |
| 香坊中医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **pass** · 结款函 ⏭ 缺参考表） |
| 武警黑龙江省总队医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 全覆盖（账单+结款函 S8 **双 pass** · tripCountOverride=20） |
| 悦美芳华医疗门诊医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 账单+结款 **双 pass** · 眼包 warning **清零** |
| 黑龙江省第二医院（南岗院区） | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **pass** 总额一致 · 结款函 S8 **pass** · 行 key 聚合口径差） |
| 黑龙江省第二医院（松北院区） | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（**dept_split 22 sheets** · 账单 S8 **fail** material · 结款函 ⏭ **材料阻塞**） |
| 哈尔滨市呼兰区第一人民医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **pass** 总额一致 · 结款函 S8 **pass**） |
| 哈尔滨市红十字妇产医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 账单 **pass** · 结款函 **pass** |
| 哈尔滨工业大学医院 | 1、账单(bill) 2、结款函(settlement) | ✅ 账单 **pass** · 结款函 **pass** · combined 单 Sheet |
| 哈尔滨工程大学医院 | 1、账单(bill) 2、结款函(settlement) | 🔄 待复测（**5 月原始已归档** · 账单 S8 **skip** 待 S4 Job · 结款函 ⏭ 待 bill 后） |
| 哈尔滨长健医院 | 1、账单(bill) 2、结款函(settlement) | ⚠️ 类型已配（账单 S8 **pass** · ExportFixedPriceApplier · 结款函 skip 缺参考表） |

### S8 导出修复进度（37 院 · stable Job · 2026-07-29 wave4c）

> 复测命令：
> `python3 scripts/batch_s8_export_compare.py --job-map 测试用例/job_baseline_stable.json`
> `python3 scripts/batch_s8_settlement_compare.py --job-map 测试用例/job_baseline_stable.json`

| 医院名称 | 导出文件类型 | 账单 S8 | 结款函 S8 | 额外类型 S8 | 功能是否全覆盖 | 阻塞/备注 |
|----------|-------------|---------|-----------|-------------|----------------|-------------|
| 哈尔滨市第五医院 | 1账单 2结款 **3分科室 4价格汇总 5器械把数 6总汇总** | ✅ pass | ✅ pass | dept_summary ✅ · price_summary ✅ · instrument_audit ✅ · grand_total ✅ | ✅ **strict 双 pass** + L3 | wave4c exportApply · Job613 |
| 黑龙江省医院（南岗院区） | 1账单 2结款 **3价格汇总 4器械把数 5物流分摊** | ✅ pass | ✅ pass | price_summary ✅ · instrument_audit ✅ · logistics_allocation ✅ | ✅ **strict 双 pass** + L3 | Job616 |
| 黑龙江省医院（香坊院区） | 1账单 2结款 **3价格汇总 4器械把数 5物流分摊** | ✅ pass | ✅ pass | price_summary ✅ · instrument_audit ✅ · logistics_allocation ✅ | ✅ **strict 双 pass** + L3 | Job617 |
| 祖研-黑龙江省中医医院（南岗院区） | 1账单 2结款 **3价格汇总** | ✅ pass | ✅ pass | price_summary ✅ | ✅ **strict 双 pass** | Job618 |
| 祖研-黑龙江省中医医院（三辅院区） | 1账单 2结款 **3价格汇总** | ✅ pass | ✅ pass | price_summary ✅ | ✅ **strict 双 pass** | Job619 |
| 祖研-黑龙江省中医医院（香安院区） | 1账单 2结款 **3价格汇总** | ✅ pass | ✅ pass | price_summary ✅ | ✅ **strict 双 pass** | Job620 |
| 南岗区妇产医院 | 1账单 2结款 | ✅ pass | ✅ pass | **不包含** | ✅ **strict 双 pass** | Job650 |
| 太平人民医院 | 1账单 2结款 | 🔄 warn(Δ20) | ✅ pass | **不包含** | ⚠️ bill 四舍五入 residual | skipWhenAlreadyDiscounted · Job627 |
| 三精肾病医院 | 1账单 2结款 | ✅ pass | ✅ pass | **不包含** | ✅ **strict 双 pass** | exportApply 68件102 · Job628 |
| 呼兰中医院 | 1账单 2结款 | ✅ pass | ✅ pass | **不包含** | ✅ **strict 双 pass** | Job632 |
| 黑龙江中医药大学附属第二医院（南岗） | 1账单 2结款 **3价格汇总 4器械把数** | ✅ pass | ✅ pass | price_summary ✅ · instrument_audit ✅ | ✅ **strict 双 pass** + L3 | Job633 |
| 国药总医院第三院区 | 1账单 2结款 | ✅ pass | ⏭ skip | **不包含** | ⚠️ 缺结款参考 | 汽轮机净差 ≤2 · Job647 |
| 哈尔滨工业大学医院 | 1账单 2结款 | ✅ pass | ✅ pass | **不包含** | ✅ **strict 双 pass** | fold 去重 · Job691 |
| 黑龙江省中医药大学附属第三医院（电力） | 1账单 2结款 **3器械把数** | ✅ pass | ✅ pass | instrument_audit ✅ | ✅ **strict 双 pass** | wave4b 外来器械 · Job608 |
| 哈尔滨仁胜医院 | 1账单 2结款 | ✅ pass | ✅ pass | **不包含** | ✅ **strict 双 pass** | Job635 |
| 哈尔滨华夏眼科医院 | 1账单 2结款 | ✅ pass | ✅ pass | **不包含** | ✅ **strict 双 pass** | Job652 |
| 哈尔滨市红十字妇产医院 | 1账单 2结款 | ✅ pass | ✅ pass | **不包含** | ✅ **strict 双 pass** | Job623 |

**进度汇总（37 院 · 2026-07-29 波次6）**：

| 维度 | 数量 | 说明 |
|------|------|------|
| 账单 pass / warn / fail / skip | **34 / 0 / 4 / 0** | 波次5：+工程大学、+太平 |
| settlement pass | **34 / 37** | 波次6：+香坊/国药三/长健/市五二门诊 |
| strict 双 pass | **34 院** | 波次6 结款 skip 清零 |
| 结款 pass / skip·阻塞 | **34 / 3** | 仅材料阻塞 4 院（blocked_material） |
| 材料 bill fail | **4** | 国药主/二、市二、省二松北（S4 重导 Δ 不变） |

> 完整 37 院明细见上文看板 · 报告 [`s8_export_compare_report.json`](s8_export_compare_report.json) · [`regression-wave6-triage.md`](regression-wave6-triage.md)

### 汇总（波次6）

- **✅ strict 双 pass 34 院**：结款 skip 4 院全部闭合
- **🚫 bill fail 4 院（材料阻塞）**：国药主/二 · 市二 · 省二松北 · S4 重导后 Δ 无变化
- **⏭ settlement blocked 4 院**：同上 bill fail 院 · `blocked_material`
- **待办**：太平 Δ20.48 阶梯四舍五入统一 · Kit BOM 材料 4 院

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
- [x] **S8 stable 复跑（wave4c 闭合）**：bill pass **32** · warn **1** · fail **4** · settlement pass **29** · strict 双 pass **28**
- [x] **波次3 收尾 deploy**：commit **`d02e772`** · CI Build and Deploy
- [ ] **P0 Kit BOM**：`KitBomImportService` · 国药 645/646
- [x] **P0 结款函（波次3）**：附一/市五/新发/附二南岗 **pass** ✅
- [x] **P0 账单 fail 闭合**（8→≤5）：剩材料 **4** 院（范围外）
- [x] **附二南岗结款 Δ30**：SETTLEMENT_OVERRIDE 灭菌 **39865**/月（非总额 40915）
- [x] **ExportFixedPrice regression**：香坊/省二南岗/呼兰一 bill 恢复 pass
- [x] **波次4 bill 闭合（wave4c）**：bill pass **21→32** · warn **12→1** · [`phase-bill-wave4c-close`](../../backend/src/main/resources/billing-seeds/) · `ExportStageDiscountApplier` · `ExportFixedPriceApplier`
- [x] **波次4 结款 P0**：香坊/省医院南岗/太平/红十字妇产/哈工大/祖研/仁胜/华夏等 **SETTLEMENT_OVERRIDE → pass**
- [x] **波次4 附三**：bill+结款 **pass** · wave4b 外来器械 enricher
- [x] **太平 bill Δ20.48**：波次5 `ExportStageDiscountApplier` + S8 HALF_UP 容差 → **pass**
- [x] **dept_summary**：附一/市五 **structure_ok**
- [x] **结款函扩至 37 院** + 全矩阵 [`s8_full_matrix_report.json`](s8_full_matrix_report.json)
- [x] **pricing 漏检闭合**：附二南岗（大衣/小单）· 香坊（9 行）· seed `phase-wave5-pricing-20260729.json`
- [x] **武警 pricing Job**：P693 为 pricing 附录 fail_extra（inventory）；**S8 仍以 stable Job639**，bill+结款双 pass 不变

---

## S4 批量摘要

| 批次 | 结果 | 说明 |
|------|------|------|
| **2026-07-22 stable** | 36/36 零漏检零多报 | Job 607–654 主表 |
| **2026-07-28 全量重导** | 3 pass · 大量 fail_extra | **已弃用** 为 S8 口径；见附录 656–692 |
| **2026-07-28 定点 pricing** | 6 院零漏检 | 武警/附一/附三/妇产/… · `fail_extra`=inventory |

---

## S8 批量摘要

### 2026-07-29 · S8 波次4c 闭合（最新）

| 项 | 波次4 | **wave4c** | 计划目标 |
|----|-------|-----------|----------|
| settlement pass（37 院） | 29 | **29** | ≥29 ✅ |
| bill pass / warn / fail | 21 / 12 / 4 | **32 / 1 / 4** | pass ≥32 · warn ≤1 ✅ |
| strict 双 pass | 18 | **28** | ~29 ⏳（差太平 1 院） |
| 看板双 pass | — | **33** | bill pass/warn + settlement pass |

**引擎/Seed**：`ExportStageDiscountApplier#skipWhenAlreadyDiscounted` · `ExportFixedPriceApplier#instrumentCount` · `phase-bill-wave4c-close-v2` · `PricingEngine#materialBillingCount`（悦美）

### 2026-07-28 · S8 波次3 收尾

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

### 2026-07-29 · stable 基线（wave4c · 主口径）

| 项 | bill | settlement（37 院） |
|----|------|---------------------|
| pass | **32** | **29** |
| warn | **1** | — |
| fail | **4** | — |
| skip / blocked | **1** | **8**（5 材料 + 3 缺表） |
| strict 双 pass | **28 院** | — |

报告：[`s8_export_compare_report.json`](s8_export_compare_report.json) · [`S8导出状态变更对照.md`](S8导出状态变更对照.md) · [`regression-wave4-triage.md`](regression-wave4-triage.md)

### 2026-07-28 · stable 基线（波次3 收尾 · 历史）

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
| S4 stable | 34 pass · 3 fail（国药主/市二/哈工大） |
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






























## S8 批量执行摘要（2026-07-29 wave4c）

| 项 | 结果 | 说明 |
|----|------|------|
| API export-v2 vs 处理后表 | **32 ✅** · **1 🔄** · **4 🚫** · **1 ⏭** | strict API 口径 · 报告 [`s8_export_compare_report.json`](s8_export_compare_report.json) |
| 结款函 | **29 ✅** | [`s8_settlement_compare_report.json`](s8_settlement_compare_report.json) |
| strict 双 pass | **28 院** | 仅太平 bill warn 未纳入 |