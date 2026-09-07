# Legacy billing phase seeds (archived 2026-09)

169 个 `phase-*.json` 增量种子已冻结归档。自 2026-09 起：

- **唯一可编辑源**：`billing-rules/baseline/{CODE}.json`
- **启动同步**：`BillingRulesBaselineSyncRunner`（`baseline_hash` 驱动）
- **禁止**在 `BillingSeedMigrationRunner.INCREMENTAL_SEEDS` 新增项

历史种子保留供审计与回滚参考，Runner 不再加载。
