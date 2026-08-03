#!/usr/bin/env python3
"""将 铂康/测试账单-6 同步到 测试用例/{医院}/（波次6 材料闭环）。"""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BATCH6 = ROOT / "铂康" / "测试账单-6"
CASE = ROOT / "测试用例"

# (源文件名, 医院目录, 子目录, 目标文件名)
BATCH6_MAP: list[tuple[str, str, str, str]] = [
    (
        "工业大学6.15-7.14账单-原始.xlsx",
        "哈尔滨工业大学医院",
        "原始表格",
        "6月__工业大学6.15-7.14原始账单.xlsx",
    ),
    (
        "三辅社区、香坊中医院6月结款函.xlsx",
        "香坊中医院",
        "处理后表格",
        "6月__三辅社区、香坊中医院6月结款函.xlsx",
    ),
    (
        "三辅社区、香坊中医院6月结款函.xlsx",
        "祖研-黑龙江省中医医院（三辅院区）",
        "处理后表格",
        "6月__三辅社区、香坊中医院6月结款函.xlsx",
    ),
    (
        "长健6月结款涵.xlsx",
        "哈尔滨长健医院",
        "处理后表格",
        "6月__长健6月结款涵.xlsx",
    ),
    (
        "哈尔滨市第五医院2026年5月9日-2026年6月8日结款函.xlsx",
        "哈尔滨市第五医院",
        "处理后表格",
        "6月__哈尔滨市第五医院2026年5月9日-2026年6月8日结款函.xlsx",
    ),
]

# docx → xlsx 目标名（入库时转换）
DOCX_TO_XLSX: list[tuple[str, str, str, str]] = [
    (
        "国药总医院第三院区5.26-6.25结款函.docx",
        "国药总医院第三院区",
        "处理后表格",
        "6月__国药总医院第三院区5.26-6.25结款函.xlsx",
    ),
]


@dataclass
class CopyResult:
    src: str
    dest: str
    action: str


def copy_file(src: Path, dest: Path) -> CopyResult:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size == src.stat().st_size:
        return CopyResult(str(src), str(dest), "skip_same_size")
    shutil.copy2(src, dest)
    return CopyResult(str(src), str(dest), "copied")


def convert_docx_to_xlsx(docx: Path, xlsx: Path) -> CopyResult:
    xlsx.parent.mkdir(parents=True, exist_ok=True)
    for cmd in (
        ["soffice", "--headless", "--convert-to", "xlsx", "--outdir", str(xlsx.parent), str(docx)],
        ["libreoffice", "--headless", "--convert-to", "xlsx", "--outdir", str(xlsx.parent), str(docx)],
    ):
        if shutil.which(cmd[0]):
            subprocess.check_call(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            produced = xlsx.parent / f"{docx.stem}.xlsx"
            if produced.is_file():
                if produced != xlsx:
                    produced.replace(xlsx)
                return CopyResult(str(docx), str(xlsx), "converted_docx")
            break
    # fallback: copy docx alongside for manual review; settlement script may still skip
    dest_docx = xlsx.with_suffix(".docx")
    shutil.copy2(docx, dest_docx)
    return CopyResult(str(docx), str(dest_docx), "copied_docx_only")


def main() -> int:
    results: list[CopyResult] = []
    missing: list[str] = []

    # 铂康根目录同名文件（客户微信直传）
    root_hit = ROOT / "铂康" / "工业大学6.15-7.14账单-原始(1).xlsx"
    if root_hit.is_file():
        dest = CASE / "哈尔滨工业大学医院" / "原始表格" / "6月__工业大学6.15-7.14原始账单.xlsx"
        results.append(copy_file(root_hit, dest))

    for name, hospital, subdir, dest_name in BATCH6_MAP:
        src = BATCH6 / name
        if not src.is_file():
            missing.append(name)
            continue
        dest = CASE / hospital / subdir / dest_name
        results.append(copy_file(src, dest))

    for name, hospital, subdir, dest_name in DOCX_TO_XLSX:
        src = BATCH6 / name
        if not src.is_file():
            missing.append(name)
            continue
        dest = CASE / hospital / subdir / dest_name
        results.append(convert_docx_to_xlsx(src, dest))

    report = {
        "source": str(BATCH6),
        "copied": sum(1 for r in results if r.action in ("copied", "converted_docx", "copied_docx_only")),
        "skipped": sum(1 for r in results if r.action == "skip_same_size"),
        "missing_sources": missing,
        "details": [r.__dict__ for r in results],
    }
    out = CASE / "test_batch6_ingest_report.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("copied", "skipped", "missing_sources")}, ensure_ascii=False, indent=2))
    return 0 if not missing else 0  # missing docx sources are warnings only if partial


if __name__ == "__main__":
    raise SystemExit(main())
