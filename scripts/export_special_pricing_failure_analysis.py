#!/usr/bin/env python3
"""Generate dated Markdown failure analysis from 814 strict audit JSON."""

from __future__ import annotations

import argparse
import json
from datetime import date
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
REPORT_JSON = ROOT / "测试用例" / "814新增严格Excel对账报告-20260814.json"
OUT = ROOT / "测试用例" / "特殊计价医院严格对账失败分析-20260814.md"
MANIFEST = ROOT / "backend/src/main/resources/billing-seeds/billing-rules-manifest.json"

HOSPITAL_META: dict[str, dict[str, str]] = {
    "哈尔滨冰城医疗美容医院": {
        "label": "冰城医美",
        "code": "BINGCHENG-YM",
        "billing_mode": "special",
    },
    "国药总医院第二院区": {
        "label": "电机厂",
        "code": "GUOYAO-2",
        "billing_mode": "hybrid",
    },
    "黑龙江菁华上德生殖妇产医院": {
        "label": "上德",
        "code": "SHANGDE-FC",
        "billing_mode": "special_only",
    },
    "黑龙江九洲妇科医院": {
        "label": "九州",
        "code": "JIUZHOU-FK",
        "billing_mode": "standard+折扣",
    },
    "祖研-黑龙江省中医医院（南岗院区）": {
        "label": "祖研南岗",
        "code": "ZUYAN-NG",
        "billing_mode": "special",
    },
}

ROOT_CAUSE: dict[str, str] = {
    "哈尔滨冰城医疗美容医院": (
        "v8 规则未完全部署：处理后 ground truth 为环钻按件 5.5+无纺布加价 3=33.5，"
        "本地仍命中旧规则 `环钻27.5`；漏检 2 条 + 多报 1 条。"
    ),
    "国药总医院第二院区": (
        "标准计费误报：7 月原始与处理后单价一致（E=0），系统仍对 4 条出 warning，"
        "命中默认规则 `高温纸塑袋10cm计费` / `敷料包(无纺布包)驱血带——2.0`。"
    ),
    "黑龙江菁华上德生殖妇产医院": (
        "规则缺失 + 标准计费误报：2 条客户改价无 warning（漏检）；"
        "4 条未改价行被默认/阶梯规则误报（多报）。客户尚未建档 billing seed。"
    ),
    "黑龙江九洲妇科医院": (
        "折扣策略误报：6 月原始与处理后一致（E=0），系统对 11 条仍出 warning，"
        "模式为「标准高温/纸塑计费 + 黑龙江九洲妇科医院 折扣（50%）」。"
    ),
    "祖研-黑龙江省中医医院（南岗院区）": (
        "标准/默认规则误报：23 条期待校正均命中，额外 2 条美容科未改价行被 "
        "`高温纸塑袋15cm计费` 误报；warning 集合存在 strict key 重复。"
    ),
}

V8_RULE_NOTE = (
    "manifest/seed 预期（phase-special-v8-rules-20260814）：停用 `环钻27.5`，"
    "启用 `冰城环钻包按件5.5` + `冰城环钻包无纺布加价3`。"
    "本地 verify-deploy spot-check 仍命中 `环钻27.5` → 27.5，说明 v8 incremental seed 未完全落库。"
)


def load_fail_results(path: Path) -> list[dict[str, Any]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    fails: list[dict[str, Any]] = []
    for section in data.get("sections", {}).values():
        for r in section:
            if r.get("status") == "FAIL":
                fails.append(r)
    return fails


def fmt_price(v: Any) -> str:
    if v is None or v == "":
        return "—"
    return str(v)


def diff_rows_table(rows: list[dict[str, Any]]) -> list[str]:
    if not rows:
        return ["（无）", ""]
    lines = [
        "| 差异 | 科室 | 发货单号 | 包名 | 包数 | 原单价 | 处理后 | 系统 ruleUnit | 命中规则 |",
        "|------|------|---------|------|------|--------|--------|--------------|----------|",
    ]
    type_label = {"MISSED": "漏检", "EXTRA": "多报", "PRICE_MISMATCH": "价差"}
    for d in rows:
        lines.append(
            f"| {type_label.get(d.get('diff_type', ''), d.get('diff_type', ''))} | "
            f"{d.get('sheet', '')} | {d.get('ship_no', '')} | {d.get('pack_name', '')} | "
            f"{d.get('pack_count', '')} | {fmt_price(d.get('raw_unit'))} | "
            f"{fmt_price(d.get('proc_unit'))} | {fmt_price(d.get('system_unit'))} | "
            f"{d.get('pricing_rule') or '—'} |"
        )
    lines.append("")
    return lines


def render_hospital_section(r: dict[str, Any]) -> list[str]:
    hospital = r["hospital"]
    meta = HOSPITAL_META.get(hospital, {"label": hospital, "code": "—", "billing_mode": "—"})
    month = r.get("month", "—")
    lines = [
        f"## {meta['label']}（{hospital}）· {month}月",
        "",
        f"- **客户代码**：`{meta['code']}`",
        f"- **计费模式**：{meta['billing_mode']}",
        f"- **Job ID**：{r.get('job_id', '—')}",
        f"- **材料**：`{r.get('raw_file', '—')}` / `{r.get('proc_file', '—')}`",
        f"- **结论**：{r.get('message', '')}（E={r.get('expected_count', 0)}，W={r.get('warning_count', 0)}）",
        "",
        "### 根因",
        "",
        ROOT_CAUSE.get(hospital, "见下表逐条差异。"),
        "",
    ]
    if hospital == "哈尔滨冰城医疗美容医院":
        lines.extend(["### 规则层面", "", V8_RULE_NOTE, ""])

    all_diffs = (
        list(r.get("missed") or [])
        + list(r.get("extra") or [])
        + list(r.get("price_mismatch") or [])
    )
    lines.append("### 逐条记录")
    lines.append("")
    lines.extend(diff_rows_table(all_diffs))

    if r.get("dedupe_note"):
        lines.extend([f"**dedupe**：{r['dedupe_note']}", ""])

    return lines


def build_markdown(report_path: Path) -> str:
    fails = load_fail_results(report_path)
    generated = date.today().isoformat()
    order = [
        "哈尔滨冰城医疗美容医院",
        "国药总医院第二院区",
        "黑龙江菁华上德生殖妇产医院",
        "黑龙江九洲妇科医院",
        "祖研-黑龙江省中医医院（南岗院区）",
    ]
    by_hospital = {r["hospital"]: r for r in fails}
    ordered = [by_hospital[h] for h in order if h in by_hospital]
    for r in fails:
        if r["hospital"] not in order:
            ordered.append(r)

    lines = [
        "# 特殊计价医院严格对账失败分析",
        "",
        f"> 生成日期：{generated}",
        f"> 数据来源：[`814新增严格Excel对账报告-20260814.json`](814新增严格Excel对账报告-20260814.json)",
        "> 判定标准：E/W/P 三方、strict key 含科室、单价 Decimal 零容差",
        "",
        "## 总览",
        "",
        f"共 **{len(ordered)}** 家 FAIL（7 月 3 家 + 6 月 2 家）。",
        "",
        "| 院名 | 账期 | E | W | 漏检 | 多报 | 价差 | Job |",
        "|------|------|---|---|------|------|------|-----|",
    ]
    for r in ordered:
        meta = HOSPITAL_META.get(r["hospital"], {"label": r["hospital"]})
        lines.append(
            f"| {meta['label']} | {r.get('month')}月 | {r.get('expected_count')} | "
            f"{r.get('warning_count')} | {len(r.get('missed') or [])} | "
            f"{len(r.get('extra') or [])} | {len(r.get('price_mismatch') or [])} | "
            f"{r.get('job_id')} |"
        )
    lines.extend(["", "---", ""])

    for r in ordered:
        lines.extend(render_hospital_section(r))
        lines.extend(["---", ""])

    return "\n".join(lines).rstrip() + "\n"


def main() -> int:
    p = argparse.ArgumentParser(description="导出特殊计价 FAIL 深度分析 Markdown")
    p.add_argument("--write", action="store_true")
    p.add_argument("--report", type=Path, default=REPORT_JSON)
    p.add_argument("--out", type=Path, default=OUT)
    args = p.parse_args()
    if not args.report.is_file():
        print(f"报告不存在: {args.report}", file=__import__("sys").stderr)
        return 1
    text = build_markdown(args.report)
    if args.write:
        args.out.write_text(text, encoding="utf-8")
        print(f"已写入: {args.out}")
    else:
        print(text[:2000])
        print("…（使用 --write 写入完整文件）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
