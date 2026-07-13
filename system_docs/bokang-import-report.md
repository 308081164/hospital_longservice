# 铂康 SQL 转储分析与导入报告

> 执行日期：2026-07-10  
> 工具：`BokangDataImportRunner`（`backend/src/main/java/com/hospital/backend/imports/bokang/`）  
> 关联分析：`[bokang-legacy-analysis.md](./bokang-legacy-analysis.md)`

---

## 1. 各文件内容分析


| 文件                                       | 规模                   | 是否含产品/价格主数据        | 内容摘要                                                                                                   |
| ---------------------------------------- | -------------------- | ------------------ | ------------------------------------------------------------------------------------------------------ |
| `hospital_pricing_rule.sql`              | **8 条** INSERT       | **是（规则配置）**        | `rules_json` 含高低温袋型价、阶梯价、小件 needle 关键词、包装收费、物流/结款函/导出选项；**无** `specialRules.fixedPrices`               |
| `hospital_reconciliation_row.sql`        | **580,915 行** INSERT | **是（产品样本 + 期望单价）** | `pack_name`、`package_material`、`type`、`expected_unit_price`、`pricing_rule`、`notes_json`；真实计价 golden 样本 |
| `hospital_reconciliation_job.sql`        | **417 条** INSERT     | **间接（客户/医院名）**     | `hospital_name`、`rule_id`、源文件名、多 sheet 元数据；用于客户别名与规则绑定                                                 |
| `hospital_reconciliation_export_log.sql` | **328 条** INSERT     | **否**              | 账单/异常/结款函/分科室汇总导出记录，仅操作审计                                                                              |
| `sys_user.sql`                           | **4 用户**             | **否**              | 旧系统账号（含密码哈希），**禁止导入生产**                                                                                |
| `sys_menu.sql`                           | **6 菜单**             | **否**              | 通用 RBAC 菜单，与现 `DataInitializer` 体系不同                                                                   |


**结论：** 产品/价格相关源为 `hospital_pricing_rule.sql` + `hospital_reconciliation_row.sql`；客户名来自 `hospital_reconciliation_job.sql`。其余文件不参与 master data 导入。

---



## 2. 导入策略（已实现）


| 源                                 | 目标表                              | 策略                                                                                                                                 |
| --------------------------------- | -------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `hospital_pricing_rule.sql`       | `hospital_pricing_rule`          | 按 `name` 幂等插入（已存在则跳过）                                                                                                              |
| `hospital_reconciliation_job.sql` | `customer` + `customer_alias`    | 去重 `hospital_name`；已知医院映射到已有 code；过滤测试医院；新医院自动生成 `BK-xxxxx` code                                                                   |
| `hospital_reconciliation_row.sql` | `product` + `product_match_rule` | **流式**扫描 58 万行；按 `pack_name` 词干 + `type` + `material` 聚合；取 Top 500 频次；写入 `public_price`/`original_price`（中位 `expected_unit_price`） |
| Engine 硬编码                        | `customer_product_rule`          | 已由 `HardcodedRulesMigrationRunner` 迁移（非本次 SQL 解析）                                                                                  |
| 全量 row/job                        | `hospital_reconciliation_*`      | **故意跳过**（58 万历史明细不入库，仅 dev 回归时可抽样标杆 job）                                                                                           |


---



## 3. 本次 Docker 导入结果（2026-07-10）

启动命令：

```powershell
$env:IMPORT_BOKANG_DATA="1"
docker compose up -d --build backend
```

日志摘要：

```
rules +7/skipped 1, customers +2, aliases +18/skipped 3,
products +456/skipped 44, prices updated 2,
rows scanned 580915, test hospitals skipped 25
```



### 3.1 导入后表计数


| 表                             | 数量    | 说明                             |
| ----------------------------- | ----- | ------------------------------ |
| `customer`                    | 26    | 种子 ~24 + 新增 2（如 2026天天美容 等）    |
| `customer_alias`              | 43    | 新增别名 18（来源 `bokang_job`）       |
| `product`                     | 473   | 种子 17 + 新增 456                 |
| `product_match_rule`          | 473   | 与 product 1:1                  |
| `hospital_pricing_rule`       | 9     | 种子 1（标准模板）+ 导入 7 + 跳过 1（同名已存在） |
| `customer_product_rule`       | 32    | Engine 迁移规则（非 SQL 导入）          |
| `hospital_reconciliation_job` | **0** | 未导入历史作业                        |
| `hospital_reconciliation_row` | **0** | 未导入 58 万明细                     |




### 3.2 跳过的内容


| 类别                  | 数量/原因                                                  |
| ------------------- | ------------------------------------------------------ |
| 计价规则                | 1 条「标准灭菌计费规则」— 与 `HardcodedRulesMigrationRunner` 已种子同名 |
| 客户别名                | 3 条 — 别名已存在于 `customer_alias`                          |
| 测试医院 job            | 25 条 hospital_name — 含「测试副本」「测试医院」「哈工程」等               |
| 产品                  | 44 个词干 — 与 `MasterDataInitializer` 已有产品重名              |
| 对账历史                | 580,915 row + 417 job — **按设计不导入**（仅 master data）      |
| sys_user / sys_menu | 全部 — 安全/菜单体系不兼容                                        |


---



## 4. 如何重新运行导入

导入器**幂等**：重复运行只会补充缺失项，不会重复插入同名规则/别名/产品。

### 方式 A：Docker 启动时自动导入

```powershell
# 项目根目录
$env:IMPORT_BOKANG_DATA = "1"
$env:BOKANG_MAX_PRODUCTS = "500"   # 可选，默认 500
docker compose up -d --build backend
```

数据目录挂载（已在 `docker-compose.yml` 配置）：

```
./铂康/建表语句  →  /app/bokang-data
```



### 方式 B：本地 dev profile

```powershell
$env:IMPORT_BOKANG_DATA = "1"
$env:BOKANG_DATA_DIR = ".\铂康\建表语句"
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```



### 方式 C：重启 backend 容器并带环境变量

```powershell
docker compose stop backend
docker compose run --rm -e IMPORT_BOKANG_DATA=1 backend
# 或修改 .env 中 IMPORT_BOKANG_DATA=1 后 docker compose up -d backend
```



### 验证 SQL

```powershell
docker exec hospital-mysql mysql -uhospital -p<password> hospital -e "
  SELECT setting_value FROM sys_setting WHERE setting_key='bokang_data_import_v1';
  SELECT COUNT(*) products FROM product;
  SELECT COUNT(*) rules FROM hospital_pricing_rule;
"
```

---



## 5. 代码位置


| 文件                                        | 职责                       |
| ----------------------------------------- | ------------------------ |
| `BokangDataImportRunner.java`             | 主编排：规则 / 客户 / 产品导入       |
| `BokangSqlInsertParser.java`              | INSERT VALUES 流式解析       |
| `BokangImportProperties.java`             | `IMPORT_BOKANG_DATA` 等配置 |
| `application.yml` → `app.import.bokang.*` | 默认路径与开关                  |


---

*本次未提交 git；如需纳入版本库请单独 review* `BokangDataImportRunner` *与 docker-compose 挂载变更。*