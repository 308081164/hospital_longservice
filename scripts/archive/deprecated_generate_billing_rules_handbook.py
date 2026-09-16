#!/usr/bin/env python3
"""Generate docs/账单规则全览手册.md from manifest + engine template."""

from __future__ import annotations

import json
import textwrap
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "backend/src/main/resources/billing-seeds/billing-rules-manifest.json"
REGISTRY = ROOT / "backend/src/test/resources/pricing-engine/rule-type-registry.json"
OUT = ROOT / "docs/账单规则全览手册.md"

SC11_TYPES: list[tuple[str, str, str, str]] = [
    ("SC11-T01", "按件×单价 + 无纺布加价", "冰城环钻包 7 件", "7×5.5+3=41.5 元（PER_INSTRUMENT + EXTRA_FEE）"),
    ("SC11-T02", "缝合针 1 件×5.5 + 标准包材", "电机厂缝合针 1 件/15cm", "5.5+2.5=8 元或固定 8 元"),
    ("SC11-T03", "「双」包少件按件+包材", "电机厂双包 ≤2 件", "n×5.5 + 纸塑袋价"),
    ("SC11-T03b", "低温「双」固定 35 元", "通用低温双包", "35 元/包（FIXED_PRICE, LT）"),
    ("SC11-T04", "小件 5 合 1 + 包材（≤10 件）", "方南南 P 钻 8 件", "ceil(8/5)×5.5+袋费"),
    ("SC11-T04b", "5 合 1 但单价 5.6", "东北农大根管锉 12 件", "ceil(12/5)×5.6+袋费"),
    ("SC11-T05", "小件 5 合 1，>10 件免包材", "克氏针 15 件", "ceil(15/5)×5.5=16.5"),
    ("SC11-T06", "10 合 1 + 包材", "祖研排针 15 件", "ceil(15/10)×5.5+袋费=16.5"),
    ("SC11-T07", "敷料包 W 码固定价", "驱血带 W90", "30 元/包"),
    ("SC11-T08", "包级固定单价", "九州方盘", "5.5 元/包（与件数无关）"),
    ("SC11-T09", "纸塑 ≥20cm 固定价", "工程大学孔巾 20cm", "4 元/包"),
    ("SC11-T10", "纸塑 <20cm 固定价", "通用敷料 15cm", "2.5 元/包"),
    ("SC11-T11", "低温小件 ≤5 按 1 件+包材", "海员胶帽 3 件", "1 件低温包装价"),
    ("SC11-T12", "低温强制 1 件一包", "春语塑料管", "标准低温包材价"),
    ("SC11-T13", "标准价上加收", "总工会 12°镜头", "标准价 +8 元"),
    ("SC11-T14", "双层袋 + 按件 5.5", "索菲面吸针 ≥3 件", "n×5.5（skipPackaging）"),
    ("SC11-T15", "不论件数一口价", "软镜 ETO/低温", "300 元/包"),
    ("SC11-T16", "环氧/低温件数阶梯+宽度 addon", "低温纸塑 5 件/20cm", "88 元（标准模板）"),
]

RULE_TYPE_CN = {
    "FIXED_PRICE": "固定单价",
    "PRICE_PER_INSTRUMENT": "按件计价",
    "FOLD": "件数折算",
    "EXTRA_FEE": "附加费",
    "ADD_FEE": "附加费",
    "MULTIPLIER": "倍数加价",
    "ZERO_PRICE_OVERRIDE": "零价覆盖",
}

MODE_CN = {
    "special_only": "仅特色（未命中保留原价）",
    "hybrid": "混合（未命中走标准灭菌阶梯价）",
    "standard": "标准灭菌表",
    None: "标准灭菌表（默认）",
}


def kw_list(rule: dict) -> list[str]:
    kws = rule.get("keywords") or []
    return [str(k) for k in kws]


def parse_conditions(conditions_json: str | None) -> list[str]:
    if not conditions_json:
        return []
    out: list[str] = []
    try:
        conds = json.loads(conditions_json)
        if isinstance(conds, list):
            for c in conds:
                if not isinstance(c, dict):
                    continue
                field = c.get("field")
                value = c.get("value")
                if field == "department":
                    if isinstance(value, list):
                        out.append("科室 ∈ {" + "、".join(str(v) for v in value) + "}")
                    elif value:
                        out.append(f"科室 = {value}")
                elif field == "originalUnitPrice":
                    out.append(f"账单单价 = {value} 元")
                elif field == "exportApply" and value:
                    out.append("仅导出账单阶段生效（exportApply）")
    except json.JSONDecodeError:
        pass
    return out


def rule_effect(rule: dict) -> str:
    rt = rule.get("ruleType") or "FIXED_PRICE"
    price = rule.get("price")
    fee = rule.get("fee")
    threshold = rule.get("threshold")
    fold = rule.get("foldRatio")
    if rt == "FIXED_PRICE" and price is not None:
        return f"命中后建议单价 = **{price:g} 元/包**（固定，不随件数变）"
    if rt == "PRICE_PER_INSTRUMENT" and price is not None:
        return f"命中后建议单价 = **计费件数 × {price:g} 元/件**"
    if rt == "FOLD" and threshold is not None and fold is not None:
        return (
            f"命中后先将器械数折算：≤{threshold} 件按 1 计费件；"
            f">{threshold} 件按 ceil(件数÷{fold:g}) 计费件，再乘标准单价"
        )
    if rt in ("EXTRA_FEE", "ADD_FEE") and fee is not None:
        return f"命中后在基础价上 **+{fee:g} 元**（附加费）"
    if rt == "ZERO_PRICE_OVERRIDE":
        return "命中后强制按零价导入规则处理（覆盖原价）"
    if price is not None:
        return f"命中后涉及价格 **{price:g} 元**"
    return "见引擎编译结果"


def rule_example(rule: dict, customer_name: str) -> str:
    rt = rule.get("ruleType") or "FIXED_PRICE"
    name = rule.get("name") or "规则"
    kws = kw_list(rule)
    pack_hint = kws[0] if kws else "某包"
    price = rule.get("price")
    fee = rule.get("fee")

    if rt == "FIXED_PRICE" and price is not None:
        return textwrap.dedent(
            f"""
            ```
            医院：{customer_name}
            包名：{pack_hint}-示例/Z7520
            账单价：{price + 10:g} 元（与规则价不符）
            → 引擎建议：{price:g} 元/包，status=warning
            ```
            """
        ).strip()
    if rt == "PRICE_PER_INSTRUMENT" and price is not None:
        n = rule.get("minInstrumentCount") or 5
        total = round(n * float(price), 2)
        extra = ""
        if rule.get("skipPackaging"):
            extra = "（规则配置不计包装费）"
        return textwrap.dedent(
            f"""
            ```
            医院：{customer_name}
            包名：{pack_hint}-{n}/Z7520，器械 {n} 件
            账单价：{total - 5:g} 元
            → 引擎建议：{n}×{price:g}={total:g} 元{extra}，status=warning
            ```
            """
        ).strip()
    if rt == "FOLD":
        return textwrap.dedent(
            f"""
            ```
            医院：{customer_name}
            包名：{pack_hint}-15/Z7520，器械 15 件
            → 先按 FOLD 折算计费件数，再套高温纸塑 5.5 元/件 + 袋费
            ```
            """
        ).strip()
    if rt in ("EXTRA_FEE", "ADD_FEE") and fee is not None:
        base = 8.5 if price is None else float(price)
        return textwrap.dedent(
            f"""
            ```
            医院：{customer_name}
            包名：{pack_hint}
            基础价 {base:g} 元 + 附加费 {fee:g} 元 → 合计 {base + float(fee):g} 元
            ```
            """
        ).strip()
    return f"（示例：包名含「{pack_hint}」时命中 `{name}`）"


def render_rule_block(cr_id: str, rule: dict, customer_code: str, customer_name: str, mode: str) -> str:
    rt = rule.get("ruleType") or "FIXED_PRICE"
    active = rule.get("isActive", True)
    status = "启用" if active else "已停用"
    kws = kw_list(rule)
    excludes = [str(x) for x in (rule.get("excludeKeywords") or [])]
    conds = parse_conditions(rule.get("conditionsJson"))
    lines = [
        f"#### {cr_id} · {rule.get('name', '(未命名)')}",
        "",
        "| 属性 | 值 |",
        "|------|-----|",
        f"| 客户 | {customer_name}（`{customer_code}`） |",
        f"| 规则类型 | `{rt}` — {RULE_TYPE_CN.get(rt, rt)} |",
        f"| 客户计价模式 | `{mode or 'standard'}` — {MODE_CN.get(mode, mode)} |",
        f"| 优先级 | {rule.get('priority', '—')}（越小越先匹配） |",
        f"| 状态 | **{status}** |",
        "",
        "**作用**",
        "",
        rule_effect(rule),
        "",
        "**生效条件**（须全部满足）",
        "",
    ]
    if kws:
        lines.append(f"- 包名/类型/包材合并文本 **包含任一关键词**：{'、'.join(kws)}")
    else:
        lines.append("- 无关键词限制（通常绑定产品 ID 或全局）")
    if excludes:
        lines.append(f"- **排除**关键词：{'、'.join(excludes)}")
    if rule.get("minInstrumentCount") is not None:
        lines.append(f"- 器械件数 ≥ {rule['minInstrumentCount']}")
    if rule.get("maxInstrumentCount") is not None:
        lines.append(f"- 器械件数 ≤ {rule['maxInstrumentCount']}")
    if rule.get("temperature"):
        lines.append(f"- 灭菌类型 = `{rule['temperature']}`（HT=高温，LT=低温）")
    if rule.get("minBagSizeInclusive") is not None:
        lines.append(f"- 纸塑袋宽 ≥ {rule['minBagSizeInclusive']} cm")
    if rule.get("maxBagSizeExclusive") is not None:
        lines.append(f"- 纸塑袋宽 < {rule['maxBagSizeExclusive']} cm")
    for c in conds:
        lines.append(f"- {c}")
    if rule.get("skipPackaging"):
        lines.append("- 命中后 **不计包装/袋材费**")
    if rule.get("skipDiscount"):
        lines.append("- 命中后 **不计客户折扣**")
    lines.extend(["", "**示例**", "", rule_example(rule, customer_name), ""])
    return "\n".join(lines)


def build_sc11_chapter(registry: dict | None) -> list[str]:
    lines = [
        "## 第 6 章 · 特殊收费(11) 业务分类（SC11-*）",
        "",
        "> Excel 源文件 [`特殊收费(11).xlsx`](source/特殊收费(11).xlsx) 共 **107** 条语义条目（53 院级 + 22 通用 + 32 阶梯），",
        "> 编译落库后对应第 5 章 CR-* 与第 2 章 STD-*。下列 **18** 种 SC11 类型为业务口径分类编号。",
        "",
    ]
    counts = (registry or {}).get("sc11TypeCounts") or {}
    for i, (tid, title, cond, example) in enumerate(SC11_TYPES, 1):
        cnt = counts.get(tid, "—")
        lines.extend(
            [
                f"### {tid} · {title}",
                "",
                f"**Registry 条目数**：{cnt}",
                "",
                "**作用**：将 Excel 自然语言规则映射为 seed `ruleType` 组合，供引擎编译执行。",
                "",
                f"**典型生效场景**：{cond}",
                "",
                "**示例**：",
                "```",
                example,
                "```",
                "",
            ]
        )
    return lines


def build_handbook(manifest: dict, registry: dict | None = None) -> str:
    customers: dict[str, Any] = manifest.get("customers") or {}
    all_rules: list[tuple[str, dict, dict]] = []
    for code in sorted(customers):
        entry = customers[code]
        for rule in entry.get("productRules") or []:
            all_rules.append((code, entry, rule))

    active_rules = [(c, e, r) for c, e, r in all_rules if r.get("isActive", True)]
    inactive_rules = [(c, e, r) for c, e, r in all_rules if not r.get("isActive", True)]
    type_counts: dict[str, int] = {}
    for _, _, r in active_rules:
        t = r.get("ruleType") or "UNKNOWN"
        type_counts[t] = type_counts.get(t, 0) + 1

    generated = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    mhash = (manifest.get("manifest_hash") or "")[:12]
    active_n = len(active_rules)
    cr_end = f"CR-{active_n:03d}"

    lines: list[str] = [
        "# 账单规则全览手册",
        "",
        "> 本文档说明系统内**全部计价规则**：标准灭菌默认表、全局内置特色规则、以及各客户 `CustomerProductRule` 落库规则。",
        f"> 数据来自 [`billing-rules-manifest.json`](../backend/src/main/resources/billing-seeds/billing-rules-manifest.json)（hash `{mhash}…`），生成时间 {generated}。",
        "> 重新生成：`python3 scripts/generate_billing_rules_handbook.py`",
        "",
        "---",
        "",
        "## 目录",
        "",
        "| 章节 | 内容 | 条数 |",
        "|------|------|------|",
        "| [第 0 章](#第-0-章--规则总量一览) | 规则总量 | — |",
        "| [第 1 章](#第-1-章--计价模式与执行顺序) | 计价模式与顺序 | 3 节 |",
        "| [第 2 章](#第-2-章--标准灭菌规则全局默认-std-) | STD-01～STD-12 | **12** |",
        "| [第 3 章](#第-3-章--特色规则类型说明type-) | TYPE-01～TYPE-06 | **6** |",
        "| [第 4 章](#第-4-章--全局内置特色规则glb-) | GLB-01 | **1** |",
        f"| [第 5 章](#第-5-章--客户特色规则完整索引cr-001cr-{active_n:03d}) | CR-001～{cr_end} | **{active_n}** |",
        "| [第 6 章](#第-6-章--特殊收费11-业务分类sc11-) | SC11-T01～T16 | **18** |",
        "| [附录 A](#附录-a--已停用客户规则) | 已停用 CR | 55 |",
        "",
        "**一眼总数**：启用客户规则 **300** + 标准规则 **12** + 全局特色 **1** = **313 条可执行计价规则**；另 SC11 业务分类 **18** 种。",
        "",
        "---",
        "",
        "## 第 0 章 · 规则总量一览",
        "",
        "| 层级 | 数量 | 说明 |",
        "|------|------|------|",
        f"| **客户特色规则（启用）** | **{active_n}** | 本文 **CR-001～{cr_end}** |",
        f"| 客户特色规则（已停用） | {len(inactive_rules)} | 附录 A |",
        f"| 涉及客户（有规则） | {len({c for c, _, _ in all_rules})} | 其中启用特色计价 {len({c for c, e, _ in active_rules if e.get('billingPricingMode') in ('special_only', 'hybrid')})} 家 |",
        "| **标准灭菌规则（全局默认）** | **12 项** | 第 2 章 STD-01～STD-12 |",
        "| **全局内置特色规则** | **1 条** | 第 4 章 GLB-01 软镜 300 元 |",
        "| **特色规则类型（机制）** | **6 种** | 第 3 章 TYPE-01～TYPE-06 |",
        "| **SC11 业务分类** | **18 种** | 第 6 章（Excel 语义索引） |",
        "",
        "**启用规则按类型分布**",
        "",
        "| 类型编号 | ruleType | 中文 | 启用条数 |",
        "|----------|----------|------|----------|",
    ]

    type_order = [
        ("TYPE-01", "FIXED_PRICE", "固定单价"),
        ("TYPE-02", "PRICE_PER_INSTRUMENT", "按件计价"),
        ("TYPE-03", "FOLD", "件数折算"),
        ("TYPE-04", "EXTRA_FEE", "附加费"),
        ("TYPE-05", "ZERO_PRICE_OVERRIDE", "零价覆盖"),
        ("TYPE-06", "MULTIPLIER", "倍数加价"),
    ]
    for tid, rt, cn in type_order:
        lines.append(f"| {tid} | `{rt}` | {cn} | {type_counts.get(rt, 0)} |")

    lines.extend(
        [
            "",
            "---",
            "",
            "## 第 1 章 · 计价模式与执行顺序",
            "",
            "### 1.1 三种客户计价模式",
            "",
            "| 模式 | 编号 | 未命中特色规则时 | 典型客户 |",
            "|------|------|------------------|----------|",
            "| `special_only` | MODE-01 | **保留账单单价**，status 多为 unchanged | 冰城医美 |",
            "| `hybrid` | MODE-02 | **走标准灭菌价**（第 2 章 STD-*，含 override） | 电机厂、祖研南岗 |",
            "| `standard` | MODE-03 | **走标准灭菌价**（第 2 章 STD-*） | 多数普通医院 |",
            "",
            "### 1.2 单行账单处理顺序（简化）",
            "",
            "```",
            "1. billingEnabled=false → 保留原价（特色账单已关闭）",
            "2. 匹配客户 ZERO_PRICE_OVERRIDE / 固定价 / 折算 / 附加费",
            "3. 若 special_only 且未命中 → 保留原价",
            "4. 若 hybrid / standard 且未命中 → 标准高温/低温/敷料路径（STD-*）",
            "5. 比较账单价与建议价 → warning / unchanged",
            "```",
            "",
            "### 1.3 规则匹配通用条件",
            "",
            "每条客户规则（CR-*）在匹配时还检查：",
            "",
            "- 医院名与客户规范名/别名一致",
            "- 包名 + 类型 + 包材 合并文本含关键词（无关键词则靠产品绑定）",
            "- 器械件数、袋宽、温度（HT/LT）区间",
            "- 科室、原价等 `conditionsJson` 附加条件",
            "",
            "---",
            "",
            "## 第 2 章 · 标准灭菌规则（全局默认 STD-*）",
            "",
            "当客户为 **`standard` / `hybrid`** 且未命中特色规则时生效（走高温/低温/敷料标准路径，含 `standardPricingOverride` 深合并）。",
            "**`special_only` 未命中时不走本章**（引擎保留账单单价）。",
            "源码：[`DefaultPricingTemplate.java`](../backend/src/main/java/com/hospital/backend/service/DefaultPricingTemplate.java)。",
            "",
            "### STD-01 · 高温纸塑袋按件 + 袋材费",
            "",
            "**作用**：额外包/纸塑袋高温灭菌的默认计价。",
            "",
            "**生效**：类型或包材含「纸塑袋」且非无纺布、非敷料特例。",
            "",
            "**公式**：`ceil(计费件数/5)×5.5 + 袋宽对应袋价`；≥3 件时可按 `件数×5.5+袋价`。",
            "",
            "**袋宽价**：10cm→2.5，15cm→5.5，20cm→7.5，25cm→10.5 元。",
            "",
            "**示例**：",
            "```",
            "包名：缝合针-1/Z7520，纸塑 15cm，1 件",
            "→ 5.5 + 5.5 = 11.0 元（或 minCharge 16.5 封顶场景另计）",
            "```",
            "",
            "### STD-02 · 高温纸塑最低消费 16.5",
            "",
            "**作用**：单包纸塑高温最低收费。",
            "",
            "**生效**：STD-01 路径下，折算后单价低于 16.5 时抬到 16.5。",
            "",
            "**示例**：2 件小件纸塑包 → 常落到 **16.5 元/包**。",
            "",
            "### STD-03 · 高温无纺布按件",
            "",
            "**作用**：器械包(ZSD)/无纺布高温灭菌。",
            "",
            "**生效**：包材或类型含「无纺布」。",
            "",
            "**公式**：≤2 件 often 16.5；≥3 件 `件数×5.5`（见引擎 `computeHighTempNonWoven`）。",
            "",
            "### STD-04 · 低温纸塑袋阶梯（按件）",
            "",
            "**作用**：低温/ETO/EO 纸塑袋灭菌。",
            "",
            "**袋宽底价**：10cm→22，15→25，20→28，25→30，30→35 元。",
            "",
            "**件数阶梯**：5 件→88，10 件→165，20 件→300；中间按 `remainderPerPiecePrice=22` 补差。",
            "",
            "**示例**：",
            "```",
            "单包装 5 件/20cm 低温纸塑 → 88 元",
            "```",
            "",
            "### STD-05 · 低温无纺布阶梯",
            "",
            "同 STD-04 件数阶梯；单件最低 35 元。",
            "",
            "### STD-06 · 敷料包（无纺布）按 W 码分档",
            "",
            "**作用**：棉球/纱布/驱血带等敷料包。",
            "",
            "**生效**：类型含「敷料包」且识别 W60/W90/W120 等规格。",
            "",
            "| W 码 | 单价 |",
            "|------|------|",
            "| W50/W60/W70 | 25 元 |",
            "| W90 | 30 元 |",
            "| W120/W150 | 35 元 |",
            "",
            "**示例**：驱血带 W90 → **30 元/包**。",
            "",
            "### STD-07 · 敷料包（纸塑）棉球/孔巾小袋",
            "",
            "**作用**：纸塑敷料小袋固定价。",
            "",
            "**生效**：敷料包+纸塑袋，袋宽 15cm→2.5，20cm→4 元。",
            "",
            "### STD-08 · 全局针类/小件 5 合 1 折算",
            "",
            "**作用**：包名含「针/小件/车针…」时，5 件算 1 计费件（全局 needle 配置）。",
            "",
            "**生效**：无客户 FOLD 优先命中时，匹配 `DefaultPricingTemplate.needle.keywords`。",
            "",
            "**示例**：12 件机扩针 → ceil(12/5)=3 计费件 → 3×5.5+袋费。",
            "",
            "### STD-09 · 包名「针N」拆分规则",
            "",
            "**作用**：包名形如「AR水管-2件/双/Z1530」中「针N」参与折算。",
            "",
            "### STD-10 · 物流费（结款函，非单行单价）",
            "",
            "**作用**：按趟 50 元物流（`logistics.enabled`）。",
            "",
            "### STD-11 · 结款函模板",
            "",
            "结算单导出格式，不参与 `processRow` 单价。",
            "",
            "### STD-12 · 账单导入清洗",
            "",
            "删除合计行、修剪包材列等；不参与单价计算。",
            "",
            "---",
            "",
            "## 第 3 章 · 特色规则类型说明（TYPE-*）",
            "",
            "### TYPE-01 · FIXED_PRICE（固定单价）",
            "",
            "**作用**：无论件数多少，命中后建议单价为固定值（元/包）。",
            "",
            "**生效**：关键词 + 条件匹配；客户规则优先于全局规则。",
            "",
            "**示例**（电机厂 CR 类）：",
            "```",
            "缝合针-8/Z7520 → 固定 8.0 元",
            "```",
            "",
            "### TYPE-02 · PRICE_PER_INSTRUMENT（按件计价）",
            "",
            "**作用**：`建议单价 = 计费件数 × 单价`；常配合 `skipPackaging=true`。",
            "",
            "**示例**（冰城医美）：",
            "```",
            "环钻包 10 件 → 10×5.5=55 + EXTRA_FEE 加价 → 见 CR 条目",
            "```",
            "",
            "### TYPE-03 · FOLD（件数折算）",
            "",
            "**作用**：将器械数按 threshold/foldRatio 折算成更少「计费件」后再乘 5.5/5.6。",
            "",
            "**公式**：件数 ≤ threshold → 1 计费件；否则 `ceil(件数/foldRatio)`。",
            "",
            "**示例**（祖研排针）：",
            "```",
            "排针 15 件 → ceil(15/10)=2 计费件 → 2×5.5+袋费",
            "```",
            "",
            "### TYPE-04 · EXTRA_FEE / ADD_FEE（附加费）",
            "",
            "**作用**：在按件价或固定价基础上 **+fee 元**（如无纺布加价、包装加价）。",
            "",
            "**示例**（冰城环钻）：",
            "```",
            "环钻包：5.5×件数 + 3（无纺布）+ 3（小件包装，2-5 件）",
            "```",
            "",
            "### TYPE-05 · ZERO_PRICE_OVERRIDE（零价覆盖）",
            "",
            "**作用**：对特定包强制按规则价覆盖零价/异常导入。",
            "",
            "### TYPE-06 · MULTIPLIER（倍数加价）",
            "",
            "**作用**：在基础价上乘 multiplier（当前 manifest 中较少使用）。",
            "",
            "---",
            "",
            "## 第 4 章 · 全局内置特色规则（GLB-*）",
            "",
            "### GLB-01 · 软镜固定 300 元",
            "",
            "| 属性 | 值 |",
            "|------|-----|",
            "| 来源 | `DefaultPricingTemplate.specialRules.fixedPrices` |",
            "| 关键词 | 软镜 |",
            "| 价格 | **300 元/包** |",
            "",
            "**示例**：",
            "```",
            "包名：软镜包-5，任意件数 → 300 元",
            "```",
            "",
            "---",
            "",
            f"## 第 5 章 · 客户特色规则完整索引（CR-001～{cr_end}）",
            "",
            f"以下 **{active_n}** 条为当前 **启用** 的客户规则，按客户编码字母序、同客户内按优先级排列。",
            "",
            "### 5.0 客户速查表",
            "",
            "| 客户 | 编码 | 模式 | 启用条数 | 首条 CR |",
            "|------|------|------|----------|---------|",
        ]
    )

    cr = 1
    customer_groups: list[tuple[str, dict, list]] = []
    grouped: dict[str, list] = {}
    for code, entry, rule in sorted(
        active_rules, key=lambda x: (x[0], x[2].get("priority") or 100, x[2].get("name") or "")
    ):
        grouped.setdefault(code, []).append((entry, rule))
    for code in sorted(grouped):
        entry = grouped[code][0][0]
        rules = [r for _, r in grouped[code]]
        customer_groups.append((code, entry, rules))
        name = entry.get("name") or code
        mode = entry.get("billingPricingMode") or "standard"
        anchor = (rules[0].get("name") or "rule").replace(" ", "-")
        lines.append(
            f"| {name} | `{code}` | `{mode}` | {len(rules)} | [CR-{cr:03d}](#cr-{cr:03d}-{anchor}) |"
        )
        cr += len(rules)

    lines.extend(["", "---", ""])

    cr = 1
    cust_idx = 1
    for code, entry, rules in customer_groups:
        name = entry.get("name") or code
        mode = entry.get("billingPricingMode")
        lines.extend(
            [
                f"### 5.{cust_idx} 客户 · {name}（`{code}`）",
                "",
                f"- 计价模式：`{mode or 'standard'}` — {MODE_CN.get(mode, MODE_CN[None])}",
                f"- 启用规则：{len(rules)} 条（CR-{cr:03d}～CR-{cr + len(rules) - 1:03d}）",
                "",
            ]
        )
        cust_idx += 1
        for rule in rules:
            cr_id = f"CR-{cr:03d}"
            lines.append(render_rule_block(cr_id, rule, code, name, mode))
            cr += 1

    lines.extend(build_sc11_chapter(registry))
    lines.extend(
        [
            "---",
            "",
            f"## 附录 A · 已停用客户规则（{len(inactive_rules)} 条）",
            "",
            "<details><summary>点击展开</summary>",
            "",
        ]
    )
    for code, entry, rule in sorted(inactive_rules, key=lambda x: (x[0], x[2].get("name") or "")):
        name = entry.get("name") or code
        lines.append(f"- `{code}` · {rule.get('name')} · {RULE_TYPE_CN.get(rule.get('ruleType'), rule.get('ruleType'))}")
    lines.extend(["", "</details>", ""])

    return "\n".join(lines)


def main() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    registry = json.loads(REGISTRY.read_text(encoding="utf-8")) if REGISTRY.exists() else None
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(build_handbook(manifest, registry), encoding="utf-8")
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()
