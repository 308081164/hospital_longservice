# 特殊收费关键词包含语义全量对齐 + manifest 生成器静默丢弃修复报告（2026-09-02 深夜）

## 背景

全量 Excel↔manifest 对账（`测试用例/excel-manifest-parity-audit-20260902.md`）结论：29 家中 28 家逐条 PASS，差距集中在人口 1 家 2 项实质差异 + 10 院 21 个「包名称带X」仍为 exact_token 的潜在风险关键词。本报告记录后续修复。

## 修复内容

### 1. 人口「水管膜片」关键词补落库（D1）

- **根因**：`phase-special-charge-12-sync-20260818.json` 的密封件补词补丁（setKeywords 含水管膜片）在 manifest 生成器中按字母序先于规则创建文件（`phase-special-v8-rules-20260814.json`，charge < v8）执行，被**静默丢弃**；Java 侧顺序本正确，但 reconcile 每次启动按 manifest 覆盖 DB，把生产拉回缺失状态。该补丁自 8-18 起丢失 15 天。
- **修复**：新种子 `phase-special-charge-keyword-contains-align-20260902.json` 将人口密封件规则对关键词定为 `[密封件@contains, 密封胶圈@contains, 水管膜片@contains]`。
- **口径说明**：12-sync 原补丁含「垫片」，按用户 2026-09-02 当日 Excel 版本（截图确认），垫片归属 5.5 折算组（见 `phase-special-charge-renkou-contains-fix-20260902.json`），故密封件组不含垫片。仓库内 `docs/source/` 各版 Excel 的人口①仍将垫片列入低温 ×22 组，系旧版副本，以用户当日版本为准。

### 2. 10 院 21 个「包名称带X」关键词 @contains 对齐

Excel 措辞均为「包名称带X」（包含语义），此前为 exact_token 词边界匹配，CJK 邻接变体包名会词中失配（先例：加长根管锉-6、胶帽组件-25件）。全部改为词级 `@contains`（规则级 keywordMatchMode 不变，仍管文本域）：

| 医院 | 规则 | 改后关键词（加粗为本次改 contains） |
|------|------|-----------------------------------|
| 方南南 FNN-YY | 小件5合1含/免包材 | P钻/根管锉/光滑针/机扩针 全部 @contains |
| 农大 NEAU-YY | 小件5合1含/免包材 | 根管针/机锉/牙探针 @contains（机扩针/车针/扩大针/拔髓针/探针/球钻为通用词保持 exact；根管锉已 @contains） |
| 松电 HRB-SD-MB | 机扩针5合1含/免包材 + 机扩针 5 件算 1 件 | 机扩针 @contains（其余 8 通用词保持 exact） |
| 航天风华 HRB-HTFH | 镍钛锉5合1含/免包材 | 镍钛锉 @contains |
| 电机厂 GUOYAO-2 | 指针5合1 ×3 | 指针 @contains |
| 道里妇幼 DL-FUCHAN | 棉花针/洗髓针5合1含/免包材 | 棉花针/洗髓针 @contains |
| 春语 CHUNYU-YL | 塑料管≤10按1件 / >10折算22 | 塑料管/管子 @contains |
| 监狱 HLJ-JYGLJ-YY | 密封件≤5按1件 / >5折算22 | 密封件/密封胶圈 @contains |
| 人口 HLJ-FY-RK | 针类组件/机扩锉/加长锉/洗髓针/彩色锉/手扩锉 规则对 | 各词 @contains |
| 祖研南岗 ZUYAN-NG | 排针10合1含/免包材 + 排针10盘算1件 | 排针 @contains |

已经 `scripts/tmp_keyword_gap_scan.py` 对全部真实账单包名复扫：**当前数据零行为变化**，仅消除未来变体包名失配风险。

### 3. manifest 生成器系统性修复（`scripts/billing_rules_manifest.py`）

- **硬编码规则插入移到二遍补丁应用之前**：Java 侧 HardcodedRulesMigrationRunner @Order(110) 先于 BillingSeedMigrationRunner @Order(115)，种子补丁可作用于硬编码规则；生成器此前顺序相反，导致 excel17-align 对「松电机扩针 5 件算 1 件」的 setKeywordMatchMode 补丁被静默丢弃（本次已补应用）。
- **延后名单扩充**：加入 `phase-special-charge-12-sync-20260818.json`（水管膜片历史补丁）与本种子；名单内按文件字母序追加、二遍按序应用，与 Java INCREMENTAL_SEEDS 顺序语义一致。
- **丢弃硬闸门**：`_apply_rule_update` 返回是否应用成功；所有丢弃补丁收集分级——目标规则在最终 manifest 中存在而补丁未应用 → **FATAL 构建失败**；目标不存在（Java 同样 no-op）→ WARN；10 条历史一致丢弃入基线（冰城整形包改名系列、呼兰 PDF 外来器械、航天风华旧规则名），基线外任何新丢弃立即失败。

## 验证

- `python3 scripts/billing_rules_manifest.py --write`：零 FATAL，hash `af072391…`；37/37 补丁断言全部落库。
- manifest diff 审查：仅预期关键词变化 + 松电硬编码规则补 keywordMatchMode，无任何意外变更。
- 后端重启后：`rules compare --all` 零漂移；水管膜片/变体包名 simulate 抽查；人口 7 月基线严格对账无新增差异（见下文验证记录）。

## 制度化

- 新增 `docs/计费规则迁移与验收规范.md`（根因分类 R1-R5、六步迁移流程、关键词判定表、G1-G5 验收闸门、禁止事项）。
- `.cursor/rules/billing-test-paths.mdc` 增加「规则变更守则」节，强制引用上述规范。
