# Legacy billing phase seeds (archived 2026-09)

169 个 `phase-*.json` 增量种子已冻结归档。自 2026-09 起：

- **唯一可编辑源**：`billing-rules/baseline/{CODE}.json`
- **启动同步**：`BillingRulesBaselineSyncRunner`（`baseline_hash` 驱动）
- **禁止**在 `BillingSeedMigrationRunner.INCREMENTAL_SEEDS` 新增项

历史种子保留供审计与回滚参考，Runner 不再加载。

## 校正价种子（已废止，禁止复用）

2026-09-16 起，全部「校正价」规则非法。P0 期待价格校正种子已移至：

`archive/legacy-2026-obsolete-correction-price/`

该目录内种子**禁止**重新落库。`legacy-2026/` 内其余文件若含历史 `校正价*` 条目，亦仅作删除审计参考，不得作为规则来源。
