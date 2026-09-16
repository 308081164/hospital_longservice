# 已废止：期待价格校正种子（禁止复用）

> **2026-09-16 决策**：全部「校正价」规则非法，已从 baseline/manifest 彻底删除。
> 本目录种子**仅作历史审计**，**禁止**重新加入 `BillingSeedMigrationRunner.INCREMENTAL_SEEDS` 或任何落库路径。

## 目录内容

| 文件 | 说明 |
|------|------|
| `phase-batch-p0.json` | 2026-07-21 22 院「6 月期待价格校正」反推种子（含 ZY3 等 73 条校正价） |
| `phase-batch-p0.1.json` | P0 补充批次 |
| `phase-batch-p0.2.json` | P0 补充批次 |

## 业务来源（已废止，不可作为权威）

- `测试用例/批量6月期待价格校正索引.md`（已标记 OBSOLETE）
- 各院 `6月期待价格校正清单.csv`

权威来源仅为《特殊收费》Excel（`docs/source/特殊收费(*).xlsx`）。

## 处置记录

- 2026-09-08：`phase-correction-price-delete-all-20260908.json` 删除生产 DB 全部 85 条校正价
- 2026-09-15：ZY3 四条以 `电力校正价*` 改名写回 baseline（错误复活）
- 2026-09-16：再次删除 ZY3 四条；P0 种子移入本目录
