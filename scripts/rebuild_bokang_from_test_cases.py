#!/usr/bin/env python3
"""从 测试用例/ 反向重建 铂康/ 目录（账单与特殊价格单）。

用于在 铂康 源目录已删除后，将已归档的测试用例 Excel/PDF 同步回 铂康 布局以便入库。
"""

from __future__ import annotations

import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CASE = ROOT / "测试用例"
BOKANG = ROOT / "铂康"
RAW_DIR = BOKANG / "AI账单（原始未处理的）"
PROC_DIR = BOKANG / "2026年账单(正确的)"
PRICE_DIR = BOKANG / "特殊价格单"
DATA_EXTS = {".xlsx", ".xls", ".csv"}


def copy_unique(src: Path, dest_dir: Path) -> int:
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest = dest_dir / src.name
    if dest.exists():
        stem, suffix = src.stem, src.suffix
        n = 2
        while dest.exists():
            dest = dest_dir / f"{stem}_{n}{suffix}"
            n += 1
    shutil.copy2(src, dest)
    return 1


def main() -> None:
    raw_count = 0
    proc_count = 0
    pdf_count = 0
    md_count = 0

    for hospital_dir in sorted(CASE.iterdir()):
        if not hospital_dir.is_dir() or hospital_dir.name.startswith("."):
            continue
        if hospital_dir.name in ("待匹配",):
            continue
        raw = hospital_dir / "原始表格"
        if raw.is_dir():
            for f in raw.iterdir():
                if f.suffix.lower() in DATA_EXTS and f.is_file():
                    raw_count += copy_unique(f, RAW_DIR)
        proc = hospital_dir / "处理后表格"
        if proc.is_dir():
            for f in proc.iterdir():
                if f.suffix.lower() in DATA_EXTS and f.is_file():
                    proc_count += copy_unique(f, PROC_DIR)
        ref_pdf = hospital_dir / "参考" / "特殊价格单"
        if ref_pdf.is_dir():
            for f in ref_pdf.iterdir():
                if f.suffix.lower() == ".pdf" and f.is_file():
                    pdf_count += copy_unique(f, PRICE_DIR)
        rule_md = hospital_dir / "特色账单规则梳理.md"
        if rule_md.is_file():
            dest = PRICE_DIR / f"特色账单规则梳理_{hospital_dir.name}.md"
            if not dest.exists():
                shutil.copy2(rule_md, dest)
                md_count += 1

    unmatched_proc = CASE / "待匹配" / "处理后表格"
    if unmatched_proc.is_dir():
        for f in unmatched_proc.iterdir():
            if f.suffix.lower() in DATA_EXTS and f.is_file():
                proc_count += copy_unique(f, PROC_DIR)

    readme = PRICE_DIR / "README.md"
    if not readme.exists():
        readme.write_text(
            "# 铂康特殊价格单\n\n"
            "PDF 与规则梳理 md 已从 `测试用例/{医院}/参考/特殊价格单/` 同步。\n"
            "完整索引见 `测试用例/优先医院对齐TODO.md` S7 章节。\n",
            encoding="utf-8",
        )

    bokang_readme = BOKANG / "README.md"
    bokang_readme.write_text(
        "# 铂康源材料目录\n\n"
        "本目录由 `scripts/rebuild_bokang_from_test_cases.py` 从 `测试用例/` 反向重建。\n\n"
        "## 子目录\n\n"
        "- `AI账单（原始未处理的）/` — 原始导入格式账单\n"
        "- `2026年账单(正确的)/` — 人工处理后参考账单\n"
        "- `特殊价格单/` — 各院 PDF 价目与规则梳理 md\n"
        "- `建表语句/` — SQL 转储（需从本地备份/U盘放入；`*.sql` 走 Git LFS）\n"
        "- `参考文件（按照医院）/` — 按院参考包（需从本地备份补充）\n\n"
        "## 缺失项\n\n"
        "若 `建表语句/` 或 `参考文件（按照医院）/` 为空，请从开发机/U盘复制后：\n\n"
        "```bash\n"
        "git lfs install\n"
        "git add 铂康/\n"
        "git commit -m \"chore: 补充铂康源材料\"\n"
        "git push\n"
        "```\n",
        encoding="utf-8",
    )

    (BOKANG / "建表语句").mkdir(parents=True, exist_ok=True)
    gitkeep = BOKANG / "建表语句" / ".gitkeep"
    if not gitkeep.exists():
        gitkeep.write_text(
            "请将 hospital_reconciliation_job.sql 等 INSERT 转储放入此目录（Git LFS）。\n",
            encoding="utf-8",
        )

    print(
        f"rebuilt 铂康: raw={raw_count} proc={proc_count} pdf={pdf_count} md={md_count} "
        f"→ {BOKANG}"
    )


if __name__ == "__main__":
    main()
