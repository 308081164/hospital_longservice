# 账单系统交接：未完成与待决策清单

> **2026-10-28 状态更新（接手后请先读这段）**
>
> 本清单撰写时 tip ≈ `675d9f61`；此后 main 已推进，以下事项已闭环，**不要再重复劳动**：
>
> | 原条目 | 现状 |
> |---|---|
> | §1.1 生产验收报告缺失 | ✅ 已补：[`docs/authority-1027-prod-20261027.md`](authority-1027-prod-20261027.md) |
> | §1.2 市二 215–216 内热针（待财务确认） | ✅ 已上线：`unit_price` 落库 + 两条 FOLD 规则 + `FoldUnitPricePricingTest` |
> | §1.2 骨伤 243 钢针「(件数-1)/6」 | ✅ 已上线：新增 `foldOffset` 能力 + 规则 + `GushangSteelNeedlePricingTest`（8 件→19.3、1 件→8.3） |
> | §2.1 敷料包「保留睿思原价」 | ✅ 用户已拍板**按标准价改**；已移除 preserve 早返回并补 `敷料包(低温等离子)` 类型映射 |
> | parity 长期 ok:false | ✅ 根因是仓库内 2026-09-23 静态文件被反复上传；已改为每次现场生成，45/45 通过 |
> | G7 白名单 4 条 | 收敛为 **1 条**（骨伤 251 人工核对） |
>
> 仍未完成：§1.2 其余引擎缺口（骨伤 241 / 230–238 / 247–249、通用 24–25、市二 213），已立项见
> [`docs/engine-gap-roadmap-20261028.md`](engine-gap-roadmap-20261028.md)；请以该文档排期，不要擅自当需求做。

日期：2026-10-27（北京时间）  
用途：本 Project 协调方已按用户要求**停止全部在跑任务**；下文只列**尚未完成**、以及**用户尚未拍板**的事项，方便其他 Agent 接手。  
仓库：`308081164/hospital_longservice`  
生产：`http://39.102.213.51:8853`  
协调方 Project store：`/cursor/stores/bc-3fabfa38-7034-44d3-a6ab-c15e75f34211`（Cursor Context；不在本仓库内）

---

## 0. 立刻生效的约束

1. **全部在途 Agent 任务已停**，不要自动续跑旧 worker（`October authority rules`、`30cm price retest`、`Dressing price preserve` 等）。新工作请开新 Agent，以本文 + `main` 为准。
2. 计费测试两套口径**禁止混用**（见 `.cursor/rules/billing-test-paths.mdc`）：
   - 路径 A：特殊计价医院 Excel 严格对账（`STRICT_KEEP_CODES`、锁定基线月、`特殊计价严格对账报告-*`）
   - 路径 B：billing-seed / 规则同步（`verify-billing-seed.sh`、`billing-seed-*`、`rules compare`）
3. 规则变更遵守 `docs/计费规则迁移与验收规范.md`：G1→G2→G3→G4；另有 G7（Excel↔baseline 全覆盖，白名单在 `scripts/known_rule_gaps.json`）。
4. 不要整文件 Read 巨型 `测试用例/*.json`；用脚本/`jq` 摘要。

---

## 1. 未完成（代码可能已合，验收/报告未完）

### 1.1 【优先】10.27 内勤 + 20261026 特殊收费：生产验收未闭环

| 项 | 状态 |
|----|------|
| 权威 Excel 入库 | **已在 `main`**：`docs/source/医院内勤规则10.27.xlsx`、`docs/source/特殊收费20261026.xlsx`（索引见 `docs/source/README.md`） |
| 落库 PR | [#28](https://github.com/308081164/hospital_longservice/pull/28) `d40ac483` 曾合入；其后有过 revert / restore；请以**当前 `origin/main` tip** 为准（撰写时 tip ≈ `675d9f61`，含 restore #28/#30/#31 与后续修复） |
| 太平把数区间 + 按包一口价 | [#30](https://github.com/308081164/hospital_longservice/pull/30) `19b4ed06`（后经 revert/restore） |
| 太平 30cm 低温纸塑认 300mm | [#31](https://github.com/308081164/hospital_longservice/pull/31) `e24b5e1c`（后经 revert/restore） |
| 生产验收报告 | **未写出**：约定路径 `docs/authority-1027-prod-20261027.md`（store 里也没有同名文件） |
| 用户验收标准 | 「落库并推生产；完成后执行测试保证全部生效才算通过」——**尚未对用户宣布通过** |

**接手必须做完：**

1. 确认生产 `gitSha` 是否包含当前 `main` 上权威更新与 #30/#31 等价修复（不要假设旧 tip `e24b5e1c` 仍是生产）。
2. 对**相对旧权威的每条变更**做生产验证（simulate 或不落库重算），至少覆盖：
   - 太平：账单整段重写（含 16.5→8.91、N=1 宽档、N>2 且≠16.5 的 75 折、低温把数区间）；**材料「低温纸塑袋300*400」规则单价应为 26.3，不能是 22.5**（历史账单任务曾用 1583，以生产现有太平 9 月单为准，**不要改客户账单**）。
   - 附一：＜3 件系统价 75 折；敷料无纺布 ×0.999；换药包加价 **5.49**（不是 5.5）。
   - 市二：产包并入「件数×5.5＋8」。
   - 回归抽查：香坊放疗科 W12050、附一冲洗头、G3 `rules compare --all --fail-on-drift` 零错误零警告。
3. 写中文报告（建议仍用 `docs/authority-1027-prod-20261027.md` 或同级新名），写清：新文件 SHA、相对旧权威增改删、生产 gitSha、每条验证数字、表达不了的条目。
4. **未通过不得写成通过**；继续修→PR→部署→复测。

**相关后续已合进 main、接手时需一并理解：**

- `4df44e1d`：baseline sync 补齐 `standardPricingOverride` 落库（附一纸塑阶梯/13.2 封顶曾因库中 override 为 null 不生效）。
- `1400968e`：G7 Excel→baseline 闸门 + `scripts/known_rule_gaps.json` 历史欠账白名单。
- `675d9f61`：一次性生产诊断 workflow（注释写明仅临时分支意图；是否保留以仓库现状为准）。

Store 草稿脚本（未完成验收）：  
`/cursor/stores/bc-3fabfa38-7034-44d3-a6ab-c15e75f34211/internal/authority-1027-prod-verify.py`

---

### 1.2 引擎「仍不能整句表达」的特殊收费（已登记，未承诺实现）

来源：`/cursor/stores/.../docs/special-pricing-prod-all-20261026.md`（2026-10-26 生产全量核对，对应 PR [#27](https://github.com/308081164/hospital_longservice/pull/27) 前后口径）。  
数字口径（当时）：可表达 262 / 仍不能整句表达 **17**。仓库白名单另见 `scripts/known_rule_gaps.json`（条目更少，侧重 G7）。

**仍不能整句表达（不要伪装已生效）：**

| 范围 | 内容 |
|------|------|
| 骨伤 241 | 外架钢针 >6：按 6 进位加袋；试算未走出「/双+0.3」路径 |
| 骨伤 242 | 钢针减扳手：(件数-1)/6 进位等；引擎无此句 |
| 骨伤 230–238 | 克氏针双层袋再加 0.3：底价可命中，**/双 再加 0.3 未生效** |
| 骨伤 247–249 | 钉盒按组件拆；引擎无组件拆分 |
| 通用 24–25 | 骨科租赁篮筐/钉拆分；引擎无篮筐计数与组件拆分 |
| 市二 213 | 「/双」低温硬编码在 `PricingEngine.isHrb2ndHospital`，样例仍 28 不是 35；勿算进「manifest 已生效」 |

**G7 白名单（`scripts/known_rule_gaps.json`，状态均为待确认/待评估/人工）：**

- 市二 215–216：内热针 ≤10/>10 两档（待财务确认）
- 骨伤 243：钢针-几件 扳手1（待引擎能力评估）
- 骨伤 251：Excel 写明 AI 出不了、人工核对

**接手前问用户：** 是否要立项实现上述任一条；默认**不要**擅自当需求做。

---

## 2. 待用户决策（已查明原因，未改代码）

### 2.1 敷料包「保留睿思原价」——客户高健反馈「不对劲」

| 项 | 内容 |
|----|------|
| 状态 | **只排查，未改代码、未开 PR**；用户尚未说「按标准价改」 |
| 报告（store） | `/cursor/stores/bc-3fabfa38-7034-44d3-a6ab-c15e75f34211/docs/dressing-preserve-feedback-20261027.md` |
| 截图 | 同 store `media/dressing-preserve/{list,trace,wechat}.png` |
| 客户原意 | 不要「保留原价」；应按敷料包标准算价并与账单比对。所有医院敷料包都出现该标签 |

**结论摘要：**

- 「不对劲」= 规则价被写成账单原价，差额恒 0，路径 `preserve`，文案「敷料包保留睿思原价 / 不做包材核对 / 不再打折」。
- 自 `b8c9b280`（2026-10-12）起：`type.contains("敷料包")` 即提前返回；10-14 挪到内勤一口价之后；10-24 仅豁免附一 `ZYY-D1`。
- 覆盖：非附一且未命中敷料一口价的医院。市二截图四行必然 preserve。省二/呼兰七折、省医院六折对敷料行被跳过，与权威表冲突。
- 市二按通用敷料纸塑标准（＜20cm=2.5，≥20cm=4）：尿刷针应 −5.50、分娩包应 −14.00；消毒巾权威固定 15 但类型门是「敷料包（纸塑袋）」，账单是「敷料包(低温等离子)」对不上。
- **只删提前返回不够**：`敷料包(低温等离子)` 不在包类型对照表，引擎仍会未知类型保留原价。

**若用户拍板「按标准改」，改动入口（报告原文）：**

1. `ReconciliationPricingOrchestrator` 的 `isDressingPack` / `preserveDressingOriginal` / 附一豁免。
2. `pack-type-material-map.json` 补类型或映射到「敷料包（纸塑袋）」。
3. 市二消毒巾 15 元的类型门。
4. 单测：`otherHospitalDressingStillKeepsRuisiPrice`、`dressingPackKeepsOriginalPriceAtTwoHospitals`（方向需改）。

**不要**把所有含「敷料包」的行一律改成 2.5/4（无纺布 W 码、道外/三精/太平结算价、各院具名固定价另算）。

---

## 3. 已完成可参考（不要重做，除非用户新要求）

下列在 Project tracker 中已 Done；详细报告在 store `docs/`，多数对应已合并 PR：

| 主题 | PR / 报告线索 |
|------|----------------|
| 特殊收费生产全量核对 | [#27](https://github.com/308081164/hospital_longservice/pull/27)、store `special-pricing-prod-all-20261026.md` |
| 红十字括号不参与名称包含、一件环氧 22 | [#25](https://github.com/308081164/hospital_longservice/pull/25) |
| 成批补漏特殊收费 | [#22](https://github.com/308081164/hospital_longservice/pull/22) [#26](https://github.com/308081164/hospital_longservice/pull/26) |
| 仅消毒额外包类型 | [#21](https://github.com/308081164/hospital_longservice/pull/21) |
| 香坊放疗科 W12050 +5 | [#20](https://github.com/308081164/hospital_longservice/pull/20) |
| 老肯「等温」→「低温」+ 封顶 | [#19](https://github.com/308081164/hospital_longservice/pull/19) |
| 折扣/封顶步骤文案 | [#18](https://github.com/308081164/hospital_longservice/pull/18) |
| 棉布额外包类型 + 字段核对落库 | [#16](https://github.com/308081164/hospital_longservice/pull/16) [#17](https://github.com/308081164/hospital_longservice/pull/17) |
| 仁胜客户档案、结款函样式、松北 11 列等 | [#11](https://github.com/308081164/hospital_longservice/pull/11)–[#15](https://github.com/308081164/hospital_longservice/pull/15) |

**未获用户授权不要回退的行为：** 红十字括号匹配与一件一包 22；香坊棉布三档价；封顶 180 金额；W12050 只限放疗科且 skipDiscount 保持；老肯类型名「单包装包（老肯低温）」；其他医院敷料保留原价（除非用户按 §2.1 拍板改）。

---

## 4. 环境与操作备忘

- 部署：GitHub Actions「Build and Deploy」。
- CLI：`./bin/hospital-cli`（`rules compare`、simulate 等）；生产 profile 已有，**勿把密码写入仓库或报告**。
- SSH 生产机历史上用 root；私钥若在 store，复制后 `chmod 600`（store 挂载权限过宽会导致 OpenSSH 拒钥）。
- 权威源当前应以 `docs/source/README.md` 为准（10.27 / 20261026），不要再用 10.20 或 `特殊收费(4)-20261023` 当 G0。
- 10.27 表内南岗/香坊封顶句仍可能写「老肯等温」；系统类型保持「老肯低温」（README 已注明）。

---

## 5. 建议接手顺序

1. **先闭环 §1.1**（权威更新生产验收 + 写报告），这是用户明确要求「全部生效才算通过」且尚未交付的项。  
2. **问用户 §2.1**（敷料包是否按标准价改造）——未答复前不要改。  
3. **§1.2** 仅在用户点名某条规则时再立项。

---

## 6. 本清单维护

- 文件：`docs/agent-handover-open-items-20261027.md`  
- 写于停止全部任务之时；之后若有 Agent 完成 §1.1 / 用户拍板 §2.1，请更新本文件或另开日期版本，避免交接过期。
