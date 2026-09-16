#!/usr/bin/env python3
"""组装「账单导出客户确认包」：规则说明 + 按类型/按医院样例 Excel。

用法:
  python3 scripts/build_customer_export_package.py
  python3 scripts/build_customer_export_package.py --out docs/账单导出客户确认包
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE = ROOT / "测试用例"
S8_EXPORTS = TEST_CASE / ".s8_exports"
RULES_SRC = ROOT / "docs" / "账单导出规则审阅清单.md"
CAPS_JSON = ROOT / "backend" / "src/main/resources/hospital-export-capabilities.json"
JOBS_JSON = TEST_CASE / "job_baseline_stable.json"

EXPORT_TYPES = [
    "logistics_allocation",
    "settlement_part",
    "instrument_audit",
    "price_summary",
    "dept_summary",
    "grand_total",
    "settlement",
    "bill",
]

TYPE_LABEL = {
    "bill": "已改账单",
    "settlement": "结款函",
    "dept_summary": "分科室汇总表",
    "price_summary": "价格汇总表",
    "instrument_audit": "把数器械核对表",
    "logistics_allocation": "物流分摊表",
    "grand_total": "总计表",
}

# 按导出类型：代表性医院（便于客户理解「这一类」长什么样）
CATEGORY_SAMPLES: dict[str, list[tuple[str, list[str]]]] = {
    "01-标准账单与结款函": [
        ("香坊中医院", ["bill", "settlement"]),
        ("新发红十字医院", ["bill", "settlement"]),
    ],
    "02-按科室分工作表": [
        ("南岗区妇产医院", ["bill", "settlement"]),
        ("黑龙江省医院（南岗院区）", ["bill", "settlement"]),
        ("黑龙江中医药大学附属第二医院（南岗）", ["bill"]),
    ],
    "03-十一列账单（附一）": [
        (
            "黑龙江中医药大学附属第一医院",
            ["bill", "settlement", "dept_summary", "logistics_allocation"],
        ),
    ],
    "04-九列账单与行合并": [
        ("国药总医院第三院区", ["bill"]),
        ("国药总医院主院区", ["bill"]),
        ("黑龙江省第二医院（南岗院区）", ["bill", "settlement"]),
    ],
    "05-删除或隐藏列": [
        ("道外区人民医院", ["bill", "settlement"]),
        ("哈尔滨冰城医疗美容医院", ["bill", "settlement"]),
        ("哈尔滨市呼兰区第一人民医院", ["bill", "settlement"]),
    ],
    "06-结款函含折扣说明": [
        ("哈尔滨工程大学医院", ["bill", "settlement"]),
    ],
    "07-账单或结款金额折扣展示": [
        ("太平人民医院", ["bill", "settlement"]),
        ("黑龙江省中医药大学附属第三医院（电力）", ["bill", "settlement"]),
        ("哈尔滨市呼兰区第一人民医院", ["bill"]),
    ],
    "08-可额外导出的附表": [
        (
            "哈尔滨市第五医院",
            [
                "bill",
                "settlement",
                "dept_summary",
                "price_summary",
                "instrument_audit",
                "grand_total",
            ],
        ),
        (
            "黑龙江省医院（香坊院区）",
            ["price_summary", "instrument_audit", "logistics_allocation"],
        ),
        ("祖研-黑龙江省中医医院（南岗院区）", ["bill", "price_summary"]),
        ("哈尔滨市第五医院（二门诊）", ["bill", "grand_total"]),
    ],
    "09-强制单表合计": [
        ("哈尔滨工业大学医院", ["bill", "settlement"]),
    ],
}


def parse_export_index() -> dict[str, dict[str, tuple[int, Path]]]:
    """医院 → 导出类型 → (job_id, 源文件路径)。"""
    avail: dict[str, dict[str, tuple[int, Path]]] = defaultdict(dict)
    if not S8_EXPORTS.is_dir():
        return avail
    for f in S8_EXPORTS.glob("job*.xlsx"):
        if "cmp_" in f.name or f.name.endswith("_verify.xlsx"):
            continue
        m = re.match(r"job(\d+)_(.+)\.xlsx$", f.name)
        if not m:
            continue
        job = int(m.group(1))
        rest = m.group(2)
        et = None
        hosp = rest
        for t in EXPORT_TYPES:
            if rest.endswith("_" + t):
                hosp = rest[: -(len(t) + 1)]
                et = t
                break
        if et is None:
            et = "bill"
        prev = avail[hosp].get(et)
        if prev is None or job > prev[0]:
            avail[hosp][et] = (job, f)
    return avail


def find_processed_settlement(hospital: str) -> Path | None:
    proc = TEST_CASE / hospital / "处理后表格"
    if not proc.is_dir():
        return None
    candidates = sorted(proc.glob("*结款*.xlsx"), key=lambda p: p.name)
    june = [p for p in candidates if "6月" in p.name]
    return (june or candidates)[-1] if (june or candidates) else None


def resolve_source(
    hospital: str,
    export_type: str,
    index: dict[str, dict[str, tuple[int, Path]]],
    preferred_job: int | None,
) -> Path | None:
    if preferred_job is not None and S8_EXPORTS.is_dir():
        stable = S8_EXPORTS / f"job{preferred_job}_{hospital}_{export_type}.xlsx"
        if stable.is_file():
            return stable
    by_type = index.get(hospital, {})
    if export_type in by_type:
        return by_type[export_type][1]
    if export_type == "settlement":
        proc = find_processed_settlement(hospital)
        if proc is not None:
            return proc
        # 国药等院历史材料仅有账单、结款函为 docx 或未归档：用标准院结款函作版式参考
        return resolve_source("香坊中医院", "settlement", index, 638)


def copy_sample(
    src: Path,
    dest: Path,
) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dest)


def dest_name(hospital: str, export_type: str) -> str:
    label = TYPE_LABEL.get(export_type, export_type)
    return f"{hospital}-{label}.xlsx"


def build_category_samples(
    out: Path,
    index: dict[str, dict[str, tuple[int, Path]]],
    jobs: dict[str, int],
    manifest: list[dict],
) -> None:
    base = out / "样例-按导出类型"
    for category, entries in CATEGORY_SAMPLES.items():
        cat_dir = base / category
        for hospital, types in entries:
            for et in types:
                src = resolve_source(hospital, et, index, jobs.get(hospital))
                if src is None:
                    manifest.append(
                        {
                            "category": category,
                            "hospital": hospital,
                            "type": et,
                            "status": "missing",
                        }
                    )
                    continue
                dest = cat_dir / dest_name(hospital, et)
                copy_sample(src, dest)
                manifest.append(
                    {
                        "category": category,
                        "hospital": hospital,
                        "type": et,
                        "status": "ok",
                        "file": str(dest.relative_to(out)),
                    }
                )


def build_per_hospital(
    out: Path,
    caps: dict[str, list[str]],
    index: dict[str, dict[str, tuple[int, Path]]],
    jobs: dict[str, int],
    manifest: list[dict],
) -> None:
    base = out / "样例-按医院"
    for hospital, types in sorted(caps.items()):
        hosp_dir = base / hospital
        for et in types:
            if et == "settlement_part":
                continue
            src = resolve_source(hospital, et, index, jobs.get(hospital))
            if src is None:
                manifest.append(
                    {"hospital_folder": hospital, "type": et, "status": "missing"}
                )
                continue
            label = TYPE_LABEL.get(et, et)
            if (
                et == "settlement"
                and hospital in ("国药总医院主院区", "国药总医院第二院区")
                and "香坊中医院" in src.name
            ):
                dest = hosp_dir / f"{label}（标准版式参考）.xlsx"
            else:
                dest = hosp_dir / f"{label}.xlsx"
            copy_sample(src, dest)
            manifest.append(
                {
                    "hospital_folder": hospital,
                    "type": et,
                    "status": "ok",
                    "file": str(dest.relative_to(out)),
                }
            )


def build_review_quick_access(out: Path) -> None:
    """在确认包内增加「审阅速查」分层目录，便于先扫通用格式再查特殊导出。"""
    quick = out / "审阅速查"
    std_bill = quick / "01-通用格式-已改账单（标准8列）"
    std_settle = quick / "02-通用格式-结款函（标准版式）"
    special = quick / "03-特殊导出格式"
    std_bill.mkdir(parents=True, exist_ok=True)
    std_settle.mkdir(parents=True, exist_ok=True)
    special.mkdir(parents=True, exist_ok=True)

    std_src = out / "样例-按导出类型" / "01-标准账单与结款函"
    if std_src.is_dir():
        for f in std_src.glob("*-已改账单.xlsx"):
            copy_sample(f, std_bill / f.name)
        for f in std_src.glob("*-结款函.xlsx"):
            copy_sample(f, std_settle / f.name)

    by_type = out / "样例-按导出类型"
    for cat in CATEGORY_SAMPLES:
        if cat.startswith("01-"):
            continue
        src_dir = by_type / cat
        if not src_dir.is_dir():
            continue
        dest_dir = special / cat
        dest_dir.mkdir(parents=True, exist_ok=True)
        for f in src_dir.glob("*.xlsx"):
            copy_sample(f, dest_dir / f.name)


def write_readme(out: Path, caps: dict[str, list[str]]) -> None:
    readme = out / "00-阅读说明.md"
    readme.write_text(
        """# 账单导出客户确认包

本文件夹供贵方审阅系统**对账完成后的 Excel 导出结果**，与《账单导出规则说明》对照使用。

## 文件夹结构

| 路径 | 内容 |
|------|------|
| `01-账单导出规则说明.md` | 文字说明：各医院导出时有哪些列、是否分表、结款函是否含折扣等 |
| `02-样例文件对照表.md` | 样例 Excel 与规则条目的对照索引 |
| `审阅速查/` | **推荐入口**：通用账单/结款函 + 特殊导出格式分层样例 |
| `样例-按导出类型/` | 按**处理方式**分类的代表性样例（如标准 8 列、11 列、按科室分表等） |
| `样例-按医院/` | **每家医院**一份文件夹，内含该院可导出的各类文件样例 |

## 如何使用

1. 先阅读 `01-账单导出规则说明.md`，了解通用规则与贵院是否在「特殊处理」列表中。  
2. 打开 `审阅速查/`：先看 `01-通用格式-已改账单` 与 `02-通用格式-结款函`，再按需查看 `03-特殊导出格式` 各子文件夹。  
3. 在 `样例-按医院/` 中找到贵院文件夹，打开其中的 Excel，核对列名、工作表数量、金额展示是否符合预期。  
4. 若贵院属于某一类特殊处理（如附一 11 列、国药行合并等），可同时打开 `样例-按导出类型/` 中对应分类下的样例作参照。  
5. 在规则说明文末「审阅确认」处勾选并填写意见；如有差异，请注明医院全称、账期、文件类型及具体列名。

## 样例数据说明

- 样例 Excel 来自系统对 **2026 年 6 月**（及部分医院为相邻账期）真实对账数据的导出结果，仅作版式与规则确认，**不代表贵院当前账期金额**。  
- 文件名格式：`医院名称-文件类型.xlsx`（如 `香坊中医院-已改账单.xlsx`）。  
- 共覆盖 **{count}** 家医院的导出配置。

## 文件类型说明

| 文件名中的类型 | 含义 |
|----------------|------|
| 已改账单 | 校正后的发货明细表 |
| 结款函 | 费用汇总，供财务结款 |
| 分科室汇总表 | 按科室汇总包数、金额（不展开到每一包） |
| 价格汇总表 | 按价格档位汇总 |
| 把数器械核对表 | 科室、包名、把数、包数核对 |
| 物流分摊表 | 物流费在各科室的分摊 |
| 总计表 | 全院或合并口径合计 |

---

*如有疑问，请提供：医院全称、账期、导出文件类型、与预期的差异说明或截图。*
""".format(
            count=len(caps)
        ),
        encoding="utf-8",
    )


def write_index_md(out: Path, caps: dict[str, list[str]], manifest: list[dict]) -> None:
    lines = [
        "# 样例文件对照表",
        "",
        "> 与《账单导出规则说明》配套使用。",
        "",
        "## 一、按导出类型（代表性样例）",
        "",
        "| 分类文件夹 | 说明 | 所含样例 |",
        "|------------|------|----------|",
    ]
    cat_desc = {
        "01-标准账单与结款函": "多数医院采用的 8 列明细 + 标准结款函",
        "02-按科室分工作表": "每个科室单独一个 Sheet",
        "03-十一列账单（附一）": "附一：包装材料、把数、单价（把）等额外列",
        "04-九列账单与行合并": "国药汽轮机口径 9 列及行合并；省二南岗 9 列",
        "05-删除或隐藏列": "道外删列、冰城删备注差额、呼兰一院无器械数列",
        "06-结款函含折扣说明": "结款函单独列出灭菌费折扣行",
        "07-账单或结款金额折扣展示": "导出文件中金额按折扣展示",
        "08-可额外导出的附表": "除账单、结款函外的汇总类附表",
        "09-强制单表合计": "不分科室，始终一张表",
    }
    for cat in CATEGORY_SAMPLES:
        files = [
            m["file"].split("/")[-1]
            for m in manifest
            if m.get("category") == cat and m.get("status") == "ok"
        ]
        lines.append(
            f"| `{cat}` | {cat_desc.get(cat, '')} | {'、'.join(files) if files else '—'} |"
        )

    lines.extend(
        [
            "",
            "## 二、按医院（完整样例）",
            "",
            "在 `样例-按医院/` 下，每家医院一个文件夹。下表列出各院文件夹内应包含的文件类型。",
            "",
            "| 医院 | 文件夹内样例文件 |",
            "|------|------------------|",
        ]
    )
    for hospital, types in sorted(caps.items()):
        labels = [TYPE_LABEL.get(t, t) for t in types if t != "settlement_part"]
        lines.append(f"| {hospital} | {'、'.join(labels)} |")

    lines.extend(
        [
            "",
            "## 三、说明",
            "",
            "- **国药总医院主院区、第二院区**：结款函与多数医院采用相同标准版式；文件夹内「结款函（标准版式参考）」供版式确认，非该院账期数据。",
            "- 样例账期以 2026 年 6 月为主；部分医院为相邻账期（如市五院 5.9–6.8、国药 5.26–6.25）。",
        ]
    )
    missing = [m for m in manifest if m.get("status") == "missing"]
    if missing:
        lines.extend(["", "### 暂未提供的样例", ""])
        for m in missing:
            h = m.get("hospital") or m.get("hospital_folder", "")
            t = TYPE_LABEL.get(m.get("type", ""), m.get("type", ""))
            lines.append(f"- {h} · {t}")

    (out / "02-样例文件对照表.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=ROOT / "docs" / "账单导出客户确认包")
    args = parser.parse_args()
    out: Path = args.out

    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)

    caps_data = json.loads(CAPS_JSON.read_text(encoding="utf-8"))
    caps: dict[str, list[str]] = caps_data["hospitals"]
    jobs = json.loads(JOBS_JSON.read_text(encoding="utf-8")).get("jobs", {})
    index = parse_export_index()
    manifest: list[dict] = []

    rules_dest = out / "01-账单导出规则说明.md"
    rules_text = RULES_SRC.read_text(encoding="utf-8")
    rules_text = rules_text.replace(
        "# 账单导出规则说明（客户审阅版）",
        "# 账单导出规则说明（客户审阅版）\n\n> **配套样例**：请同时打开本文件夹内 `样例-按医院/`（逐院核对）与 `样例-按导出类型/`（按处理方式对照）。详见 `00-阅读说明.md`。\n",
    )
    rules_dest.write_text(rules_text, encoding="utf-8")
    write_readme(out, caps)
    build_category_samples(out, index, jobs, manifest)
    build_per_hospital(out, caps, index, jobs, manifest)
    build_review_quick_access(out)
    write_index_md(out, caps, manifest)

    ok = sum(1 for m in manifest if m.get("status") == "ok")
    miss = sum(1 for m in manifest if m.get("status") == "missing")
    print(f"Done: {out}")
    print(f"  copied: {ok}, missing: {miss}")
    return 0 if miss == 0 else 0


if __name__ == "__main__":
    raise SystemExit(main())
