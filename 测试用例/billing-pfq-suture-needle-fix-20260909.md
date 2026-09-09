# 平房人民缝合针/针盒规则修复（2026-09-09）

## 问题

`缝合针-2件/Z7520` 误命中院级「平房人民针盒针5合1含包材」→ 13.5 元；应为全局「通用缝合针按1件含包材」→ **8.0 元**。

## 修复

- [`backend/src/main/resources/billing-rules/baseline/PFQ-RM.json`](../backend/src/main/resources/billing-rules/baseline/PFQ-RM.json)：两条 FOLD 规则 keywords 仅保留 `针盒针@needle_box`，删除 `缝合针`
- manifest 已 `baseline_to_manifest.py --write` 同步

## 本地验收（无需 Docker）

```bash
cd backend && mvn -q test -Dtest=PricingEngineTest#pfqSutureNeedleTwoPieceUsesGlobalFoldNotNeedleBox,PricingEngineTest#pfqNeedleBoxFoldSubtractsBoxBeforeFiveInOne
python3 scripts/baseline_to_manifest.py --check
```

## API / 严格对账（需本地 stack 或生产 baseline sync 后）

```bash
python3 scripts/tmp_pfq_suture_needle_fix_verify.py
python3 scripts/special_v8_strict_excel_audit.py --hospital 平房区人民 --month 7 --batch 814 --out-date 20260909
```

**7 月严格对账预期**：原 3 条多报中 2 条 `缝合针-2件` EXTRA **消失**；`种植盒-36件` 仍为独立问题。

## 生产闭环

1. 部署后确认 `/api/.../version` 的 `rulesBaselineHash` 更新
2. `./bin/hospital-cli rules verify --all` 中 PFQ-RM keywords 与 manifest 一致（无 `缝合针`）
3. 对当前平房人民对账 Job **重新跑价**（历史快照不会自动更正）
4. `python3 scripts/rules_spot_check.py PFQ-RM`（生产 API）
