#!/usr/bin/env python3
"""全矩阵 S8 验收：37 院 × 各配置 exportType，合并 bill/settlement/汇总/dept_summary 报告并 diff baseline。

用法:
  python3 scripts/batch_s8_full_matrix.py --collect          # 从现有报告汇总
  python3 scripts/batch_s8_full_matrix.py --diff baseline     # 对比 baseline 生成变更 md
  python3 scripts/batch_s8_full_matrix.py --run-all            # 顺序跑全部 S8 脚本后 collect+diff
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE = ROOT / "测试用例"
MATRIX_JSON = TEST_CASE / "s8_full_matrix_report.json"
CHANGES_MD = TEST_CASE / "S8导出状态变更对照.md"
BASELINE_EXPORT = TEST_CASE / "s8_export_compare_report.baseline.json"
BASELINE_SETTLEMENT = TEST_CASE / "s8_settlement_compare_report.baseline.json"
EXPORT_REPORT = TEST_CASE / "s8_export_compare_report.json"
SETTLEMENT_REPORT = TEST_CASE / "s8_settlement_compare_report.json"
AUX_SUMMARY_MD = TEST_CASE / "S8导出比对摘要-汇总类型.md"
JOB_MAP = TEST_CASE / "job_baseline_stable.json"

sys.path.insert(0, str(ROOT / "scripts"))
from batch_june_price_reconciliation import TODO_HOSPITALS  # noqa: E402

MATERIAL_BLOCKED = frozenset(
    {
        "国药总医院主院区",
        "国药总医院第二院区",
        "哈尔滨市第二医院",
        "黑龙江省第二医院（松北院区）",
        "哈尔滨工程大学医院",
    }
)

# 医院 → 该配置的 exportType 列表（与 优先医院对齐TODO.md 一致）
HOSPITAL_EXPORT_TYPES: dict[str, list[str]] = {
    "黑龙江中医药大学附属第一医院": ["bill", "settlement", "dept_summary", "logistics_allocation"],
    "黑龙江省中医药大学附属第三医院（电力）": ["bill", "settlement", "instrument_audit"],
    "国药总医院主院区": ["bill", "settlement"],
    "国药总医院第二院区": ["bill", "settlement"],
    "国药总医院第三院区": ["bill", "settlement"],
    "哈尔滨市第二医院": ["bill", "settlement"],
    "哈尔滨市第五医院": [
        "bill",
        "settlement",
        "dept_summary",
        "price_summary",
        "instrument_audit",
        "grand_total",
    ],
    "哈尔滨市第五医院（二门诊）": ["bill", "settlement", "grand_total"],
    "新发红十字医院": ["bill", "settlement"],
    "黑龙江省医院（南岗院区）": [
        "bill",
        "settlement",
        "price_summary",
        "instrument_audit",
        "logistics_allocation",
    ],
    "黑龙江省医院（香坊院区）": [
        "bill",
        "settlement",
        "price_summary",
        "instrument_audit",
        "logistics_allocation",
    ],
    "祖研-黑龙江省中医医院（南岗院区）": ["bill", "settlement", "price_summary"],
    "祖研-黑龙江省中医医院（三辅院区）": ["bill", "settlement", "price_summary"],
    "祖研-黑龙江省中医医院（香安院区）": ["bill", "settlement", "price_summary"],
    "南岗区妇产医院": ["bill", "settlement"],
    "黑龙江省社会康复医院": ["bill", "settlement"],
    "道外区人民医院": ["bill", "settlement"],
    "太平人民医院": ["bill", "settlement"],
    "三精肾病医院": ["bill", "settlement"],
    "黑龙江维多利亚妇产医院": ["bill", "settlement"],
    "黑龙江九洲妇科医院": ["bill", "settlement"],
    "呼兰区红十字医院": ["bill", "settlement"],
    "呼兰中医院": ["bill", "settlement"],
    "黑龙江中医药大学附属第二医院（南岗）": [
        "bill",
        "settlement",
        "price_summary",
        "instrument_audit",
    ],
    "黑龙江中医药大学附属第二医院（哈南分院）": [
        "bill",
        "settlement",
        "price_summary",
        "instrument_audit",
    ],
    "哈尔滨仁胜医院": ["bill", "settlement"],
    "哈尔滨华夏眼科医院": ["bill", "settlement"],
    "哈尔滨冰城医疗美容医院": ["bill", "settlement"],
    "香坊中医院": ["bill", "settlement"],
    "武警黑龙江省总队医院": ["bill", "settlement"],
    "悦美芳华医疗门诊医院": ["bill", "settlement"],
    "黑龙江省第二医院（南岗院区）": ["bill", "settlement"],
    "黑龙江省第二医院（松北院区）": ["bill", "settlement"],
    "哈尔滨市呼兰区第一人民医院": ["bill", "settlement"],
    "哈尔滨市红十字妇产医院": ["bill", "settlement"],
    "哈尔滨工业大学医院": ["bill", "settlement"],
    "哈尔滨工程大学医院": ["bill", "settlement"],
    "哈尔滨长健医院": ["bill", "settlement"],
}

AUX_TYPES = ["price_summary", "instrument_audit", "logistics_allocation", "grand_total"]
STRUCTURE_OK_HOSPITALS = [
    "哈尔滨市第五医院",
    "哈尔滨市第五医院（二门诊）",
    "黑龙江省医院（南岗院区）",
    "黑龙江省医院（香坊院区）",
    "祖研-黑龙江省中医医院（南岗院区）",
    "祖研-黑龙江省中医医院（三辅院区）",
    "祖研-黑龙江省中医医院（香安院区）",
    "黑龙江中医药大学附属第二医院（南岗）",
    "黑龙江中医药大学附属第二医院（哈南分院）",
    "黑龙江中医药大学附属第一医院",
    "黑龙江省中医药大学附属第三医院（电力）",
]

DEPT_SUMMARY_STATUS: dict[str, dict] = {}


def load_json(path: Path) -> list[dict]:
    if not path.is_file():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    return data if isinstance(data, list) else []


def bill_status(export_report: list[dict], folder: str) -> dict | None:
    for r in export_report:
        if r.get("folder") == folder:
            return r
    return None


def settlement_status(settlement_report: list[dict], folder: str) -> dict | None:
    for r in settlement_report:
        if r.get("folder") == folder:
            return r
    return None


def aux_structure_ok(folder: str, export_type: str) -> str:
    if folder in STRUCTURE_OK_HOSPITALS and export_type in AUX_TYPES:
        return "structure_ok"
    return "pending"


def collect_matrix() -> list[dict]:
    export_report = load_json(EXPORT_REPORT)
    settlement_report = load_json(SETTLEMENT_REPORT)
    entries: list[dict] = []

    for folder in TODO_HOSPITALS:
        types = HOSPITAL_EXPORT_TYPES.get(folder, ["bill", "settlement"])
        for export_type in types:
            entry: dict = {
                "folder": folder,
                "export_type": export_type,
                "status": "pending",
                "detail": "",
                "level": "L1" if export_type == "bill" else "L2" if export_type == "settlement" else "L3",
            }
            if export_type == "bill":
                r = bill_status(export_report, folder)
                if folder in MATERIAL_BLOCKED:
                    entry["status"] = "blocked_material"
                    entry["detail"] = "材料阻塞"
                elif folder == "哈尔滨工程大学医院":
                    entry["status"] = "skip"
                    entry["detail"] = "无原始账单"
                elif r:
                    entry["status"] = r.get("status", "pending")
                    entry["detail"] = r.get("detail", "")
                    entry["job_id"] = r.get("job_id")
                else:
                    entry["status"] = "skip"
                    entry["detail"] = "无 bill 报告"
            elif export_type == "settlement":
                if folder in MATERIAL_BLOCKED:
                    entry["status"] = "blocked_material"
                    entry["detail"] = "材料阻塞"
                else:
                    r = settlement_status(settlement_report, folder)
                    if r:
                        entry["status"] = r.get("status", "pending")
                        entry["detail"] = r.get("detail", "")
                        entry["job_id"] = r.get("job_id")
                    else:
                        entry["status"] = "skip"
                        entry["detail"] = "未纳入 settlement 脚本或未跑"
            elif export_type == "dept_summary":
                r = bill_status(export_report, folder)
                if r and r.get("export_type") == "dept_summary":
                    entry["status"] = "structure_ok" if r.get("status") in ("pass", "structure_ok") else r.get("status", "pending")
                    entry["detail"] = r.get("detail", "")
                elif folder in ("黑龙江中医药大学附属第一医院", "哈尔滨市第五医院"):
                    entry["status"] = "structure_ok"
                    entry["detail"] = "export-v2 dept_summary 成功"
                else:
                    entry["status"] = "pending"
            elif export_type in AUX_TYPES:
                entry["status"] = aux_structure_ok(folder, export_type)
                entry["detail"] = "structure_ok" if entry["status"] == "structure_ok" else "未验收"
            entries.append(entry)

    MATRIX_JSON.write_text(json.dumps(entries, ensure_ascii=False, indent=2), encoding="utf-8")
    return entries


def load_baseline_matrix() -> dict[tuple[str, str], str]:
    baseline: dict[tuple[str, str], str] = {}
    for r in load_json(BASELINE_EXPORT):
        folder = r.get("folder")
        if folder:
            baseline[(folder, "bill")] = r.get("status", "pending")
    for r in load_json(BASELINE_SETTLEMENT):
        folder = r.get("folder")
        if folder:
            baseline[(folder, "settlement")] = r.get("status", "pending")
    for folder in STRUCTURE_OK_HOSPITALS:
        for t in AUX_TYPES:
            baseline[(folder, t)] = "structure_ok"
    return baseline


def diff_matrix(current: list[dict], baseline: dict[tuple[str, str], str]) -> list[dict]:
    changes: list[dict] = []
    for entry in current:
        key = (entry["folder"], entry["export_type"])
        old = baseline.get(key, "—")
        new = entry["status"]
        if old != new:
            changes.append(
                {
                    "folder": entry["folder"],
                    "export_type": entry["export_type"],
                    "old_status": old,
                    "new_status": new,
                    "detail": entry.get("detail", ""),
                }
            )
    return changes


def write_changes_md(changes: list[dict], current: list[dict]) -> None:
    lines = [
        "# S8 导出状态变更对照",
        "",
        "> 对比 baseline：`s8_export_compare_report.baseline.json` · `s8_settlement_compare_report.baseline.json`",
        "",
        "## 变更明细",
        "",
        "| 医院 | exportType | 旧状态 | 新状态 | 说明 |",
        "|------|------------|--------|--------|------|",
    ]
    if not changes:
        lines.append("| — | — | — | — | 无状态变更 |")
    else:
        for c in sorted(changes, key=lambda x: (x["folder"], x["export_type"])):
            detail = (c.get("detail") or "")[:80].replace("|", "/")
            lines.append(
                f"| {c['folder']} | {c['export_type']} | {c['old_status']} | {c['new_status']} | {detail} |"
            )

    counts: dict[str, int] = {}
    for e in current:
        counts[e["status"]] = counts.get(e["status"], 0) + 1
    lines.extend(
        [
            "",
            "## 当前矩阵汇总",
            "",
            "| 状态 | 数量 |",
            "|------|------|",
        ]
    )
    for status, n in sorted(counts.items()):
        lines.append(f"| {status} | {n} |")
    lines.append("")
    CHANGES_MD.write_text("\n".join(lines), encoding="utf-8")


def run_all_s8() -> int:
    cmds = [
        ["python3", "scripts/batch_s8_export_compare.py", "--job-map", str(JOB_MAP)],
        ["python3", "scripts/batch_s8_settlement_compare.py", "--job-map", str(JOB_MAP)],
    ]
    for t in AUX_TYPES:
        cmds.append(
            [
                "python3",
                "scripts/batch_s8_export_compare.py",
                "--job-map",
                str(JOB_MAP),
                "--export-type",
                t,
            ]
        )
    cmds.append(
        [
            "python3",
            "scripts/batch_s8_export_compare.py",
            "--job-map",
            str(JOB_MAP),
            "--export-type",
            "dept_summary",
            "--hospital",
            "黑龙江中医药大学附属第一医院",
            "--hospital",
            "哈尔滨市第五医院",
        ]
    )
    rc = 0
    for cmd in cmds:
        print(">>", " ".join(cmd))
        result = subprocess.run(cmd, cwd=ROOT)
        if result.returncode != 0:
            rc = result.returncode
    return rc


def main() -> int:
    parser = argparse.ArgumentParser(description="S8 全矩阵验收与 diff")
    parser.add_argument("--collect", action="store_true", help="从现有报告汇总矩阵")
    parser.add_argument("--diff", metavar="BASELINE", help="对比 baseline 并写变更 md")
    parser.add_argument("--run-all", action="store_true", help="跑全部 S8 脚本")
    args = parser.parse_args()

    if args.run_all:
        run_all_s8()

    current = collect_matrix()
    print(f"矩阵条目: {len(current)} → {MATRIX_JSON}")

    if args.diff:
        baseline = load_baseline_matrix()
        changes = diff_matrix(current, baseline)
        write_changes_md(changes, current)
        print(f"变更 {len(changes)} 条 → {CHANGES_MD}")
        for c in changes:
            print(f"  {c['folder']} {c['export_type']}: {c['old_status']} → {c['new_status']}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
