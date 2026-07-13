# 硬编码业务数据审计报告

> 审计范围：`backend/`（Java、YAML、SQL、resources）及前端中被后端引用/重复的规则定义  
> 审计日期：2026-07-08  
> 结论：**计费规则采用「DB JSON + 引擎内硬编码兜底」双轨架构**，客户/医院特例大量写在 `PricingEngine.java` 中，与前端默认规则存在三处重复。

---

## 概述

| 类别 | 条目数（约） | 严重程度 | 主要位置 |
|------|-------------|----------|----------|
| 客户/医院名称与别名 | **15+ 医院**（含括号变体） | 高 | `PricingEngine.java` |
| 产品/固定单价规则 | **22 条** fixedPrices + 产品关键词 | 高 | `PricingEngine.java` L378-399 |
| 小件折算规则 | **7 条** foldRules | 高 | `PricingEngine.java` L328-335 |
| 通用定价参数（袋型/阶梯/无纺布） | **30+ 数值** | 中 | 前端默认 + 测试 + DB 可配 |
| 引擎内不可配置算法 | **10+ 处** | 高 | `PricingEngine.java` |
| 公司/银行信息 | **3 项** | 高（含默认账号） | YAML + `HospitalReconciliationServiceImpl` |
| 系统初始化 | 角色 1、菜单 4、用户 4 | 中 | `DataInitializer.java` |
| 模板路径 | **2 个 xlsx**（仓库内缺失） | 中 | `application-dev.yml` 等 |
| 结款函文案/HTML | **多处** | 低-中 | `HospitalReconciliationServiceImpl` |
| 前端重复默认规则 | **3 处** | 高（漂移风险） | `pricingRules.ts` 等 |

**架构要点：**

- 规则主存储：`hospital_pricing_rule.rules_json`（可 CRUD）
- 定价执行：`PricingEngine` 先读 JSON，**JSON 缺失时回退到 Java 内 `List.of(Map.of(...))` 默认值**
- 部分逻辑**永远硬编码**（敷料包定价表、0.7 折扣、医院名 `contains` 判断等），不在 JSON 中

---

## 1. 客户/医院信息

### 1.1 引擎常量（精确匹配）

| 文件 | 行号 | 内容 | 用途 |
|------|------|------|------|
| `backend/.../PricingEngine.java` | 25-27 | `黑龙江省第二医院（南岗区）`、`（松北区）`、`呼兰区第一人民医院` | 0.7 倍特色账单 |
| 同上 | 28 | `SECOND_HOSPITAL_NANGANG_RATE = 0.7` | 折扣系数 |

### 1.2 小件折算规则中的医院（foldRules 兜底）

| 医院名称 | 行号 |
|----------|------|
| 松电慢性病专科门诊部 | 329 |
| 哈尔滨航天风华医院 | 330 |
| 哈尔滨美涵美医疗美容有限公司 | 331 |
| 黑龙江省海员总医院（松北）/ (松北) | 332 |
| 黑龙江省中医药大学附属第 四 医院 / 附属第四医院 | 333 |
| 哈尔滨市道里区妇幼保健院 | 334 |
| 黑龙江省妇幼保健院（人口）/ (人口) | 335 |

### 1.3 固定单价规则中的医院（fixedPrices 兜底）

| 医院 | 关联产品数 | 行号 |
|------|-----------|------|
| 黑龙江省第二医院（松北区） | 9 项 | 379-387 |
| 黑龙江省第二医院（南岗区） | 9 项 | 388-396 |
| 东北农业大学医院 | 洁牙机尖 | 397 |
| 哈尔滨航天风华医院 | 挖勺 | 398 |
| 显著医生集团中西医结合门诊 | 棉球 30cm | 399 |

### 1.4 字符串包含匹配（非精确，易误匹配）

| 医院关键词 | 行号 | 规则 |
|-----------|------|------|
| `黑龙江总工会医院` + `镜头` | 475-480 | 加收 8 元 |
| `五常市人民医院` | 692-694 | 纸塑袋 capMode=none |
| `显著医生集团中西医结合门诊` | 692-694 | 同上 |
| `予美医疗整形医院` | 704 | 封顶时双层袋仍收费 |

### 1.5 Legacy 按件计价医院

| 行号 | 医院 + 关键词 |
|------|--------------|
| 448-449 | 东北农业大学医院+洁牙机尖；哈尔滨航天风华医院+挖勺 |

### 1.6 其他引用

| 文件 | 行号 | 内容 | 用途 |
|------|------|------|------|
| `HospitalReconciliationServiceImpl.java` | 583 | `"未命名医院"` | Excel 未识别医院名时的默认值 |
| 同上 | 266（注释） | `"哈尔滨市第一医院"` | 文档示例 |
| `PricingEngineTest.java` | 全文 | 10+ 医院名 | 单元测试夹具 |

**唯一医院清单（去重后约 15 家）：**

东北农业大学医院、哈尔滨航天风华医院、黑龙江总工会医院、黑龙江省第二医院（南岗/松北及括号变体）、呼兰区第一人民医院、显著医生集团中西医结合门诊、松电慢性病专科门诊部、哈尔滨美涵美医疗美容有限公司、黑龙江省海员总医院（松北）、黑龙江省中医药大学附属第四医院、哈尔滨市道里区妇幼保健院、黑龙江省妇幼保健院（人口）、五常市人民医院、予美医疗整形医院

---

## 2. 商品/产品/报价信息

### 2.1 省二院固定单价（引擎兜底，skipHospitalDiscount=true）

| 院区 | 产品/关键词 | 单价(元) | 行号 |
|------|------------|---------|------|
| 松北 | 3.6空心钉工具包 | 190.05 | 379 |
| 松北 | 3.6空心钉 / 7.3空心钉 | 13.3 | 380-381 |
| 松北 | 手术衣+无纺布/纸塑袋 | 26.6 / 28.0 | 382-383 |
| 松北 | 钉 / 软镜 / 泌尿显微镜头 | 35 / 210 / 210 | 384-386 |
| 松北 | 小腔包 | 53.55 | 387 |
| 南岗 | 3.6空心钉工具包 | 205.45 | 388 |
| 南岗 | 钉 | **140.0**（松北为 35） | 393 |
| 南岗 | 小腔包 | 49.7 | 396 |

### 2.2 其他医院固定单价

| 规则 | 价格 | 行号 |
|------|------|------|
| 东北农业大学医院 洁牙机尖（按件） | 5.5/件 | 397 |
| 航天风华 挖勺（按件） | 5.5/件 | 398 |
| 显著医生集团 30cm 棉球 | 4.0 | 399 |

### 2.3 敷料包（**完全硬编码，不可 JSON 配置**）

| 条件 | 单价 | 行号 |
|------|------|------|
| 纸塑袋+棉球 20cm | 4.0 | 199-202 |
| 纸塑袋+棉球 15cm | 2.5 | 203-206 |
| 无纺布敷料包 measure<90 | 25 | 943-946 |
| measure=90 | 30 | 945 |
| measure 1.2~1.5 | 35 | 948 |

### 2.4 默认袋型/阶梯价（前端新建规则 + 测试 + normalize 兜底）

**高温纸塑袋袋费：** 25cm/10.5, 20cm/7.5, 15cm/5.5, 10cm/2.5；件费 5.5；封顶 16.5  
**低温纸塑袋/无纺布：** 袋型 30/35 … 10/22；阶梯 20→300, 10→165, 5→88；余数 22；单件最低 35

来源：

- `frontend/src/api/hospital/pricingRules.ts` L66-91, L226-246
- `frontend/src/views/hospital/pricing-rules/index.vue` L796-839
- `PricingEngineTest.java` L298-341

### 2.5 包装收费默认项（前端）

| 项目 | 规格选项 | 价格 | 文件行号 |
|------|---------|------|---------|
| 纱布棉球 | 大/中/小/20纸塑/15纸塑 | 2.5/2/1.5/4/2.5 | `pricingRules.ts` L119-150 |
| rigip / 纸塑袋 | 空 options | - | L152-163 |

### 2.6 小件关键词列表（needle.keywords 默认）

`小件、探针、穿刺针、缝合针、车针、拔髓针、成型片、根管针、根管锉、支抗钉、洁牙机尖、球钻、挖勺`

（`pricingRules.ts` L249；`pricing-rules/index.vue` L861）

---

## 3. 计费/对账规则

### 3.1 PricingEngine 内嵌算法常量

| 规则 | 默认值 | 行号 | 可 JSON 覆盖？ |
|------|--------|------|---------------|
| 小件 threshold/foldRatio | 5 / 5 | 134, 154-155 | 部分（needle 节点） |
| 纸塑袋 >25cm 按 25 计 | 25 | 665-667 | 否（算法） |
| 双袋 75→10 映射 | 75→10 | 596-597 | 否 |
| 低温未识别袋型 | 22 元 | 751-752 | 部分 |
| 阶梯 ≤5 封顶 | 88 | 771, 830 | tierPrices |
| 物流费单次 | 50 元 | `HospitalReconciliationServiceImpl` 384, 621 | rules.logistics.feePerTrip |
| 物流跨天边界 | 20 点 | 前端默认 | logistics.dayBoundaryHour |

### 3.2 特色账单 0.7 倍

```java
// PricingEngine.java L274-281
// 特色账单黑龙江省第二医院（南岗区）
if (!skipHospitalDiscount && (isSecondHospitalNangang(hospitalName) || isSecondHospitalSongbei(hospitalName) || isHulanFirstPeopleHospital(hospitalName)) && expectedUnitPrice != null) {
    double baseUnitPrice = expectedUnitPrice;
    expectedUnitPrice = round(baseUnitPrice * SECOND_HOSPITAL_NANGANG_RATE);
}
```

**注意：** 注释写「南岗区」，但实际三家医院（含松北、呼兰）均适用 0.7。

### 3.3 规则回退机制

当 `rules_json.specialRules.fixedPrices/foldRules/extraFees` 为空或未命中时，引擎使用 L328-399 的 Java 默认列表——**即使 DB 有规则，特例仍可能来自代码**。

### 3.4 对账流程硬编码

| 文件 | 行号 | 内容 |
|------|------|------|
| `HospitalReconciliationServiceImpl` | 376-399, 616-621 | 物流费 = 唯一发货日 × 50 |
| 同上 | 2431 | Excel 模板占位 `"计费规则占位"` |
| 同上 | 2400, 2454 | `"日期占位"`、`"科室占位"` |

---

## 4. 公司/组织信息

| 文件 | 行号 | 字段 | 默认值 |
|------|------|------|--------|
| `application.yml` | 31-34 | `app.company.name` | 黑龙江省铂康医疗灭菌有限公司 |
| 同上 | | `bank-account` | `${APP_COMPANY_BANK_ACCOUNT:}` 空 |
| 同上 | | `bank-name` | `${APP_COMPANY_BANK_NAME:}` 空 |
| `HospitalReconciliationServiceImpl.java` | 215-224 | `@Value` 兜底 | 公司名、账号 **168995238437**、中国银行股份有限公司哈尔滨道里支行 |
| `.env.example` | 24-26 | 环境变量占位 | 银行信息可选注入 |

**风险：** 生产未设环境变量时，结款函 HTML 会使用硬编码银行账号。

---

## 5. 系统初始化数据

### 5.1 DataInitializer.java

| 类型 | 内容 | 行号 |
|------|------|------|
| 角色 | `R_USER` / 普通用户 | 35-38 |
| 菜单 | 追踪系统、医院发货表上传、医院计费规则、历史版本与审核 | 42-49 |
| 用户 | user1~user4 @hospital.com，**随机 8 位密码** | 60-78 |

### 5.2 文档与实现不一致

| 文件 | 问题 |
|------|------|
| `backend/README.md` L70 | 写密码均为 `abcd1234`，**与 DataInitializer 随机密码不符** |

### 5.3 schema.sql

- 仅 DDL，**无 INSERT 种子数据**
- 计费规则、医院数据需运行时通过 UI/API 创建

### 5.4 开发环境敏感配置

| 文件 | 行号 | 内容 |
|------|------|------|
| `application-dev.yml` | 12 | JWT secret: `dev-jwt-secret-key-for-local-development-only` |
| 同上 | 6 | DB password: `root` |

---

## 6. 配置文件中的业务常量

| 文件 | 键 | 值 |
|------|-----|-----|
| `application-dev.yml` | `app.template.bill` | `测试表格/账单_标准模板.xlsx` |
| 同上 | `app.template.settlement` | `测试表格/结款函.xlsx` |
| `application-docker.yml` | 同上 | 同上 |
| `application.yml` | `app.upload.dir` | `./uploads/hospital-reconciliations` |
| `application-dev.yml` | `app.storage.dir` | `./storage/hospital-reconciliations` |

**缺口：** 仓库内 **未找到** 任何 `.xlsx` 模板文件；导出会降级为简单 Excel。

---

## 7. 模板与静态资源

### 7.1 模板 ID 常量

| 文件 | 常量 | 值 |
|------|------|-----|
| `HospitalReconciliationServiceImpl` | `DEFAULT_SETTLEMENT_TEMPLATE_ID` | `default_settlement` |
| 同上 | `DEFAULT_BILL_TEMPLATE_ID` | `default_bill` |

### 7.2 结款函 HTML 硬编码文案

| 位置 | 行号 | 内容摘要 |
|------|------|---------|
| 正式结款函 | 4184-4192 | 核对说明、3 工作日付款、公司名称/账号/银行、默认金额条款 |
| 预览模板 | 4237-4240 | 「示例医院」、2026-01-01~31、铂康公司名 |
| 示例费用表 | 4261-4268 | 灭菌 1234.56、物流 150（50元/次）、大写金额 |

### 7.3 导出文件名前缀（前端默认）

`账单_`、`异常_`、`结款函_`（`pricingRules.ts` L288-291）

---

## 8. 前端重复硬编码（后端引用/漂移风险）

| 文件 | 与后端关系 |
|------|-----------|
| `frontend/src/api/hospital/pricingRules.ts` | `createDefaultSpecialRules()` 等与 `PricingEngine` defaults **部分同步、部分缺失**（前端 fixedPrices 仅 3 条，后端 22 条） |
| `frontend/src/views/hospital/pricing-rules/index.vue` | `defaultEmptyRules()` 完整复制默认规则，用于「新建方案」 |
| `frontend/src/views/hospital/reconciliation/index.vue` L10 | 已禁止本地默认定价，依赖后端规则 API |

**关键漂移：** 前端 specialRules.fixedPrices **缺少省二院 18 条规则**，但后端引擎仍会应用——前后端 UI 展示与实际计算可能不一致。

---

## 9. 测试/Mock 数据

| 文件 | 说明 |
|------|------|
| `PricingEngineTest.java` | 生产级医院名+价格断言，可作为规则文档参考 |
| `frontend/src/mock/temp/articleList.ts` | 与业务无关 |
| `buildSampleFeeTableHtml()` | 仅预览，非生产计费 |

---

## 10. 风险与建议

### 高风险

1. **PricingEngine 双轨兜底**：DB 规则与 Java defaults 并存，运维难以知悉真实生效规则。
2. **0.7 折扣、敷料包定价、医院 contains 逻辑** 无法通过规则 UI 修改。
3. **默认银行账号** 写入 Java 源码，存在泄露与误用风险。
4. **三处默认规则重复**（Engine / pricingRules.ts / index.vue），维护成本高。

### 中风险

5. xlsx 模板路径指向不存在文件，生产导出格式可能不符合预期。
6. README 默认密码文档错误。
7. 医院名括号/空格变体需多处维护（如 `（松北）` vs `(松北)`）。

### 建议下一步（规划）

1. **单一数据源**：将所有 specialRules、折扣、敷料包表迁入 DB JSON Schema，引擎只解释 JSON，删除 `List.of` defaults。
2. **客户主数据表**：`hospital_client(id, name, aliases[], discount_rate, cap_mode, …)`，规则引用 client_id。
3. **产品价目表**：`product_price(hospital_id, keyword, material, bag_size, price, …)`。
4. **配置外置**：公司/银行信息仅来自环境变量，移除 Java 默认账号。
5. **模板资产化**：xlsx 入仓或对象存储，路径可配置。
6. **一致性测试**：以 `PricingEngineTest` 为基准，加「DB 规则 vs 引擎」契约测试。
7. **前端去重**：新建规则从后端 API 获取 `GET /default-template`，删除 `defaultEmptyRules` 副本。

---

## 统计汇总

| 类别 | 数量 |
|------|------|
| 硬编码医院/客户名（去重） | ~15 |
| fixedPrices 兜底规则 | 22 |
| foldRules 兜底规则 | 7 |
| extraFees 兜底规则 | 1 |
| 引擎内不可 JSON 配置算法块 | ~10 |
| 默认袋型配置项 | 9（高/低温） |
| 阶梯价档位 | 3 × 2 |
| 包装收费默认项 | 3 项目 + 5 规格 |
| 公司信息字段 | 3 |
| 初始化用户/角色/菜单 | 4 / 1 / 4 |
| 模板 xlsx 引用 | 2（文件缺失） |
| 前端重复默认规则文件 | 2 |

---

## 附录：审计缺口说明

| 项 | 状态 |
|----|------|
| SQL 种子数据 | 无（当前 schema）；**铂康转储含生产数据**，见 §11 |
| Java enum 业务枚举 | 无 |
| 独立客户/产品 CRUD 模块 | 无（仅 `hospital_pricing_rule` JSON blob） |
| 仓库内 xlsx 模板 | 未找到（需确认 `测试表格/` 是否在部署包或 `.gitignore` 中） |

---

## 11. 铂康遗留资产补充（2026-07-08）

> 深度分析见 [`bokang-legacy-analysis.md`](./bokang-legacy-analysis.md)。本节为审计报告的铂康转储补充发现。

| 类别 | 条目 | 位置 | 与主审计关系 |
|------|------|------|-------------|
| 生产客户（铂康 job） | **18+** 家（市五院、哈工大、胸科、北一等） | `铂康/建表语句/hospital_reconciliation_job.sql` | 超出 §1 去重 ~15 家 |
| 真实计价样本 | **580,915** 行 row | `铂康/建表语句/hospital_reconciliation_row.sql` | §9 测试夹具可扩展 |
| rules_json 扩展块 | `settlementLetter`、`exportOptions`、`freeBagFeeThreshold` | `hospital_pricing_rule.sql` id=8 | §2/§7 未列 |
| 客户级 needle 差异 | **克氏针、种植盒** | id=53（市五院）、id=58（哈工大） | §2.6 默认列表未含 |
| 标准 needle 扩展 | 针、缝针、手术针 | id=8 v2.0 | 与 §2.6 前端列表不完全一致 |
| 导出类型 | `department_summary`（分科室汇总）、`bill`/`result`/`warning`/`settlement` | `hospital_reconciliation_export_log.sql` | §7 未列 department_summary |
| 真实产品词 | 机扩针架、肖啸钻头、拔髓针-5件 等 | `hospital_reconciliation_row.sql` | §2 可补充产品词表 |
| 公司名（JSON 内） | 黑龙江省铂康医疗灭菌有限公司 | `rules_json.settlementLetter` | §4 仅 YAML/ Java 兜底 |
| 转储性质 | INSERT 转储，**非 DDL**；目录无 xlsx | `铂康/建表语句/` | §5.3 schema 无种子 |
| Engine 未覆盖客户 | 省二院、呼兰、五常、予美等 | 仅 `PricingEngine.java` | 铂康 job 中**未出现**，须双源种子 |
| row 计价说明 | `notes_json` 小件折算/封顶/包装警告 | `hospital_reconciliation_row` | 引擎算法块 §3.1 补充验收维度 |
| 旧系统菜单 | 仅 RBAC 系统管理，无医院业务菜单 | `铂康/建表语句/sys_menu.sql` | §5.1 DataInitializer 不同 |
