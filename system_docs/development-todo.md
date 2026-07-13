# 医院计费系统开发待办

> 跟踪迭代重构进度。关联文档：[`iteration-refactoring-plan.md`](./iteration-refactoring-plan.md)

最后更新：2026-07-10

---

## 本次重构（2026-07-10）— 菜单重组 + 硬编码迁移

### 已完成

- [x] **菜单重组**：移除侧边栏「医院计费规则」；新增「设置」分组，「版本管理」迁入 `/settings/version-management`
- [x] **`sys_setting` 表** + `schema_003_migration_sys_setting.sql` + `SchemaMigrationRunner`
- [x] **`HardcodedRulesMigrationRunner`**：幂等迁移 22 fixedPrices、7 foldRules、1 extraFee、0.7 折扣客户、敷料/公司/导出默认值
- [x] **`DefaultPricingTemplate`** + **`PricingRuleCompiler`**：DB 规则编译进 `rules_json.specialRules`
- [x] **`PricingEngine` 去硬编码**：删除 L328–399 Java defaults、省二院常量、总工会镜头 fallback、五常/予美 capMode 等
- [x] **`CustomerResolver`**：医院名 → customer 解析
- [x] **`GET /api/v1/settings/default-pricing-template`**
- [x] 前端：`settings` 路由模块、i18n `menus.settings.*`、`/hospital/pricing-rules` 重定向
- [x] `HospitalReconciliationServiceImpl` 对账时走 `PricingRuleCompiler` + `ProductMatchService`

### 待办

- [ ] `extraFees` JSON 路径单元测试（`addsLaborUnionLensBasketFee` 已 @Disabled，需集成验证）
- [ ] 前端 `pricingRules.ts` 完全改为从 API 加载默认模板
- [ ] `hospital_reconciliation_job.customer_id` 关联
- [ ] 客户产品规则 UI 完整编辑

### 验证结果（2026-07-10 Docker）

- [x] `docker compose up -d --build` 三台容器 healthy
- [x] 菜单 API：`pricing-rules` `isHidden=true`；`/settings` 分组含 `version-management`
- [x] `customer_product_rule` 29 条（20 FIXED + 2 PER_INSTRUMENT + 7 FOLD + 1 EXTRA + 1 MULTIPLIER）
- [x] `sys_setting` 8 项含 `hardcoded_rules_migrated_v1` 完成标记
- [x] `GET /api/v1/settings/default-pricing-template` 返回 v2.0 模板
- [x] `HardcodedRulesMigrationRunner` 执行顺序修正为 `@Order(110)`（在 MasterDataInitializer 之后）
- [x] **`BokangDataImportRunner`**：铂康 SQL 转储 master data 导入（客户别名、7 条规则、Top500 产品+价格）；见 [`bokang-import-report.md`](./bokang-import-report.md)
- [ ] 浏览器 UI 手测对账 Excel 上传（建议 user1/123456）

---

## Phase 1 — 客户主数据

### 已完成（2026-07-08）

- [x] `customer` / `customer_alias` / `customer_discount` / `customer_product_rule` 表与 CRUD API
- [x] 客户管理前端页面
- [x] 双源种子 **18+ 家客户**（铂康 + Engine）
- [x] `MasterDataInitializer` 幂等菜单
- [x] `schema_002_migration_customer_master.sql`

---

## Phase 2 — 产品/分类主数据

### 已完成（2026-07-08）

- [x] 产品分类/产品 CRUD + `ProductMatchService`
- [x] 前端主数据页面

---

## Phase 3 — 规则编译与双轨验证

- [x] `PricingRuleCompiler`（基础版）
- [x] 删除 PricingEngine Java defaults（2026-07-10）
- [x] 铂康 row 转储流式解析 → 456 产品 + 价格样本入库（2026-07-10，非全量 58 万行对比）
- [ ] 58 万行铂康回归对比（标杆 job 双轨 diff）

---

## Phase 4 — 系统设置与 UI 重组

- [x] `sys_setting` 表（2026-07-10）
- [x] 信息架构重组：主数据 / 追踪系统 / 设置（2026-07-10）

---

## 验证清单

1. `docker compose up -d --build` 启动成功
2. 登录 `user1` / `123456`
3. 侧边栏：**无**「医院计费规则」；**有**「设置 → 版本管理」
4. 客户管理：省二院/总工会/航天风华等含 `customer_product_rule` 种子
5. 医院 Excel 校对：上传测试账单，定价来自 DB 编译规则
6. `GET /api/v1/settings/default-pricing-template` 返回 v2.0 模板 JSON
7. `mvn test` — `PricingEngineTest`（13 项，1 项 Disabled）
