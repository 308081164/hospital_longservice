#!/usr/bin/env python3
"""将 铂康/缺失文件补充 与 特殊价格单 PDF 同步到 测试用例/{医院}/ 目录。"""

from __future__ import annotations

import json
import shutil
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SUPP = ROOT / "铂康" / "缺失文件补充"
PRICE_PDF = ROOT / "铂康" / "特殊价格单"
CASE = ROOT / "测试用例"

# (源文件名, 规范医院目录名, 子目录, 目标文件名前缀如 4月__)
FILE_MAP: list[tuple[str, str, str, str | None]] = [
    # 第一节：红十字 4/5 月处理后
    ("红十字4月结款函.xlsx", "哈尔滨市红十字妇产医院", "处理后表格", "4月__红十字4月结款函.xlsx"),
    ("红十字4月分科室.xlsx", "哈尔滨市红十字妇产医院", "处理后表格", "4月__红十字4月分科室.xlsx"),
    ("红十字5月结款函.xlsx", "哈尔滨市红十字妇产医院", "处理后表格", "5月__红十字5月结款函.xlsx"),
    ("红十字5月分科室.xlsx", "哈尔滨市红十字妇产医院", "处理后表格", "5月__红十字5月分科室.xlsx"),
    ("红十字5月账单.xlsx", "哈尔滨市红十字妇产医院", "处理后表格", "5月__红十字5月账单.xlsx"),
    # 第二节：零差异院补「有差别月份」
    ("太平人民2026.5.13-2026.6.15账单.xlsx", "太平人民医院", "处理后表格", "5月__太平人民2026.5.13-2026.6.15账单.xlsx"),
    ("太平人民2026.5.13-2026.6.15结款函.xlsx", "太平人民医院", "处理后表格", "5月__太平人民2026.5.13-2026.6.15结款函.xlsx"),
    ("维多利亚5月账单.xlsx", "黑龙江维多利亚妇产医院", "处理后表格", "5月__维多利亚5月账单.xlsx"),
    ("维多利亚5月结款函.xlsx", "黑龙江维多利亚妇产医院", "处理后表格", "5月__维多利亚5月结款函.xlsx"),
    ("呼兰红十字5月账单.xlsx", "呼兰区红十字医院", "原始表格", None),
    ("呼兰红十字5月结款函.xlsx", "呼兰区红十字医院", "处理后表格", "5月__呼兰红十字5月结款函.xlsx"),
    ("呼兰中医院5月账单.xlsx", "呼兰中医院", "原始表格", None),
    ("呼兰中医院5月结款函.xlsx", "呼兰中医院", "处理后表格", "5月__呼兰中医院5月结款函.xlsx"),
    ("国药总医院主院区2.26-3.25账单.xlsx", "国药总医院主院区", "处理后表格", "3月__国药总医院主院区2.26-3.25账单.xlsx"),
    ("国药总医院主院区2.26-3.25结款函.docx", "国药总医院主院区", "处理后表格", "3月__国药总医院主院区2.26-3.25结款函.docx"),
    ("国药总医院第二院区1.26-2.25账单.xlsx", "国药总医院第二院区", "处理后表格", "2月__国药总医院第二院区1.26-2.25账单.xlsx"),
    ("国药总医院第二院区1.26-2.25结款函.docx", "国药总医院第二院区", "处理后表格", "2月__国药总医院第二院区1.26-2.25结款函.docx"),
    ("康复4月账单.xlsx", "黑龙江省社会康复医院", "处理后表格", "4月__康复4月账单.xlsx"),
    ("康复4月结款涵.xlsx", "黑龙江省社会康复医院", "处理后表格", "4月__康复4月结款涵.xlsx"),
    ("祖研-黑龙江省中医医院（香安院区）4月.xlsx", "祖研-黑龙江省中医医院（香安院区）", "处理后表格", "4月__祖研香安4月账单.xlsx"),
    ("祖研-黑龙江省中医医院（香安院区）4月结款函.xlsx", "祖研-黑龙江省中医医院（香安院区）", "处理后表格", "4月__祖研香安4月结款函.xlsx"),
    ("中医附二（哈南）5月账单.xlsx", "黑龙江中医药大学附属第二医院（哈南分院）", "原始表格", None),
    ("中医附二（哈南）5月结款涵.xlsx", "黑龙江中医药大学附属第二医院（哈南分院）", "处理后表格", "5月__中医附二（哈南）5月结款涵.xlsx"),
    ("中医附二（哈南）5月包数据.xls", "黑龙江中医药大学附属第二医院（哈南分院）", "处理后表格", "5月__中医附二（哈南）5月包数据.xls"),
    # 第一节：工业大学 6.15–7.14 原始（测试目录名仍为「工程」历史文件夹，见该目录 README）
    (
        "哈尔滨工业大学医院6.15-7.14原始账单.xlsx",
        "哈尔滨工业大学医院",
        "原始表格",
        "工业大学6.15-7.14原始账单.xlsx",
    ),
]

# PDF → 医院目录（存 参考/特殊价格单/）
PDF_MAP: list[tuple[str, str]] = [
    ("哈尔滨市道外区人民医院价格单_000142.pdf", "道外区人民医院"),
    ("哈尔滨市道外区太平人民医院价格单_000143.pdf", "太平人民医院"),
    ("哈尔滨市南岗区妇产医院价格单_000145.pdf", "南岗区妇产医院"),
    ("哈尔滨市呼兰区第一人民医院价格单_000144.pdf", "哈尔滨市呼兰区第一人民医院"),
    ("哈尔滨市三精肾脏病医院价格单_000146.pdf", "三精肾病医院"),
    ("黑龙江中医药大学附属第一医院价格表.pdf", "黑龙江中医药大学附属第一医院"),
    ("黑龙江省医院价格表.pdf", "黑龙江省医院（南岗院区）"),
    ("基础价格表.pdf", "测试用例"),  # skip — handled below
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


def main() -> None:
    results: list[CopyResult] = []
    missing: list[str] = []

    for name, hospital, subdir, dest_name in FILE_MAP:
        src = SUPP / name
        if not src.is_file():
            missing.append(name)
            continue
        dest_dir = CASE / hospital / subdir
        dest = dest_dir / (dest_name if dest_name else name)
        results.append(copy_file(src, dest))

    for pdf_name, hospital in PDF_MAP:
        if hospital == "测试用例":
            continue
        src = PRICE_PDF / pdf_name
        if not src.is_file():
            missing.append(pdf_name)
            continue
        dest = CASE / hospital / "参考" / "特殊价格单" / pdf_name
        results.append(copy_file(src, dest))

    # 省医院 PDF 复制到香坊院区一份（同一价格表）
    src = PRICE_PDF / "黑龙江省医院价格表.pdf"
    if src.is_file():
        dest = CASE / "黑龙江省医院（香坊院区）" / "参考" / "特殊价格单" / src.name
        results.append(copy_file(src, dest))

    base = PRICE_PDF / "基础价格表.pdf"
    if base.is_file():
        dest = ROOT / "铂康" / "参考文件（按照医院）" / "_共享" / "基础价格表.pdf"
        results.append(copy_file(base, dest))

    report = {
        "copied": sum(1 for r in results if r.action == "copied"),
        "skipped": sum(1 for r in results if r.action == "skip_same_size"),
        "missing_sources": missing,
        "details": [r.__dict__ for r in results],
    }
    out = CASE / "bokang_supplement_ingest_report.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("copied", "skipped", "missing_sources")}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
