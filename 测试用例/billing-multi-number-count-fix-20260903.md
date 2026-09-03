# 多数字包名件数推断修复报告（2026-09-03）

## 1. 用户报告的问题

平房区人民医院（PFQ-RM，低温灭菌）生产环境（39.102.213.51:8853，部署 commit 07d692f0）：

| 账期 | 包名 | 客户 | 系统（生产） | 判定 |
|------|------|------|--------------|------|
| 7月 | 腹腔镜下胆囊切除（戳卡4转换器1气腹针1）/Z1026 | 6件 110.00 | 2件 44.00 | 错误 |
| 7月 | 腹腔镜下胆囊切除（戳卡5转换器1）/Z1026 | 6件 110.00 | 6件 110.00 | 正确 |
| 4月 | 腹腔镜下胆囊切除（戳卡5转换器1气腹针1）/Z1026 | 7件 132.00 | 2件 44.00 | 错误 |
| 4月 | 腹腔镜下胆囊切除（戳卡5转换器1）/Z1026 | 6件 110.00 | 6件 110.00 | 正确 |

规律：以「气腹针N」结尾的多数字包名件数被算成 2（只数了 转换器1+气腹针1，丢了 戳卡4/5）。

## 2. 复现结论（重要：与预期不同）

**当前 main（07d692f0）本地对「无空白」包名算出正确结果 6件/110，无法复现生产的 2件/44。**

复现突破口：包名中「气腹」与「针」之间带**空白字符**时（如 `气腹 针1` 半角空格），本地精确复现 2件/44。推断生产账单中该包名含空白分隔（Excel 常见全角/半角空格），fb195881 的气腹针排除检查未兼容空白而失效。

同时证实 fb195881 遗留的潜在缺陷真实存在：针拆分触发时 `extractLastNumber` 只取末位数字。实例：`剪刀2止血钳1探针1`（低温）修复前算 **2件/44**（1+1），正确应为 **4件/88**（2+1+针折算1）。

## 3. 根因分析（代码路径）

文件：`backend/src/main/java/com/hospital/backend/service/PricingEngine.java`（行号为修复后版本）

1. **L210-211**：全局针数量拆分规则 `needleQtyPattern = 针(\d+)` 对 packName 逐匹配。
2. **L215-224（修复前）**：气腹针排除检查为 `packName.substring(matchStart-2, matchStart).equals("气腹")`——**「气腹」与「针」之间有任何空白即失效**，拆分规则误触发。
3. **L237-244（修复前）**：拆分触发后非针器械数 `nonNeedleCount = extractLastNumber(beforeNeedle) (+ extractFirstNumber(afterNeedle))`——只取**末位/首位**数字。对 `戳卡4转换器1|气腹 针1`：beforeNeedle 末位数字=1（转换器1，丢戳卡4），afterNeedle=空，针 1÷5 折算 1 → **1+1=2 件 → 低温 2×22=44 元**。这就是「2件/44」的完整成因。
4. 对照正确案例 `戳卡5转换器1`（无「针N」）：不进入针拆分块，走 `sumAllNumbers` 路径 5+1=6 件 → 110 元。这解释了「无气腹针的两数字包名计数正确」。

## 4. 共性问题排查（全量测试材料扫描）

脚本：`scripts/tmp_multi_number_scan.py`，扫描 `测试用例/` 全部 raw 材料。

**「≥2 个数字且含 气腹针N」形态**：真实材料中气腹针均为**无空白**独立包名（`气腹针-1/zXXXX`、`气腹针（高温）-1` 等，分布于国药主院、附一、省医院南岗/香坊、社会康复，共 5 院 11 处），**不存在**多数字+气腹针组合、也不存在气腹针在中间的实例；`气腹 针`/`气腹　针`（含空白）形态在全部材料中 **0 处**（生产形态本地无样本）。

**其他「多数字包名计数错误」形态**（针拆分可触发 = 含 `针\d+` 且针前有多数字段）：全部材料仅 3 个包名——

| 包名 | 所在医院 | 影响判定 |
|------|----------|----------|
| 剪刀2止血钳1探针1 | 祖研香安（ZUYAN-SF） | 高温 ≥3 件旁路，不进入拆分块，**不受修复影响** |
| （附一 2 个 W6050 多段名） | 黑龙江中医药大学附属第一医院 | 非严格对账院，修复后计数更准 |

结论：修复对严格对账基线（路径 A 26 院材料）**零影响面**。

## 5. 修复内容

`PricingEngine.java`（净 +14/-10，两处修复）：

- **FIX-1（多段求和）**：L239 `nonNeedleCount = sumAllNumbers(beforeNeedle)`（为 0 时回退 `extractLastNumber`，兼容「（5号）」等数字在汉字前的单段写法）；L244 针后段 `sumAllNumbers(afterNeedle)` 替代 `extractFirstNumber`；删除不再被引用的 `extractFirstNumber` 方法。`sumAllNumbers` 只计「中文紧接数字」段，天然排除 Z7537 等编码。
- **FIX-2（气腹针空白兼容）**：L218 改用新 helper `isVeressNeedleAt(packName, needleStart)`（L2137）：跳过「气腹」与「针」之间的任意空白字符（`Character.isSpaceChar`，含全角 U+3000）后再判定前缀。

语义保持不变：气腹针不参与 5 合 1 拆分（fb195881 语义）；无针包名路径完全未动。

## 6. 验证结果（全部本地完成）

### 6.1 单元测试（docker maven:3.9-eclipse-temurin-17）

`PricingEngineTest` 共 211 个测试：新增 8 个全部通过；11 个失败为早前提交遗留的过期断言，**与 HEAD 基线失败集合逐一相同**（用 `.head-check/head-baseline` worktree 跑 HEAD 代码对比确认，无新增、无消失）。

新增测试：`veressNeedleTailMultiSegmentPackCountsAllPieces`（戳卡4转换器1气腹针1=6件/110）、`veressNeedleTailSevenPiecesCharges132`（7件/132）、`veressNeedleInMiddleMultiSegmentPackCountsAllPieces`（气腹针在中间=7件/132）、`multiSegmentPackWithoutNeedleKeepsExcelCount`（戳卡5转换器1=6件/110 回归）、`veressNeedleHyphenFormNotSplit`（气腹针-1 不回归）、`needleSplitSumsAllSegmentsBeforeNeedle`（剪刀2止血钳1探针1=4件/88）、`needleSplitSumsSegmentsAfterNeedle`（针后多段求和）、`veressNeedleWithSpaceBeforeNeedleStillSkipped`（空白分隔仍跳过）。

### 6.2 API 模拟复测（本地后端 8088，PFQ-RM 真实字段）

用户 4 个包（`scripts/tmp_pfq_veress_count_repro.py`）：

| 包名 | 结果 |
|------|------|
| 戳卡4转换器1气腹针1 ×6 | **6件 110.00** ✓ |
| 戳卡5转换器1 ×6 | **6件 110.00** ✓（回归不变） |
| 戳卡5转换器1气腹针1 ×7 | **7件 132.00** ✓ |
| 戳卡5气腹针1转换器1 ×7（气腹针在中间） | **7件 132.00** ✓ |

FIX 案例（`scripts/tmp_multi_number_fix_verify.py`，4/4 PASS）：

| 案例 | 修复前 | 修复后 |
|------|--------|--------|
| 气腹␣针1（半角空格）×6 | 2件/44（复现生产现象） | **6件/110** ✓ |
| 气腹　针1（全角空格）×6 | 2件/44 | 件数推断 6 件 ✓；随后命中医院特色折算「针盒针5合1含包材」（exact_token 词边界语义，07d692f0 基线行为不变）→ 3件/66 |
| 剪刀2止血钳1探针1 ×4（低温） | 2件/44 | **4件/88** ✓ |
| 转换器1探针1 ×2（单段回归） | 2件/44 | **2件/44** ✓（不变） |

### 6.3 PFQ-RM 严格对账（路径 A）

命令：`special_v8_strict_excel_audit.py --hospital 平房区人民医院 --month 7/--month 4 --out-date 20260903`（本地直连 docker 模式 + 容器内端口；脚本 L722 硬编码 `mode="docker"`，--mode direct 不生效，属既有问题未动）。

- 本地材料仅有 7 月账期对，`--month 4` 按脚本回退规则落到同一 7 月材料，两次结果一致。
- 结果：`FAIL 多报 3；忽略非计价 warning 23 条 (E=0, W=26)`，报告：`测试用例/特殊收费v8严格Excel对账报告-20260903.{json,md}`。
- **气腹针/腹腔镜包不在任何差异清单**（本地 PFQ 材料经全量扫描不含此类包名，生产 4/7 月真实行的价格正确性由 6.2 API 复测覆盖）。
- 3 条多报均**与本次修复无关**（已逐条核实）：
  - `种植盒-36件 盒1/w9050`：2026-08-27 锁定基线中已存在（多报 1 就是它）；
  - `缝合针-2件/Z7520` ×9、×4：命中「平房人民针盒针5合1含包材」，该规则关键词「缝合针」由用户 09-02 种子 `phase-special-charge-needle-fold-keyword-fix-20260902.json`（commit 5e25e4f2）**有意添加**（种子 note 明确「命中 缝合针(5)/缝合针-2件/Z7520」）；静态上该包名无 `针\d+` 匹配，不进入本次改动的针拆分块，本修复不可能影响它。

## 7. 变更文件清单（未提交，待用户审查）

| 文件 | 说明 |
|------|------|
| `backend/src/main/java/com/hospital/backend/service/PricingEngine.java` | FIX-1 多段求和 + FIX-2 气腹针空白兼容（净 +14/-10） |
| `backend/src/test/java/com/hospital/backend/service/PricingEngineTest.java` | 新增 8 个单元测试（+152） |
| `scripts/tmp_pfq_veress_count_repro.py` | 临时：用户 4 包复测 |
| `scripts/tmp_multi_number_scan.py` | 临时：共性形态扫描 |
| `scripts/tmp_multi_number_fix_verify.py` | 临时：FIX 案例复测 |
| `测试用例/特殊收费v8严格Excel对账报告-20260903.{json,md}` | PFQ 7 月严格对账留档 |

遗留事项：
- `.head-check/head-baseline` git worktree（HEAD 基线对照用）删除被环境拦截，可由用户执行 `git worktree remove --force .head-check/head-baseline` 清理。
- 生产部署本修复后，建议用真实 4/7 月账单复测这 4 行确认 2件/44 消失（本地无空白形态样本，无法直接验证生产数据形态）。
