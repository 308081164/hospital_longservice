# 铂康遗留资产深度分析

> 分析范围：仓库根目录 `铂康/` 下的生产数据转储、业务规则文档，并与当前 `backend/src/main/resources/schema.sql`、`PricingEngine.java` 硬编码审计结论交叉对照。  
> 文档性质：**遗留资产盘点 + 迁移/回归基线参考**；不含实现代码。  
> 编制日期：2026-07-08  
> 关联文档：[`iteration-refactoring-plan.md`](./iteration-refactoring-plan.md)、[`hardcoded-business-data-audit.md`](./hardcoded-business-data-audit.md)

---

## 1. 执行摘要

| 维度 | 结论 |
|------|------|
| 目录性质 | **生产 INSERT 转储 + 规则 Markdown**，非 DDL 建表脚本 |
| 文件数量 | **7 个文件**，**无 `.xlsx`** 实体（仅 job 元数据中的文件名） |
| 数据规模 | 417 job、580,915 row、328 export_log、8 pricing_rule |
| Schema | 旧库与新库均为 **9 表**，**无** `customer` / `product` 独立主数据表 |
| 生产客户 | **18+ 家**（超出代码审计 ~15 家） |
| 规则存储 | `rules_json` 含通用参数；`specialRules`（fixedPrices/foldRules）**不在 DB**，仍在 Java 兜底 |
| 回归价值 | 58 万行真实计价样本 + 5 个标杆 job，可用于 Phase 0–3 双轨验证 |

**核心建议：** Phase 1 客户种子采用**双源合并**——`PricingEngine` 硬编码（省二院等未出现在铂康 job 的客户）+ 铂康 `hospital_reconciliation_job` 去重（18+ 生产客户及别名）。

---

## 2. `铂康/` 目录清单

### 2.1 文件树（递归，共 7 文件）

```
铂康/
├── 灭菌计费规则.md                          # 业务规则文档（196 行）
└── 建表语句/                                # 实为 INSERT 转储，无 CREATE TABLE
    ├── hospital_pricing_rule.sql            # 8 条规则方案
    ├── hospital_reconciliation_job.sql      # 417 条对账作业
    ├── hospital_reconciliation_row.sql      # 580,915 行明细
    ├── hospital_reconciliation_export_log.sql  # 328 条导出记录
    ├── sys_user.sql                         # 4 用户
    └── sys_menu.sql                         # 6 菜单
```

### 2.2 各文件详情

| 路径 | 类型 | 规模 | 内容摘要 |
|------|------|------|----------|
| `铂康/灭菌计费规则.md` | 业务规则文档 | 196 行 | 高低温判定、小件折算、纸塑袋/无纺布/敷料/阶梯价、双袋规则、包装收费；与 `PricingEngine` + 前端 `pricingRules.ts` 默认高度一致 |
| `铂康/建表语句/hospital_pricing_rule.sql` | **INSERT 转储**（非 DDL） | 8 条 | id=8 标准模板 v2.0；id=53–61 按医院/客户名命名的 v1.0.0 方案 |
| `铂康/建表语句/hospital_reconciliation_job.sql` | INSERT 转储 | **417** 条 | 含 `source_file_name`、医院名、`rule_id`、多 sheet 元数据、`source_date_range` |
| `铂康/建表语句/hospital_reconciliation_row.sql` | INSERT 转储 | **580,915** 行 | 真实包名/材料/期望单价/`pricing_rule`/`notes_json` |
| `铂康/建表语句/hospital_reconciliation_export_log.sql` | INSERT 转储 | 328 条 | 账单/异常/结款函/**分科室汇总** 导出记录 |
| `铂康/建表语句/sys_user.sql` | INSERT 转储 | 4 用户 | admin、yang、admin001、qu001 |
| `铂康/建表语句/sys_menu.sql` | INSERT 转储 | 6 菜单 | 系统管理（用户/角色/菜单/API）+ 一级菜单占位 |

### 2.3 重要发现

1. **目录名误导**：`建表语句/` 下全部为 `INSERT INTO ... VALUES`，**无任何 `CREATE TABLE`**。导入前须先执行当前 `schema.sql` 建表。
2. **无 xlsx 实体**：仓库内不存在任何 `.xlsx` 测试文件；Excel 样本仅能从 job 的 `source_file_name`、`source_file_path` 推断文件名与上传路径。
3. **旧系统部署路径**（从 `source_file_path` 提取）：
   - `E:\佰创\代码\医院\invoice\backend-java\...`（早期）
   - `E:\佰创\代码\医院\第一版\backend\...`（如哈工大 job 324）
   - `E:\佰创\代码\医院\第二版\backend\...`（如胸科医院 job 362）
4. **`灭菌计费规则.md`** 可作为规则文档单一来源，建议在 Phase 0 纳入 `system_docs/` 正式引用链。

---

## 3. 旧库 vs 新库 Schema 对照

### 3.1 表集合对比

**结论：表集合一致。** 旧库转储与新 `backend/src/main/resources/schema.sql` 均为 **9 张表**，均**无** `customer`、`product`、`customer_alias` 等规划中的主数据表。

| # | 表名 | 旧转储 | 新 schema.sql |
|---|------|--------|---------------|
| 1 | `sys_user` | ✓ | ✓ |
| 2 | `sys_role` | —（转储未含） | ✓ |
| 3 | `sys_menu` | ✓ | ✓ |
| 4 | `sys_user_role` | — | ✓ |
| 5 | `sys_role_menu` | — | ✓ |
| 6 | `hospital_pricing_rule` | ✓ | ✓ |
| 7 | `hospital_reconciliation_job` | ✓ | ✓ |
| 8 | `hospital_reconciliation_row` | ✓ | ✓ |
| 9 | `hospital_reconciliation_export_log` | ✓ | ✓ |

### 3.2 逐表差异要点

| 表 | 差异要点 |
|----|----------|
| `sys_user` | 旧转储列：`id, username, email, password, created_at, updated_at`；新 schema 增 `is_active`、`is_superuser` |
| `sys_menu` | 旧系统菜单为通用 RBAC（系统管理 4 项 + 一级菜单占位），**无**「医院发货表/计费规则」菜单；与现 `DataInitializer.java` 初始化菜单不同 |
| `hospital_pricing_rule` | 列结构相同；旧数据中 `hospital_name`、`plan_name` **均为 NULL**，医院绑定靠 `name` 字段（如「哈尔滨市第五医院」） |
| `hospital_reconciliation_job` | 列一致；旧数据大量填充 `sheet_names` / `sheet_row_counts` / `sheet_warning_counts` / `source_date_range`；早期 job 含 `rows_json` |
| `hospital_reconciliation_row` | 列一致；`notes_json` 含丰富计价说明（小件折算、封顶、包装未配置等） |
| `hospital_reconciliation_export_log` | 旧数据含 `export_type=department_summary`（分科室汇总）；现 schema 对 `export_type` 无枚举约束 |

### 3.3 规划拟新增、两边均不存在的表

| 表 | Phase | 用途 |
|----|-------|------|
| `customer` | 1 | 客户/医院主数据 |
| `customer_alias` | 1 | Excel 医院名解析别名 |
| `customer_discount` | 1 | 0.7 折扣等客户级优惠 |
| `product_category` | 2 | 产品分类（小件/敷料/高低温等） |
| `product` | 2 | 产品关键词主数据 |
| `customer_product_rule` | 2 | fixedPrices/foldRules/extraFees 关系化 |
| `sys_setting` | 4 | 公司/银行/导出前缀/敷料价表 |

---

## 4. 生产客户清单（18+ 家）

从 `hospital_reconciliation_job.sql` 与 `export_log` 提炼的**真实医院/客户**（含别名风险与测试数据区分）。

### 4.1 生产客户主表

| canonical_name（建议） | code 建议 | 别名/变体（须入 `customer_alias`） | 代表 xlsx（仅文件名） | rule_id | 典型规模 |
|------------------------|-----------|-----------------------------------|----------------------|---------|----------|
| 哈尔滨市第五医院 | HRB-WY | 市五院 | `市五院.xlsx` | 38 / 53 | **4829 行/单**，32 科室 sheet |
| 哈尔滨工业大学医院 | HRB-HIT | 哈尔滨工业大学、哈工程 | `哈尔滨工业大学医院4月15日-5月14日.xlsx` | 56 / 58 | 2200 行 |
| 哈尔滨市胸科医院 | HRB-XK | — | `哈尔滨市胸科医院4月账单.xlsx` | 57 | 193 行 |
| 东北农业大学医院 | NEAU-YY | 东北农业大学 | `农业大学医院4月.xlsx` | 36 | 19 行 |
| 哈尔滨道外区松电慢性病专科门诊部 | HRB-SD-MB | 松电慢性病专科门诊部 | `道外松电慢性病4月账单.xlsx` | 37 | 7 行 |
| 哈尔滨奥美医疗美容整形医院 | HRB-AM | 奥美 | `奥美4月账单.xlsx` | 8 / 55 | 10 行 |
| 嫒尚美医疗美容诊所 | HRB-ASM | — | `嫒尚美4月账单.xlsx` | 29 / 34 | 6 行 |
| 北一医院 | HRB-BY | 北一 | `北一4月账单.xlsx` | 35 / 60 | 6 行 |
| 春语医疗美容医院 | HRB-CY | 春雨（规则方案名） | `春语4月账单.xlsx` | 8 / 61 | 2 行 |
| 哈尔滨百年夏氏中医门诊部 | HRB-BNXS | 百年夏氏 | `百年夏氏4月账单.xlsx` | 8 / 59 | 1 行 |
| 哈尔滨长健医院 | HRB-CJ | — | `山西4月账单.xlsx` | 8 | 4 行 |

### 4.2 审计硬编码但未出现在铂康 job 的客户

以下客户存在于 `PricingEngine.java` 硬编码（foldRules / fixedPrices / 0.7 折扣 / contains 匹配），但**未出现在铂康 job 转储**中，Phase 1 种子须从 Engine 侧单独导入：

- 黑龙江省第二医院（南岗区 / 松北区）及括号变体
- 呼兰区第一人民医院
- 五常市人民医院
- 予美医疗整形医院
- 显著医生集团中西医结合门诊
- 黑龙江总工会医院
- 哈尔滨航天风华医院
- 哈尔滨美涵美医疗美容有限公司
- 黑龙江省海员总医院（松北）
- 黑龙江省中医药大学附属第四医院
- 哈尔滨市道里区妇幼保健院
- 黑龙江省妇幼保健院（人口）

### 4.3 测试/开发用数据（应排除于生产种子）

| hospital_name 变体 | 代表文件 | 说明 |
|-------------------|----------|------|
| 测试副本 | `测试副本.xlsx` | 早期功能验证，多版本迭代 |
| 测试医院 | `测试医院.xlsx` | UI/流程测试 |
| 20260415034753_东北农业大学测试 | `20260415034753_东北农业大学测试.xlsx` | 命名带时间戳的测试上传 |
| 口腔科 / 哈工程（部分 job） | 复用 `测试副本.xlsx` | 医院名与文件内容不一致的实验 job |

### 4.4 rule_id 与规则方案对照

| rule_id | 规则 `name` | version | 备注 |
|---------|-------------|---------|------|
| 8 | 标准灭菌计费规则 | v2.0 | 全局默认模板；含完整 `settlementLetter`、`exportOptions`、`freeBagFeeThreshold` |
| 35 | （北一专用，转储中另见 id 60「北一」） | — | 绑定北一医院 job |
| 36 | （东北农业大学医院） | — | job 35–39、71–72 |
| 37 | （松电慢性病） | — | job 40–60 |
| 38 | （市五院生产规则） | — | job 66+ 大批量 |
| 53 | 哈尔滨市第五医院 | 1.0.0 | needle 含**克氏针、种植盒** |
| 55 | 奥美 | 1.0.0 | 标准 needle 列表 |
| 56 | （哈工大医院） | — | job 324 |
| 57 | 哈尔滨市胸科医院 | 1.0.0 | job 362 |
| 58 | 哈尔滨工业大学 | 1.0.0 | needle 含**克氏针、种植盒** |
| 59 | 百年夏氏 | 1.0.0 | |
| 60 | 北一 | 1.0.0 | |
| 61 | 春雨 | 1.0.0 | 对应春语医疗美容医院 |

---

## 5. 规则 JSON 结构与审计缺口

### 5.1 铂康 `rules_json` 顶层结构（id=8 标准模板 v2.0）

```
version, highTemperature, lowTemperature, packaging, needle,
cleaning, logistics, settlementLetter, exportOptions
```

**关键结论：** 转储中 **无 `specialRules`** 节点（无 `fixedPrices` / `foldRules` / `extraFees`）。客户特例仍依赖 `PricingEngine.java` L328–399 的 Java 兜底列表。

### 5.2 审计未覆盖的 JSON 字段

| 项 | 示例值 | 出现位置 | 建议落点（重构后） |
|----|--------|----------|-------------------|
| `freeBagFeeThreshold` | 16.5 | id=8 `highTemperature.paperPlastic` | 保留于 `rules_json` 或 `sys_setting` |
| `settlementLetter` | 公司名「黑龙江省铂康医疗灭菌有限公司」、`feeItems`（灭菌费/物流费） | id=8 | Phase 4 `sys_setting` + 结款函模板管理 |
| `exportOptions` | `billFilePrefix=账单_`、`warningFilePrefix=异常_`、`settlementFilePrefix=结款函_` | 全部 8 条规则 | 迁入 `sys_setting`，替代前端 `pricingRules.ts` L288–291 |
| `cleaning.recomputeTotalsWhenPriceChanges` | true（标准 id=8） | JSON | 文档化默认值，与 dev 配置对齐 |
| `logistics.mergeAdjacentDays` / `mergeWindowDays` | false / 1 | JSON | 物流跨日合并策略（现 Engine 可能未完全实现） |

### 5.3 needle.keywords 客户级差异

| 规则 id | 名称 | 扩展关键词（相对标准列表） |
|---------|------|---------------------------|
| 8（v2.0 标准） | 标准灭菌计费规则 | 针、小件、缝针、穿刺针、手术针（与前端默认列表**不完全一致**） |
| 53 | 哈尔滨市第五医院 | **克氏针、种植盒** + 标准小件词 |
| 58 | 哈尔滨工业大学 | **克氏针、种植盒** + 标准小件词 |
| 55/57/59/60/61 | 其他客户方案 | 标准 12 词列表（无克氏针/种植盒） |

**迁移建议：** 市五院、哈工大的 needle 扩展词写入客户级 `rules_json` 覆盖或 `customer` 关联的 needle 配置段，而非全局默认。

### 5.4 `灭菌计费规则.md` 与代码对齐度

| 章节 | 对齐对象 | 一致性 |
|------|----------|--------|
| 高低温判定（ZSD 走高温） | `PricingEngine` 分支 | 高 |
| 小件折算（13 关键词） | `needle.keywords` 默认 + Engine | 高（md 多「针」字泛匹配） |
| 高温纸塑袋袋型价 | `rules_json` + Engine | 高 |
| 双袋 75→10 映射 | Engine L596–597 | 高（md 有记载，JSON 无字段） |
| 敷料包定价表 | Engine L196–231、L943–949 | 高（**仍硬编码**，非 JSON） |
| 低温阶梯贪心拆分 | Engine + JSON `tierPrices` | 高 |

---

## 6. Row 数据字段与规划模型映射

### 6.1 列级映射

| 旧列 `hospital_reconciliation_row` | 示例值 | 新模型目标 |
|-----------------------------------|--------|-------------|
| `type` | `额外包(纸塑袋)`、`敷料包（无纺布包）` | `product_category.pricing_path` |
| `pack_name` | `拔髓针-5件/Z7520`、`机扩针架1针10/Z1020` | `product.keywords` |
| `package_material` | `高温纸塑袋75*200`、`高温纸塑袋100*200` | `material_tags` + 袋型解析算法 |
| `pricing_rule` | `高温纸塑袋20cm计费`、`高温纸塑袋10cm计费` | 引擎分支标签（回归断言用） |
| `expected_unit_price` | 16.5、27.5、38.5、60.5、8.0 | 验收 golden 值 |
| `notes_json` | 小件折算说明、封顶 16.5、包装纸塑袋未配置 | Phase 3 双轨对比时一并断言 |
| `status` | `warning` / `unchanged` / `corrected` | 对账状态枚举 |

### 6.2 `notes_json` 典型条目（回归须保留语义）

```json
[
  "名称命中小件识别规则，按约每 5 件折算 1 件，建议复核。",
  "高温纸塑袋 1-2 件按最高 16.50 元封顶。",
  "命中包装收费项目「纸塑袋」，但尚未配置收费选项，请人工复核。"
]
```

Phase 3 双轨验证除 `expected_unit_price` 外，建议对 **warning 行** 抽样比对 `notes_json` 条数与关键短语，防止引擎静默改变解释逻辑。

### 6.3 高频产品关键词（Phase 2 `product` 种子候选）

从 row 转储统计的高频包名词干（建议 ≥20 条入库）：

| 关键词 | 关联分类 | 典型医院 |
|--------|----------|----------|
| 拔髓针 | SMALL_ITEM | 东北农大 |
| 洁牙机尖 | SMALL_ITEM / FIXED | 东北农大 |
| 挖勺 | SMALL_ITEM / PRICE_PER_INSTRUMENT | 东北农大、航天风华 |
| 机扩针 | SMALL_ITEM / FOLD | 松电、市五院、哈工大 |
| 克氏针 | SMALL_ITEM | 市五院、哈工大 |
| 种植盒 | SMALL_ITEM | 市五院、哈工大 |
| 车针 | SMALL_ITEM | 多院 |
| 空心钉 / 3.6空心钉 | FIXED_OVERRIDE | 省二院（Engine 兜底） |
| 肖啸钻头 | SMALL_ITEM | 市五院 |
| 敷料包 | DRESSING | 多院 |
| 棉球 | DRESSING / FIXED | 显著医生集团 |

---

## 7. 标杆 Job 与验收用例

### 7.1 标杆 job 元数据

| job_id | hospital_name | source_file_name | rule_id | total_rows | corrected_rows | warning_rows | 优先级 |
|--------|---------------|------------------|---------|------------|----------------|--------------|--------|
| 35 | 东北农业大学医院 | 农业大学医院4月.xlsx | 36 | 19 | 2 | 0 | **P0** |
| 40 | 哈尔滨道外区松电慢性病专科门诊部 | 道外松电慢性病4月账单.xlsx | 37 | 7 | 5 | 0 | **P0** |
| 66 | 哈尔滨市第五医院 | 市五院.xlsx | 38 | 4829 | 344 | 169 | **P0** |
| 74 | 哈尔滨市第五医院 | 市五院.xlsx | 38 | 4829 | 344 | 169 | **P0**（approved 版本） |
| 324 | 哈尔滨工业大学医院 | 哈尔滨工业大学医院4月15日-5月14日.xlsx | 56 | 2200 | 0 | 104 | **P1** |
| 362 | 哈尔滨市胸科医院 | 哈尔滨市胸科医院4月账单.xlsx | 57 | 193 | 0 | 16 | **P1** |

### 7.2 代表性行级验收用例（来自真实 row）

| 医院 (job) | 包名 | 材料 | 器械/包 | 期望单价 | 规则类型 | 备注 |
|------------|------|------|---------|----------|----------|------|
| 东北农大 (job 35) | 拔髓针-5件/Z7520 | 高温纸塑袋75*200 | 5/1 | 16.5 | 小件折算 + 高温封顶 | `pricing_rule`=高温纸塑袋20cm计费 |
| 东北农大 (job 35) | 挖勺-1 | 高温纸塑袋75*300 | 5/5 | NULL | 未识别 30cm 袋型 | 应产生 warning |
| 东北农大 (job 35) | 洁牙机尖-2 | — | — | 5.5/件 | PRICE_PER_INSTRUMENT | Engine 兜底 fixedPrices |
| 市五院 (job 66) | 肖啸钻头0.7-1件 | 高温纸塑袋75*200 | 1/1 | 8.0 | 10cm 袋费 | 大批量中典型小件 |
| 哈工大 (job 324) | 机扩针架1针10/Z1020 | 高温纸塑袋100*200 | 22/2 | 60.5 | 小件折算 + 超封顶按 5.5/件 | 需克氏针/机扩针 needle 词 |
| 松电 (job 40) | 机扩针相关包 | — | ≥5 | — | FOLD 5:1 | foldRules 兜底场景 |

### 7.3 市五院 (job 66) 多科室结构

job 66 含 **32 个 sheet**（科室），例如：口内（561 行）、口外（410 行）、（一）手术室（2134 行）、（二）手术室（1015 行）等。`sheet_warning_counts` 显示口内 54 行、（二）手术室 62 行等为 warning 集中区。

**回归策略：** 全量 4829 行 CI 过慢；建议固定种子抽样 **200 行**（每科室至少 3 行 + 全部 warning 行）做 nightly diff。

### 7.4 导出类型验收（export_log）

| export_type | 含义 | 样本量（转储） |
|-------------|------|----------------|
| `result` / `bill` | 账单 xlsx | 多数 |
| `warning` | 异常表 xlsx | 多数 |
| `settlement` | 结款函 html | 部分 |
| `department_summary` | **分科室汇总** | 市五院等大客户 |

Phase 4 导出模块须保留 `department_summary` 类型，或在 `sys_setting` 中可配置启用。

---

## 8. 双源种子策略

### 8.1 数据源对照

| 数据源 | 覆盖内容 | 缺口 |
|--------|----------|------|
| `PricingEngine.java` 硬编码 | 15 家医院、22 fixedPrices、7 foldRules、1 extraFee、0.7 折扣、敷料表 | 无生产 row 样本；无市五院/哈工大等 |
| 铂康 job 转储 | 18+ 生产客户、真实 xlsx 文件名、rule_id 绑定、多科室结构 | 无省二院等 Engine 客户；无 specialRules JSON |
| 铂康 row 转储 | 58 万行 golden 价格、notes_json | 文件过大，须抽样导入测试库 |
| `灭菌计费规则.md` | 完整规则 prose | 非机器可读，作文档基准 |

### 8.2 Phase 1 客户种子合并逻辑

```
customers = UNION(
  EXPORT_FROM(PricingEngine.java, source='engine'),
  DEDUP_FROM(铂康/hospital_reconciliation_job.sql, source='bokang_job')
)
aliases = ENGINE_VARIANTS + BOKANG_HOSPITAL_NAME_VARIANTS
```

- Engine 来源客户：标记 `customer_alias.source = 'engine'`
- 铂康 job 来源：标记 `customer_alias.source = 'bokang_job'`
- 人工补录：`'manual'`

### 8.3 Phase 2 产品/规则种子合并逻辑

```
customer_product_rule = IMPORT(PricingEngine L328-399)
product.keywords = TOP_N(铂康/row.pack_name, n=20) + ENGINE_FIXED_KEYWORDS
needle_override = PER_CUSTOMER(铂康/hospital_pricing_rule.id IN (53, 58))
```

---

## 9. 铂康转储迁移映射

### 9.1 文件 → 目标表

| 铂康文件 | 目标 | Phase | 说明 |
|----------|------|-------|------|
| `hospital_pricing_rule.sql` | `hospital_pricing_rule` | 0 | 保留 id=8 为 `default_template`；其余按 `name` 关联客户 |
| `hospital_reconciliation_job.sql` | `hospital_reconciliation_job` + `customer` | 0/1 | 仅 dev/staging；`hospital_name` 去重 → customer |
| `hospital_reconciliation_row.sql` | `hospital_reconciliation_row` | 0 | 仅导入标杆 job_id IN (35,40,66,74,324,362) |
| `hospital_reconciliation_export_log.sql` | 参考 | 4 | 导出类型枚举设计输入 |
| `sys_user.sql` | 不导入生产 | — | 含真实密码哈希，仅作格式参考 |
| `sys_menu.sql` | 不导入 | — | 与现 `DataInitializer` 菜单体系不同 |
| `灭菌计费规则.md` | `system_docs/` 链接 | 0 | 已纳入文档链 |

### 9.2 推荐导入脚本骨架（Phase 0）

```
scripts/import-bokang-baseline.sh
├── 01_pricing_rules.sql      # 来自 hospital_pricing_rule.sql（8 条）
├── 02_customers_from_jobs.sql # job hospital_name 去重 → customer + alias
├── 03_baseline_jobs.sql      # job_id IN (35,40,66,74,324,362)
├── 04_baseline_rows.sql      # 对应 row 子集（从全量转储抽取）
└── manifest.txt              # 登记入 migrate_manifest.txt（仅 dev profile）
```

**约束：** 生产环境**不**直接导入铂康全量 row（58 万行）；仅 staging / CI 使用抽样。

### 9.3 `hospital_pricing_rule` → `customer` 绑定映射

| 规则 name | → customer.code | default_rule_id |
|-----------|-----------------|-----------------|
| 哈尔滨市第五医院 | HRB-WY | 53 或 38 |
| 哈尔滨工业大学 / 哈尔滨工业大学医院 | HRB-HIT | 58 或 56 |
| 哈尔滨市胸科医院 | HRB-XK | 57 |
| 奥美 | HRB-AM | 55 |
| 北一 | HRB-BY | 60 |
| 百年夏氏 | HRB-BNXS | 59 |
| 春雨 | HRB-CY | 61 |
| 标准灭菌计费规则 | —（全局模板） | 8 |

---

## 10. 与重构计划 Phase 对照

| 重构 Phase | 铂康资产用途 |
|------------|-------------|
| Phase 0 | 导入 8 条规则 + 6 个标杆 job/row；`灭菌计费规则.md` 进基线文档 |
| Phase 1 | job 去重 → ≥18 customer、≥30 alias；`resolveByName` 全量验收 |
| Phase 2 | row 高频词 → ≥20 product；市五院/哈工大 needle 扩展 |
| Phase 3 | 标杆 job 双轨 diff=0；notes_json 抽样比对 |
| Phase 4 | `settlementLetter` / `exportOptions` 迁入 sys_setting；`department_summary` 导出 |
| Phase 5 | 删除 Engine defaults 后，铂康回归集仍为最终护栏 |

---

## 11. 风险与注意事项

| ID | 风险 | 缓解 |
|----|------|------|
| B1 | 转储含真实用户密码哈希 | **禁止**导入生产；`.gitignore` 或脱敏 |
| B2 | 58 万 row 导致测试库膨胀 | 仅抽样标杆 job |
| B3 | 医院名变体（哈工程 vs 哈尔滨工业大学医院） | `customer_alias` 全量收录 |
| B4 | rule_id 与 customer 多对多历史 | `default_rule_id` + job 级 `rule_id` 并存 |
| B5 | 铂康 无 specialRules，与 Engine 双轨 | Phase 3 前须合并 Engine defaults 入 DB |
| B6 | xlsx 文件缺失 | 回归依赖 row 转储，非 Excel 重放 |

---

## 12. 附录：sys_user / sys_menu 快照

### 12.1 旧系统用户（4）

| username | email | 备注 |
|----------|-------|------|
| admin | — | 主管理员 |
| yang | — | |
| admin001 | — | |
| qu001 | — | job 324、362 操作员 |

### 12.2 旧系统菜单（6）

与现 `DataInitializer` 对比：旧系统仅有「系统管理」（用户/角色/菜单/API）和「一级菜单」占位，**无**医院对账业务菜单。说明业务菜单在后续版本才通过 `DataInitializer` 或手动配置加入。

---

## 13. 附录：标准 rules_json 片段（id=8 节选）

```json
{
  "version": "v2.0",
  "highTemperature": {
    "paperPlastic": {
      "perPackagePrice": 5.5,
      "minCharge": 16.5,
      "freeBagFeeThreshold": 16.5,
      "bagSizes": [
        { "size": 25, "price": 10.5, "keywords": ["25cm", "25", "特大"] },
        { "size": 20, "price": 7.5, "keywords": ["20cm", "20", "大"] },
        { "size": 15, "price": 5.5, "keywords": ["15cm", "15", "中"] },
        { "size": 10, "price": 2.5, "keywords": ["10cm", "10", "小"] }
      ]
    }
  },
  "needle": {
    "threshold": 5,
    "foldRatio": 5,
    "keywords": ["针", "小件", "缝针", "穿刺针", "手术针"]
  },
  "settlementLetter": {
    "companyName": "黑龙江省铂康医疗灭菌有限公司",
    "feeItems": [
      { "key": "sterilize", "label": "灭菌费", "enabled": true },
      { "key": "logistics", "label": "物流费", "enabled": true }
    ]
  },
  "exportOptions": {
    "billFilePrefix": "账单_",
    "warningFilePrefix": "异常_",
    "settlementFilePrefix": "结款函_"
  }
}
```

---

*文档结束。实施请参阅 [`iteration-refactoring-plan.md`](./iteration-refactoring-plan.md) §7.5、§10.4 及附录 B/C/D。*
