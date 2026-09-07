# billing-seeds（历史归档）

自 2026-09 起，**活跃配置路径已迁移**至：

- **运行时唯一真相源**：数据库 `customer_product_rule`
- **Git 期望态**：`backend/src/main/resources/billing-rules/baseline/{CODE}.json` + `index.json`

本目录下 `phase-*.json` 与 `billing-rules-manifest.json` **仅作历史参考**，禁止新增增量种子。
新规则变更请：改 baseline JSON（PR）或 UI 单条编辑 → `hospital-cli rules import` / `rules verify`。
