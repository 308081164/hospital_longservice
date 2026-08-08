#!/usr/bin/env python3
"""合并 local/prod S8 bill 报告与材料清单，生成三环境逐院验收 Markdown。"""

from __future__ import annotations

import argparse
import json
import re
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE = ROOT / "测试用例"
DATE_TAG = datetime.now().strftime("%Y%m%d")

import sys

sys.path.insert(0, str(ROOT / "scripts"))
from batch_june_price_reconciliation import TODO_HOSPITALS  # noqa: E402

MATERIAL_BLOCKED = frozenset(
    {
        "国药总医院主院区",
        "国药总医院第二院区",
        "哈尔滨市第二医院",
        "黑龙江省第二医院（松北院区）",
    }
)

MATERIAL_NOTES: dict[str, str] = {
    "国药总医院主院区": "kit BOM + 原始行未入库 · S8 Δ696",
    "国药总医院第二院区": "同上模式 · S8 Δ121.5",
    "哈尔滨市第二医院": "6月 vendor 补录 7 sheet 缺失",
    "黑龙江省第二医院（松北院区）": "part3/vendor + kit 拆行 · Δ8743",
    "哈尔滨工程大学医院": "5月账期已验收 · 6月主矩阵例外",
    "哈尔滨工业大学医院": "S8 warn 登记已知差 Δ104.5",
}

PASS_STATUSES = frozenset({"pass", "warn"})


def load_report(path: Path) -> dict[str, dict]:
    if not path.is_file():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        return {}
    return {r["folder"]: r for r in data if r.get("folder")}


def load_job_map(path: Path) -> dict[str, int]:
    if not path.is_file():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    return {k: int(v) for k, v in data.get("jobs", {}).items()}


def parse_s1_from_todo(todo_md: Path) -> dict[str, str]:
    """从看板表解析 S1 列（✅/🚫/⏭ 等）。"""
    out: dict[str, str] = {}
    if not todo_md.is_file():
        return out
    alias = {
        "祖研（南岗院区）": "祖研-黑龙江省中医医院（南岗院区）",
        "祖研（三辅院区）": "祖研-黑龙江省中医医院（三辅院区）",
        "祖研（香安院区）": "祖研-黑龙江省中医医院（香安院区）",
        "中医附二（南岗）": "黑龙江中医药大学附属第二医院（南岗）",
        "中医附二（哈南分院）": "黑龙江中医药大学附属第二医院（哈南分院）",
        "省二（南岗院区）": "黑龙江省第二医院（南岗院区）",
        "省二（松北院区）": "黑龙江省第二医院（松北院区）",
    }
    for line in todo_md.read_text(encoding="utf-8").splitlines():
        if not line.startswith("|") or "---" in line or "医院" in line and "S1" in line:
            continue
        parts = [p.strip() for p in line.strip("|").split("|")]
        if len(parts) < 10:
            continue
        name = parts[1].strip()
        if name in ("—", "") or name.startswith("#"):
            continue
        name = alias.get(name, name)
        s1 = parts[2].strip()
        if name and s1:
            out[name] = s1
    return out


def fmt_totals(entry: dict | None) -> str:
    if not entry:
        return "—"
    totals = entry.get("totals") or {}
    exp = totals.get("expected")
    act = totals.get("actual")
    if exp is None and act is None:
        return entry.get("detail", "—")[:80]
    if exp is not None and act is not None:
        delta = round(float(act) - float(exp), 2)
        sign = f"Δ{delta:+.2f}" if abs(delta) >= 0.01 else "≈"
        return f"{act}/{exp} ({sign})"
    return "—"


def fmt_lines(entry: dict | None) -> str:
    if not entry or not entry.get("detail"):
        return "—"
    m = re.search(r"行\s*([\d.]+)\s*/\s*([\d.]+)", entry["detail"])
    if m:
        return f"{m.group(1)}/{m.group(2)}"
    m2 = re.search(r"行\s*([\d.]+)\s+vs\s+([\d.]+)", entry["detail"])
    if m2:
        return f"{m2.group(1)}/{m2.group(2)}"
    return "—"


def classify_overall(
    local: dict | None,
    prod: dict | None,
    has_prod_job: bool,
) -> tuple[str, str]:
    """返回 (结论, 原因摘要)。"""
    local_st = (local or {}).get("status", "pending")
    prod_st = (prod or {}).get("status", "pending")
    folder = (local or prod or {}).get("folder", "")

    if local_st == "skip":
        return "skip", (local or {}).get("detail", "本地 skip")

    local_ok = local_st in PASS_STATUSES
    if not has_prod_job:
        if local_ok:
            return "pass_local_only", "生产无 Job（coverage_gap）· 本地对齐期待"
        if folder in MATERIAL_BLOCKED:
            return "fail", f"材料阻塞 · {(local or {}).get('detail', '')[:120]}"
        return "fail", (local or {}).get("detail", "本地未对齐期待")[:160]

    prod_ok = prod_st in PASS_STATUSES
    if local_ok and prod_ok:
        return "pass", "本地与生产均对齐期待"
    if local_ok and not prod_ok:
        return "fail_prod_lag", f"本地 pass · 生产 {prod_st} · {(prod or {}).get('detail', '')[:100]}"
    if not local_ok and prod_ok:
        return "fail_local", f"本地 {local_st} · 生产 pass · {(local or {}).get('detail', '')[:100]}"
    if folder in MATERIAL_BLOCKED:
        return "fail", f"材料阻塞 · 双环境 fail · {(local or {}).get('detail', '')[:80]}"
    return "fail", f"本地 {local_st} · 生产 {prod_st}"


def build_report(
    local_path: Path,
    prod_path: Path,
    stable_jobs: Path,
    prod_jobs: Path,
    todo_md: Path,
    out_path: Path,
) -> dict:
    local = load_report(local_path)
    prod = load_report(prod_path)
    stable_map = load_job_map(stable_jobs)
    prod_map = load_job_map(prod_jobs)
    s1_map = parse_s1_from_todo(todo_md)

    rows: list[dict] = []
    stats = {
        "total": len(TODO_HOSPITALS),
        "material_blocked": 0,
        "local_pass": 0,
        "local_warn": 0,
        "local_fail": 0,
        "local_skip": 0,
        "prod_pass": 0,
        "prod_fail": 0,
        "prod_coverage_gap": 0,
        "overall_pass": 0,
        "prod_lag": 0,
        "dual_material_fail": 0,
    }

    for name in TODO_HOSPITALS:
        le = local.get(name)
        pe = prod.get(name)
        has_prod = name in prod_map
        local_st = le.get("status", "—") if le else "—"
        prod_st = pe.get("status", "coverage_gap") if not has_prod else (pe.get("status", "—") if pe else "—")

        if local_st == "pass":
            stats["local_pass"] += 1
        elif local_st == "warn":
            stats["local_warn"] += 1
        elif local_st == "fail":
            stats["local_fail"] += 1
        elif local_st == "skip":
            stats["local_skip"] += 1

        if not has_prod:
            stats["prod_coverage_gap"] += 1
        elif prod_st in PASS_STATUSES:
            stats["prod_pass"] += 1
        elif prod_st not in ("—", "coverage_gap"):
            stats["prod_fail"] += 1

        if name in MATERIAL_BLOCKED:
            stats["material_blocked"] += 1

        overall, reason = classify_overall(le, pe, has_prod)
        if overall in ("pass", "pass_local_only"):
            stats["overall_pass"] += 1
        elif overall == "fail_prod_lag":
            stats["prod_lag"] += 1
        elif overall == "fail" and name in MATERIAL_BLOCKED:
            stats["dual_material_fail"] += 1

        s1 = s1_map.get(name, "—")
        material = "缺材料" if name in MATERIAL_BLOCKED else ("齐" if s1 == "✅" else s1)
        if name in MATERIAL_NOTES:
            material += f" · {MATERIAL_NOTES[name]}"

        lvsp = "一致" if local_st == prod_st else f"{local_st}≠{prod_st}"

        rows.append(
            {
                "hospital": name,
                "s1": material,
                "local_job": stable_map.get(name, "—"),
                "local_status": local_st,
                "local_lines": fmt_lines(le),
                "local_totals": fmt_totals(le),
                "local_detail": (le or {}).get("detail", "—"),
                "prod_job": prod_map.get(name, "无"),
                "prod_status": prod_st,
                "prod_lines": fmt_lines(pe) if has_prod else "—",
                "prod_totals": fmt_totals(pe) if has_prod else "—",
                "prod_detail": (pe or {}).get("detail", "生产无 Job") if has_prod else "coverage_gap",
                "local_vs_prod": lvsp if has_prod else "无 prod Job",
                "overall": overall,
                "reason": reason,
            }
        )

    lines = [
        f"# 全院 Excel 对账（bill）三环境验收报告 · {DATE_TAG}",
        "",
        f"> 生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M')} · 范围：TODO {stats['total']} 院 · **仅 bill**",
        "",
        "## 汇总统计",
        "",
        "| 维度 | 数量 |",
        "|------|------|",
        f"| 期待材料齐（非材料阻塞院） | {stats['total'] - stats['material_blocked']} |",
        f"| 材料阻塞院 | {stats['material_blocked']} |",
        f"| 本地 vs 期待 · pass | {stats['local_pass']} |",
        f"| 本地 vs 期待 · warn | {stats['local_warn']} |",
        f"| 本地 vs 期待 · fail | {stats['local_fail']} |",
        f"| 本地 vs 期待 · skip | {stats['local_skip']} |",
        f"| 生产 vs 期待 · pass（有 Job） | {stats['prod_pass']} |",
        f"| 生产 vs 期待 · fail | {stats['prod_fail']} |",
        f"| 生产 coverage_gap（无 Job） | {stats['prod_coverage_gap']} |",
        f"| 综合通过（本地对齐 + 生产无 gap 或也对齐） | {stats['overall_pass']} |",
        f"| 本地通过 · 生产未通过（部署滞后） | {stats['prod_lag']} |",
        f"| 双环境均 fail · 材料阻塞 | {stats['dual_material_fail']} |",
        "",
        "## 逐院明细",
        "",
        "| # | 医院 | 材料 | 本地Job | 本地状态 | 本地行 | 本地总额 | 生产Job | 生产状态 | 生产行 | 生产总额 | L↔P | **结论** |",
        "|:-:|------|------|---------|----------|--------|----------|---------|----------|--------|----------|-----|----------|",
    ]

    for i, r in enumerate(rows, 1):
        icon = {
            "pass": "✅",
            "pass_local_only": "✅",
            "fail": "🚫",
            "fail_prod_lag": "⚠️",
            "fail_local": "🚫",
            "skip": "⏭",
        }.get(r["overall"], "—")
        lines.append(
            f"| {i} | {r['hospital']} | {r['s1'][:24]} | {r['local_job']} | {r['local_status']} | "
            f"{r['local_lines']} | {r['local_totals']} | {r['prod_job']} | {r['prod_status']} | "
            f"{r['prod_lines']} | {r['prod_totals']} | {r['local_vs_prod']} | {icon} {r['overall']} |"
        )

    fail_rows = [r for r in rows if r["overall"] not in ("pass", "pass_local_only")]
    if fail_rows:
        lines.extend(["", "## 未通过 / 需关注院附录", ""])
        for r in fail_rows:
            lines.append(f"### {r['hospital']}")
            lines.append("")
            lines.append(f"- **综合结论**：{r['overall']}")
            lines.append(f"- **原因**：{r['reason']}")
            lines.append(f"- **本地**：Job {r['local_job']} · {r['local_status']} · {r['local_detail']}")
            lines.append(f"- **生产**：Job {r['prod_job']} · {r['prod_status']} · {r['prod_detail']}")
            lines.append("")

    lines.extend(
        [
            "## 复现命令",
            "",
            "```bash",
            "# 本地 S8 bill",
            "python3 scripts/batch_s8_export_compare.py \\",
            "  --job-map 测试用例/job_baseline_stable.json \\",
            "  --mode direct --api-base http://127.0.0.1:1001 \\",
            "  --no-todo-update --export-sleep 2",
            "",
            "# 生产 S8 bill（只读）",
            "python3 scripts/batch_s8_export_compare.py \\",
            "  --job-map 测试用例/job_baseline_prod.json \\",
            "  --mode direct --api-base http://39.102.213.51:8853 \\",
            "  --report-suffix prod \\",
            "  --export-dir 测试用例/.s8_exports_prod \\",
            "  --no-todo-update --export-sleep 2",
            "",
            "# 本报告",
            f"python3 scripts/generate_s8_tri_env_report.py --date {DATE_TAG}",
            "```",
            "",
        ]
    )

    out_path.write_text("\n".join(lines), encoding="utf-8")
    return {"out": str(out_path), "stats": stats, "rows": rows}


def main() -> int:
    p = argparse.ArgumentParser(description="生成 S8 bill 三环境验收报告")
    p.add_argument("--local", type=Path, default=TEST_CASE / "s8_export_compare_report.json")
    p.add_argument("--prod", type=Path, default=TEST_CASE / "s8_export_compare_report.prod.json")
    p.add_argument("--stable-jobs", type=Path, default=TEST_CASE / "job_baseline_stable.json")
    p.add_argument("--prod-jobs", type=Path, default=TEST_CASE / "job_baseline_prod.json")
    p.add_argument("--todo", type=Path, default=TEST_CASE / "优先医院对齐TODO.md")
    p.add_argument("--date", default=DATE_TAG, help="报告日期后缀 YYYYMMDD")
    p.add_argument("--out", type=Path, default=None)
    p.add_argument(
        "--gate",
        action="store_true",
        help="门禁模式：prod_lag>0 时 exit 1（本地 pass/warn 院生产未对齐）",
    )
    args = p.parse_args()
    out = args.out or (TEST_CASE / f"全院Excel对账三环境验收报告-{args.date}.md")
    result = build_report(args.local, args.prod, args.stable_jobs, args.prod_jobs, args.todo, out)
    print(f"报告: {result['out']}")
    print(json.dumps(result["stats"], ensure_ascii=False, indent=2))
    lag = result["stats"].get("prod_lag", 0)
    if args.gate and lag > 0:
        print(f"GATE FAIL: prod_lag={lag}", file=sys.stderr)
        return 1
    if args.gate:
        print("GATE PASS: prod_lag=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
