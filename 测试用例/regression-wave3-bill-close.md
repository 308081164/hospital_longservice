# 波次3 收尾 · Bill regression 根因（2026-07-28）

## 黑龙江省医院（香坊院区）Job617 · Δ769

**现象**：baseline pass（总额 Δ13.2）→ 波次3 fail（export 177750 vs 处理后 178518.9，Δ768.9）。

**行级 diff**：
- 2975 行 key 数量一致，80 行单价 key 错位
- 典型：`胸腔镜-21` 在处理后表为 **180 元**，export 被改为 **19.8 元**

**根因**：`ExportFixedPriceApplier` 无差别套用 fixedPrices，未校验科室/sheet、未排除 `-21`、未信任 `correctedTotalPrice`。

**修复**：导出 applier 增加条件校验 + seed exclude `-21` + 默认跳过已有 correctedTotalPrice。

---

## 黑龙江省第二医院（南岗院区）Job641 · Δ13.2

**现象**：baseline pass（总额一致，行聚合差）→ fail（969.4 vs 956.2）。

**根因**：挖勺 5.5 固定价覆盖 DB 7 折后价（3.85/把）；7 折为 after_base 不在 export 二次应用。

**修复**：有 correctedTotalPrice 时跳过非 exportApply 规则。

---

## 哈尔滨市呼兰区第一人民医院 Job643 · Δ1001.7

**现象**：baseline pass（总额一致，154 vs 423 行）→ fail（9192.4 vs 10194.1）。

**根因**：PDF 固定价规则在 export 覆盖 reconcile 已写入的 7 折价。

**修复**：默认信任 correctedTotalPrice。
