#!/usr/bin/env python3
"""合并 S1/S4/S8 审计结果，生成客户反馈账单核对报告。"""

from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE = ROOT / "测试用例"
DATE_TAG = datetime.now().strftime("%Y%m%d")

import sys

sys.path.insert(0, str(ROOT / "scripts"))
from batch_june_price_reconciliation import TODO_HOSPITALS, pick_june_pair  # noqa: E402
from batch_s8_settlement_compare import is_settlement_name, pick_settlement_file  # noqa: E402

# 波次6 基线
WAVE6_BASELINE = {
    "s8_bill_pass": 34,
    "s8_bill_fail": 4,
    "s8_bill_skip": 0,
    "s8_settlement_pass": 34,
    "s8_settlement_skip": 0,
    "strict_dual_pass": 34,
}

KNOWN_BILL_FAIL = frozenset(
    {
        "国药总医院主院区",
        "国药总医院第二院区",
        "哈尔滨市第二医院",
        "黑龙江省第二医院（松北院区）",
    }
)

KNOWN_S4_FAIL_EXTRA = frozenset(
    {
        "国药总医院主院区",
        "国药总医院第二院区",
        "哈尔滨市第二医院",
        "南岗区妇产医院",
        "哈尔滨冰城医疗美容医院",
        "哈尔滨长健医院",
        "哈尔滨工业大学医院",  # stable Job691 · MD 主表仍写 624 pass_zero
    }
)

KNOWN_MD_DRIFT = {
    "哈尔滨工业大学医院": "MD 主表 Job624 pass_zero 已过期，stable 映射 Job691 · extra_inventory",
}

KNOWN_SETTLEMENT_DIFF = frozenset({"国药总医院第三院区", "哈尔滨长健医院"})

PREFLIGHT_LOCAL = TEST_CASE / ".billing_audit_preflight.json"
PREFLIGHT_PROD = TEST_CASE / ".billing_audit_preflight.prod.json"


def paths_for_profile(profile: str) -> dict[str, Path]:
    if profile == "prod":
        return {
            "report_md": TEST_CASE / f"客户反馈账单核对报告-prod-{DATE_TAG}.md",
            "report_json": TEST_CASE / f"客户反馈账单核对报告-prod-{DATE_TAG}.json",
            "s4": TEST_CASE / "s4_prod_job_audit.json",
            "bill": TEST_CASE / "s8_export_compare_report.prod.json",
            "settlement": TEST_CASE / "s8_settlement_compare_report.prod.json",
            "jobs": TEST_CASE / "job_baseline_prod.json",
            "preflight": PREFLIGHT_PROD,
            "env_label": f"生产 API（只读）",
        }
    return {
        "report_md": TEST_CASE / f"客户反馈账单核对报告-{DATE_TAG}.md",
        "report_json": TEST_CASE / f"客户反馈账单核对报告-{DATE_TAG}.json",
        "s4": TEST_CASE / "s4_stable_job_audit.json",
        "bill": TEST_CASE / "s8_export_compare_report.json",
        "settlement": TEST_CASE / "s8_settlement_compare_report.json",
        "jobs": TEST_CASE / "job_baseline_stable.json",
        "preflight": PREFLIGHT_LOCAL,
        "env_label": "本地 Docker",
    }


@dataclass
class MaterialRow:
    hospital: str
    has_raw: bool = False
    has_processed: bool = False
    has_settlement: bool = False
    status: str = "ok"
    message: str = ""


@dataclass
class Deviation:
    priority: str
    hospital: str
    step: str
    detail: str


@dataclass
class HospitalMatrix:
    hospital: str
    s1: str = "—"
    s4: str = "—"
    s8_bill: str = "—"
    s8_settlement: str = "—"
    job_id: int | None = None
    note: str = ""


def audit_material(name: str) -> MaterialRow:
    row = MaterialRow(hospital=name)
    folder = TEST_CASE / name
    if not folder.is_dir():
        row.status = "missing_folder"
        row.message = "测试用例目录不存在"
        return row

    raw_dir = folder / "原始表格"
    proc_dir = folder / "处理后表格"
    row.has_raw = raw_dir.is_dir() and any(
        p.suffix.lower() in {".xlsx", ".xls"} for p in raw_dir.iterdir() if p.is_file()
    )
    raw_path, proc_path, note = pick_june_pair(folder)
    row.has_processed = proc_path is not None
    row.has_settlement = pick_settlement_file(folder) is not None

    if not row.has_raw:
        row.status = "skip_no_raw"
        row.message = "缺原始表格"
    elif not row.has_processed:
        row.status = "skip_no_processed"
        row.message = note or "缺处理后 6 月账单"
    elif not row.has_settlement:
        row.status = "warn_no_settlement"
        row.message = "无缺结款函参考（S8 settlement 可能 skip）"
    else:
        row.status = "ok"
        row.message = "材料齐全"
    return row


def load_json(path: Path) -> list | dict | None:
    if not path.is_file():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def index_by_folder(items: list[dict]) -> dict[str, dict]:
    return {x["folder"]: x for x in items if "folder" in x}


def load_coverage_from_calibration() -> dict | None:
    path = TEST_CASE / "job_baseline_prod_calibration.json"
    if not path.is_file():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def hospitals_with_prod_job(jobs: dict[str, int]) -> frozenset[str]:
    return frozenset(jobs.keys())


def is_export_failure(detail: str) -> bool:
    d = detail or ""
    return "非 xlsx" in d or "export-v2 失败" in d or "export-v2 Job" in d and "失败" in d


def classify_deviations(
    material: list[MaterialRow],
    s4_rows: list[dict],
    bill: dict[str, dict],
    settlement: dict[str, dict],
    preflight: dict | None,
    *,
    profile: str = "local",
    prod_jobs: frozenset[str] | None = None,
) -> list[Deviation]:
    devs: list[Deviation] = []
    is_prod = profile == "prod"

    if preflight:
        if not preflight.get("smoke_ok"):
            devs.append(Deviation("P0" if is_prod else "P3", "—", "环境", "smoke 未通过"))
        elif not is_prod and not preflight.get("deploy_check_ok"):
            devs.append(
                Deviation(
                    "P3",
                    "—",
                    "环境",
                    preflight.get("deploy_check_detail", "deploy-check 未通过"),
                )
            )

    for m in material:
        if m.status in {"missing_folder", "skip_no_raw", "skip_no_processed"}:
            devs.append(Deviation("P3", m.hospital, "S1", m.message))

    if is_prod and prod_jobs is not None:
        for name in TODO_HOSPITALS:
            if name not in prod_jobs:
                devs.append(
                    Deviation(
                        "P3",
                        name,
                        "coverage_gap",
                        "生产无 reconciliation Job（只读门禁 · 非 wave6 对标）",
                    )
                )

    for r in s4_rows:
        name = r["hospital"]
        if r.get("status") == "skip":
            continue
        if is_prod and prod_jobs is not None and name not in prod_jobs:
            continue

        if r.get("missed", 0) > 0:
            devs.append(
                Deviation(
                    "P0",
                    name,
                    "S4",
                    f"漏检 missed={r['missed']} job={r.get('job_id')} keys={r.get('missed_keys', [])[:3]}",
                )
            )
        elif r.get("status") == "fail_extra":
            if is_prod:
                devs.append(
                    Deviation(
                        "P2",
                        name,
                        "S4",
                        f"fail_extra extra={r.get('extra')}（prod Job 数据 · 非漏检）",
                    )
                )
            elif name not in KNOWN_S4_FAIL_EXTRA:
                devs.append(
                    Deviation(
                        "P0",
                        name,
                        "S4",
                        f"新 extra_inventory extra={r.get('extra')}（不在已知 6 院）",
                    )
                )
            else:
                devs.append(
                    Deviation(
                        "P2",
                        name,
                        "S4",
                        f"fail_extra extra={r.get('extra')}（已知 extra_inventory）",
                    )
                )
        if r.get("md_drift") and not is_prod:
            if name in KNOWN_MD_DRIFT:
                devs.append(Deviation("P3", name, "S4", KNOWN_MD_DRIFT[name]))
            else:
                devs.append(
                    Deviation("P0", name, "S4", r.get("message", "与 MD 主表漂移"))
                )

    for name, b in bill.items():
        if is_prod and prod_jobs is not None and name not in prod_jobs:
            continue
        st = b.get("status", "skip")
        detail = b.get("detail", "")
        if is_export_failure(detail):
            devs.append(Deviation("P0", name, "S8 bill", detail))
        elif st == "fail":
            if is_prod:
                devs.append(
                    Deviation(
                        "P2",
                        name,
                        "S8 bill",
                        f"与本地材料比对 fail · {detail or 'fail'}（prod Job 数据 · 非 export 失败）",
                    )
                )
            elif name in KNOWN_BILL_FAIL:
                delta = ""
                totals = b.get("totals") or {}
                if totals.get("expected") is not None and totals.get("actual") is not None:
                    delta = f" Δ{abs(float(totals['actual']) - float(totals['expected'])):.2f}"
                devs.append(
                    Deviation("P1", name, "S8 bill", f"材料阻塞 fail{delta} · {detail}")
                )
            else:
                devs.append(Deviation("P0", name, "S8 bill", detail or "fail"))
        elif st == "skip" and not is_prod:
            devs.append(Deviation("P3", name, "S8 bill", detail or "skip"))
        elif st not in {"pass", "warn", "skip"} and name not in KNOWN_BILL_FAIL:
            devs.append(Deviation("P0", name, "S8 bill", detail or st))

    for name, s in settlement.items():
        if is_prod and prod_jobs is not None and name not in prod_jobs:
            continue
        st = s.get("status", "skip")
        detail = s.get("detail", "")
        if is_export_failure(detail):
            devs.append(Deviation("P0", name, "S8 settlement", detail))
        elif st == "fail":
            if is_prod:
                devs.append(
                    Deviation(
                        "P2",
                        name,
                        "S8 settlement",
                        f"与本地材料比对 fail · {detail or 'fail'}（prod Job 数据）",
                    )
                )
            else:
                devs.append(Deviation("P0", name, "S8 settlement", detail or "fail"))
        elif st == "blocked_material":
            if name in MATERIAL_BLOCKED_SETTLEMENT:
                devs.append(Deviation("P3", name, "S8 settlement", "材料阻塞 · 跳过结款自动化"))
        elif st == "skip" and not is_prod and name not in MATERIAL_BLOCKED_SETTLEMENT:
            devs.append(Deviation("P3", name, "S8 settlement", detail or "skip 缺参考"))
        elif st == "pass" and name in KNOWN_SETTLEMENT_DIFF:
            devs.append(
                Deviation("P2", name, "S8 settlement", f"登记已知差 · {detail}")
            )

    order = {"P0": 0, "P1": 1, "P2": 2, "P3": 3, "INFO": 4}
    devs.sort(key=lambda d: (order.get(d.priority, 9), d.hospital, d.step))
    return devs


MATERIAL_BLOCKED_SETTLEMENT = frozenset(
    {
        "国药总医院主院区",
        "国药总医院第二院区",
        "哈尔滨市第二医院",
        "黑龙江省第二医院（松北院区）",
    }
)


def count_s8(items: dict[str, dict], key: str = "status") -> dict[str, int]:
    c: dict[str, int] = {}
    for x in items.values():
        st = x.get(key, "skip")
        c[st] = c.get(st, 0) + 1
    return c


def build_matrix(
    material: list[MaterialRow],
    s4_rows: list[dict],
    bill: dict[str, dict],
    settlement: dict[str, dict],
    jobs: dict[str, int],
) -> list[HospitalMatrix]:
    s4_map = {r["hospital"]: r for r in s4_rows}
    out: list[HospitalMatrix] = []
    for name in TODO_HOSPITALS:
        m = next((x for x in material if x.hospital == name), MaterialRow(name))
        s4 = s4_map.get(name, {})
        b = bill.get(name, {})
        s = settlement.get(name, {})
        s1_icon = "✅" if m.status == "ok" else ("⏭" if m.status.startswith("skip") else "🔄")
        s4_st = s4.get("status", "—")
        s4_icon = "✅" if s4_st in {"pass", "pass_zero"} else ("🔄" if s4_st == "fail_extra" else s4_st)
        b_st = b.get("status", "—")
        s_st = s.get("status", "—")
        note_parts = []
        if m.message and m.status != "ok":
            note_parts.append(m.message)
        if b.get("detail") and b_st == "fail":
            note_parts.append(b["detail"][:80])
        out.append(
            HospitalMatrix(
                hospital=name,
                s1=s1_icon,
                s4=s4_icon,
                s8_bill=b_st,
                s8_settlement=s_st,
                job_id=jobs.get(name),
                note=" · ".join(note_parts),
            )
        )
    return out


def render_md(
    material: list[MaterialRow],
    s4_summary: dict,
    bill_counts: dict[str, int],
    settle_counts: dict[str, int],
    matrix: list[HospitalMatrix],
    deviations: list[Deviation],
    preflight: dict | None,
    *,
    env_label: str,
    profile: str = "local",
    coverage: dict | None = None,
    local_vs_prod: list[dict] | None = None,
) -> str:
    is_prod = profile == "prod"
    lines = [
        f"# 客户反馈账单核对报告 · {DATE_TAG}" + (" · 生产" if is_prod else ""),
        "",
        f"> 生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M')} · 环境：{env_label}"
        + (" · **只读门禁**（不与 local wave6 硬对标）" if is_prod else " · 基线：波次6"),
        "",
        "## 执行摘要",
        "",
    ]

    if is_prod:
        cov_found = (coverage or {}).get("summary", {}).get("found", len([h for h in matrix if h.job_id]))
        cov_missing = (coverage or {}).get("summary", {}).get("missing", 37 - cov_found)
        lines.extend(
            [
                "| 维度 | 本轮 | 只读门禁期望 | 判定 |",
                "|------|------|--------------|------|",
                f"| Job 覆盖率 | **{cov_found}/37** | 记录即可 | {'✅' if cov_found >= 20 else '⚠️'} |",
                f"| coverage_gap | **{cov_missing}** | 不 fail CI | 登记 P3 |",
                f"| S8 bill pass（有 Job 院） | **{bill_counts.get('pass', 0)}** | export 成功 | — |",
                f"| S8 export 失败 | **{sum(1 for d in deviations if d.priority == 'P0' and ('非 xlsx' in d.detail or 'export-v2 失败' in d.detail))}** | 0 | — |",
                f"| S4 漏检（有 Job 院） | **{s4_summary.get('fail_missed', 0)}** | 0 | — |",
                "",
                "> 说明：完整 37 院 wave6 对标**仅在本地 Docker**（stable Job）完成；生产仅审计已有 Job。",
                "",
            ]
        )
    else:
        lines.extend(
            [
                "| 维度 | 本轮 | 波次6 基线 | 判定 |",
                "|------|------|------------|------|",
            ]
        )

        def cmp_row(label: str, actual: int, baseline: int) -> str:
            ok = actual == baseline
            return f"| {label} | **{actual}** | {baseline} | {'✅ 一致' if ok else '⚠️ 偏差'} |"

        bill_pass = bill_counts.get("pass", 0)
        bill_fail = bill_counts.get("fail", 0)
        bill_skip = bill_counts.get("skip", 0)
        settle_pass = settle_counts.get("pass", 0)
        settle_skip = settle_counts.get("skip", 0)
        lines.append(cmp_row("S8 bill pass", bill_pass, WAVE6_BASELINE["s8_bill_pass"]))
        lines.append(cmp_row("S8 bill fail", bill_fail, WAVE6_BASELINE["s8_bill_fail"]))
        lines.append(cmp_row("S8 bill skip", bill_skip, WAVE6_BASELINE["s8_bill_skip"]))
        lines.append(cmp_row("S8 settlement pass", settle_pass, WAVE6_BASELINE["s8_settlement_pass"]))
        lines.append(cmp_row("S8 settlement skip", settle_skip, WAVE6_BASELINE["s8_settlement_skip"]))
        strict = sum(1 for h in matrix if h.s8_bill == "pass" and h.s8_settlement == "pass")
        lines.append(cmp_row("strict 双 pass", strict, WAVE6_BASELINE["strict_dual_pass"]))
        lines.append("")

    lines.append(
        f"- S4 live 审计：pass {s4_summary.get('pass', 0)} · fail_extra {s4_summary.get('fail_extra', 0)} · "
        f"漏检 {s4_summary.get('fail_missed', 0)} · skip {s4_summary.get('skip', 0)}"
    )
    if preflight:
        lines.append(
            f"- 预检：smoke={'OK' if preflight.get('smoke_ok') else 'FAIL'}"
            + (
                f" · deploy-check={'OK' if preflight.get('deploy_check_ok') else 'FAIL'}"
                if not is_prod
                else ""
            )
        )
    lines.append("")

    p0 = [d for d in deviations if d.priority == "P0"]
    p1 = [d for d in deviations if d.priority == "P1"]
    p2 = [d for d in deviations if d.priority == "P2"]
    p3 = [d for d in deviations if d.priority == "P3"]
    info = [d for d in deviations if d.priority == "INFO"]

    lines.extend(["## 不符合预期清单", ""])
    if not deviations:
        lines.append("_本轮无 P0/P1 问题。_")
    else:
        sections = [
            ("P0 引擎/漏检/export 失败", p0),
            ("P1 已知材料阻塞", p1),
            ("P2 已知口径差", p2),
            ("P3 coverage_gap / 环境", p3),
        ]
        if info:
            sections.append(("INFO local vs prod（预期可不同）", info))
        for title, items in sections:
            lines.append(f"### {title}（{len(items)}）")
            lines.append("")
            if not items:
                lines.append("_无_")
            else:
                for d in items:
                    lines.append(f"- **{d.hospital}** · {d.step}：{d.detail}")
            lines.append("")

    lines.extend(["## 37 院矩阵", "", "| 医院 | Job | S1 | S4 | S8 bill | S8 settlement | 备注 |", "|------|-----|:--:|:--:|:-------:|:-------------:|------|"])
    for h in matrix:
        lines.append(
            f"| {h.hospital} | {h.job_id or '—'} | {h.s1} | {h.s4} | {h.s8_bill} | {h.s8_settlement} | {h.note} |"
        )
    lines.append("")

    if local_vs_prod:
        lines.extend(["## local vs prod 差异", ""])
        diffs = [d for d in local_vs_prod if d.get("diff")]
        if not diffs:
            lines.append("_生产与本地 S8 bill/settlement 状态一致（37 院）。_")
        else:
            lines.extend(
                [
                    "| 医院 | local bill | prod bill | local settlement | prod settlement | 备注 |",
                    "|------|------------|-----------|------------------|-----------------|------|",
                ]
            )
            for d in diffs:
                lines.append(
                    f"| {d['hospital']} | {d.get('local_bill','—')} | {d.get('prod_bill','—')} | "
                    f"{d.get('local_settlement','—')} | {d.get('prod_settlement','—')} | {d.get('note','')} |"
                )
        lines.append("")

    lines.extend(
        [
            "## 建议下一步",
            "",
            "1. **P0**：有 Job 院 export 失败或 S4 漏检 → 定点排查。",
            "2. **P3 coverage_gap**：运营在 UI 导入真实账期 Job，勿在 prod 批量 import 测试数据。",
            "3. **完整 37 院验收**：在本地 Docker 跑 stable Job wave6 对标。",
            "",
        ]
        if is_prod
        else [
            "## 建议下一步",
            "",
            "1. **P0**：若有 MD 漂移或新 fail，定点排查 Job 与引擎变更。",
            "2. **P1**：向铂康索要 kit BOM / vendor 7 sheet / part3 补录。",
            "3. **P2**：S4 fail_extra 为 extra_inventory，非漏检。",
            "",
        ]
    )
    return "\n".join(lines)


def build_local_vs_prod_diff(
    local_bill: dict[str, dict],
    prod_bill: dict[str, dict],
    local_settle: dict[str, dict],
    prod_settle: dict[str, dict],
) -> list[dict]:
    rows: list[dict] = []
    for name in TODO_HOSPITALS:
        lb = local_bill.get(name, {}).get("status", "—")
        pb = prod_bill.get(name, {}).get("status", "—")
        ls = local_settle.get(name, {}).get("status", "—")
        ps = prod_settle.get(name, {}).get("status", "—")
        diff = lb != pb or ls != ps
        note = ""
        if diff:
            parts = []
            if lb != pb:
                parts.append(f"bill {lb}→{pb}")
            if ls != ps:
                parts.append(f"settlement {ls}→{ps}")
            note = "; ".join(parts)
        rows.append(
            {
                "hospital": name,
                "local_bill": lb,
                "prod_bill": pb,
                "local_settlement": ls,
                "prod_settlement": ps,
                "diff": diff,
                "note": note,
            }
        )
    return rows


def write_local_vs_prod_md(rows: list[dict]) -> Path:
    out = TEST_CASE / f"local_vs_prod_billing_diff-{DATE_TAG}.md"
    diffs = [r for r in rows if r.get("diff")]
    lines = [
        f"# local vs prod 账单核对差异 · {DATE_TAG}",
        "",
        f"> 差异院数：**{len(diffs)}** / {len(rows)}",
        "",
    ]
    if not diffs:
        lines.append("_无差异。_")
    else:
        lines.extend(
            [
                "| 医院 | local bill | prod bill | local settlement | prod settlement | 备注 |",
                "|------|------------|-----------|------------------|-----------------|------|",
            ]
        )
        for d in diffs:
            lines.append(
                f"| {d['hospital']} | {d['local_bill']} | {d['prod_bill']} | "
                f"{d['local_settlement']} | {d['prod_settlement']} | {d['note']} |"
            )
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description="账单核对汇总报告")
    parser.add_argument("--profile", choices=["local", "prod"], default="local")
    args = parser.parse_args()
    paths = paths_for_profile(args.profile)

    material = [audit_material(n) for n in TODO_HOSPITALS]
    material_json = TEST_CASE / "s1_material_audit.json"
    material_json.write_text(
        json.dumps([asdict(m) for m in material], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"S1 材料审计: {material_json}")

    s4_data = load_json(paths["s4"]) or {"rows": [], "summary": {}}
    bill_list = load_json(paths["bill"]) or []
    settle_list = load_json(paths["settlement"]) or []
    if isinstance(bill_list, dict):
        bill_list = bill_list.get("results", [])
    if isinstance(settle_list, dict):
        settle_list = settle_list.get("results", [])

    bill = index_by_folder(bill_list if isinstance(bill_list, list) else [])
    settlement = index_by_folder(settle_list if isinstance(settle_list, list) else [])

    jobs_data = load_json(paths["jobs"]) or {}
    jobs = {k: int(v) for k, v in (jobs_data.get("jobs") or {}).items()}

    preflight = load_json(paths["preflight"]) if paths["preflight"].is_file() else None
    s4_rows = s4_data.get("rows", [])
    prod_job_set = hospitals_with_prod_job(jobs) if args.profile == "prod" else None
    coverage = load_coverage_from_calibration() if args.profile == "prod" else None

    deviations = classify_deviations(
        material,
        s4_rows,
        bill,
        settlement,
        preflight,
        profile=args.profile,
        prod_jobs=prod_job_set,
    )
    matrix = build_matrix(material, s4_rows, bill, settlement, jobs)

    bill_counts = count_s8(bill)
    settle_counts = count_s8(settlement)

    local_vs_prod: list[dict] | None = None
    if args.profile == "prod":
        local_bill = index_by_folder(load_json(TEST_CASE / "s8_export_compare_report.json") or [])
        local_settle = index_by_folder(load_json(TEST_CASE / "s8_settlement_compare_report.json") or [])
        local_vs_prod = build_local_vs_prod_diff(local_bill, bill, local_settle, settlement)
        diff_path = write_local_vs_prod_md(local_vs_prod)
        print(f"local vs prod: {diff_path}")
        for row in local_vs_prod:
            if row["diff"]:
                deviations.append(
                    Deviation("INFO", row["hospital"], "local vs prod", row["note"])
                )
        order = {"P0": 0, "P1": 1, "P2": 2, "P3": 3, "INFO": 4}
        deviations.sort(key=lambda d: (order.get(d.priority, 9), d.hospital, d.step))

    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "profile": args.profile,
        "wave6_baseline": WAVE6_BASELINE if args.profile == "local" else None,
        "coverage": coverage.get("summary") if coverage else None,
        "preflight": preflight,
        "s1_summary": {
            "ok": sum(1 for m in material if m.status == "ok"),
            "warn_no_settlement": sum(1 for m in material if m.status == "warn_no_settlement"),
            "skip": sum(1 for m in material if m.status.startswith("skip")),
        },
        "s4_summary": s4_data.get("summary", {}),
        "s8_bill_counts": bill_counts,
        "s8_settlement_counts": settle_counts,
        "strict_dual_pass": sum(
            1 for h in matrix if h.s8_bill == "pass" and h.s8_settlement == "pass"
        ),
        "deviations": [asdict(d) for d in deviations],
        "matrix": [asdict(h) for h in matrix],
        "local_vs_prod": local_vs_prod,
    }

    paths["report_json"].write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    paths["report_md"].write_text(
        render_md(
            material,
            s4_data.get("summary", {}),
            bill_counts,
            settle_counts,
            matrix,
            deviations,
            preflight,
            env_label=paths["env_label"],
            profile=args.profile,
            coverage=coverage,
            local_vs_prod=local_vs_prod,
        ),
        encoding="utf-8",
    )
    print(f"报告: {paths['report_md']}")
    print(f"JSON: {paths['report_json']}")
    p0 = sum(1 for d in deviations if d.priority == "P0")
    return 1 if p0 else 0


if __name__ == "__main__":
    raise SystemExit(main())
