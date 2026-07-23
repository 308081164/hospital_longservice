#!/usr/bin/env python3
"""Reorganize bill test files into 测试用例/ folder structure."""

from __future__ import annotations

import json
import re
import shutil
from collections import defaultdict
from difflib import SequenceMatcher
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REF_DIR = ROOT / "铂康" / "参考文件（按照医院）"
# 原始/处理后源目录已归档至 测试用例/{医院}/ 与 测试用例/待匹配/；路径保留供重新导入时使用
RAW_DIR = ROOT / "铂康" / "AI账单（原始未处理的）"
PROC_DIR = ROOT / "铂康" / "2026年账单(正确的)"
TARGET_DIR = ROOT / "测试用例"
UNMATCHED_DIR = TARGET_DIR / "待匹配"

DATA_EXTS = {".xlsx", ".xls", ".csv"}

# Aliases derived from reference folders and processed bill naming conventions.
HOSPITAL_ALIASES: dict[str, list[str]] = {
    "三精肾病医院": ["三精", "三精肾病"],
    "南岗区先锋路社区卫生服务中心": ["先锋路社区", "先锋社区", "南岗区先锋路"],
    "南岗区妇产医院": ["南岗妇产", "南岗区妇产"],
    "呼兰中医院": ["呼兰中医院", "呼兰区中医"],
    "呼兰区红十字医院": ["呼兰区红十字", "呼兰红十字", "呼兰红十字医院"],
    "哈尔滨工业大学医院": [
        "工业大学",
        "哈尔滨工程大学医院",
        "工程大学医院",
        "哈工程",
    ],
    "哈尔滨仁胜医院": ["仁胜医院", "仁胜"],
    "哈尔滨冰城医疗美容医院": ["冰城医美", "冰城医疗美容", "冰城"],
    "哈尔滨华夏眼科医院": ["华夏眼科", "华夏医院", "华夏"],
    "哈尔滨市南岗区人民医院（九院）": ["九院", "南岗区人民", "南岗人民"],
    "哈尔滨市呼兰区第一人民医院": [
        "呼兰第一人民",
        "呼兰区第一人民",
        "呼兰人民",
        "呼兰区第一人民医院",
    ],
    "哈尔滨市第一专科医院": [
        "第一专科",
        "第一专科医院",
        "专科水泥",
        "专科宏伟",
        "专科水泥路",
        "专科宏伟路",
        "第一专科水泥",
        "第一专科宏伟",
    ],
    "哈尔滨市第二医院": ["市二院", "第二医院", "哈二院"],
    "哈尔滨市第五医院": ["市五院", "第五医院", "哈五院", "哈尔滨市第五医院"],
    "哈尔滨市第五医院（二门诊）": [
        "第五医院（二门诊）",
        "五院二门诊",
        "二门诊",
        "第五医院二门诊",
    ],
    "哈尔滨市红十字妇产医院": [
        "哈尔滨红十字",
        "哈尔滨红十字妇产",
        "红十字妇产",
        "哈红十字",
        "红十字妇产医院",
    ],
    "哈尔滨市骨伤科医院": ["骨伤科", "骨伤", "哈尔滨市骨伤科"],
    "国药总医院主院区": [
        "国药主院区",
        "国药总医院主",
        "国药总院主",
        "国药医院主院区",
        "汽轮机",
        "国药总医院主院区",
    ],
    "国药总医院第三院区": [
        "国药第三院区",
        "国药三院",
        "国药总医院第三",
        "锅炉厂",
        "国药总医院第三院区",
    ],
    "国药总医院第二院区": [
        "国药第二院区",
        "国药二院",
        "国药总医院第二",
        "电机厂",
        "国药总医院第二院区",
    ],
    "太平人民医院": ["太平人民", "太平医院", "太平"],
    "奥兰医院": ["奥兰", "奥兰医院"],
    "悦美芳华医疗门诊医院": ["悦美芳华", "悦美芳华医疗", "悦美"],
    "新发红十字医院": ["新发红十字", "新发"],
    "武警黑龙江省总队医院": ["武警", "武警总队", "武警黑龙江省总队"],
    "祖研-黑龙江省中医医院（三辅院区）": [
        "三辅",
        "三辅社区",
        "三辅院区",
        "祖研-黑龙江省中医医院（三辅",
    ],
    "祖研-黑龙江省中医医院（南岗院区）": [
        "祖研南岗",
        "祖研-黑龙江省中医医院（南岗",
        "黑龙江省中医医院（南岗",
    ],
    "祖研-黑龙江省中医医院（香安院区）": [
        "香安院区",
        "祖研香安",
        "祖妍香安",
        "祖研-黑龙江省中医医院（香安",
        "黑龙江省中医医院（香安",
    ],
    "道外区人民医院": ["道外人民", "道外区人民", "道外区人民医院"],
    "香坊中医院": ["香坊中医", "香坊中医院"],
    "黑龙江东大肛肠": ["东大肛肠", "东大", "现代肛肠"],
    "黑龙江中医药大学附属第一医院": [
        "中医附一",
        "附一",
        "中医药大学附一",
        "黑龙江中医药大学附属第一",
    ],
    "黑龙江中医药大学附属第二医院（南岗）": [
        "中医附二",
        "附二",
        "中医附二（南岗",
        "中医药大学附二",
    ],
    "黑龙江中医药大学附属第二医院（哈南分院）": [
        "中医附二（哈南",
        "附二（哈南",
        "附二哈南",
        "哈南分院",
        "中医附二4月（哈南",
    ],
    "黑龙江九洲妇科医院": ["九洲妇科", "九洲", "九州妇科", "九州", "黑龙江九洲妇科"],
    "黑龙江省中医药大学附属第三医院（电力）": [
        "中医附三",
        "附三",
        "电力医院",
        "黑龙江省中医药大学附属第三",
        "黑龙江中医药大学附属第三",
    ],
    "黑龙江省医院（南岗院区）": [
        "南岗省医院",
        "省医院南岗",
        "南岗账单",
        "南岗备货",
        "南岗",
        "黑龙江省医院（南岗",
    ],
    "黑龙江省医院（香坊院区）": [
        "香坊省医院",
        "省医院香坊",
        "香坊账单",
        "香坊备货",
        "香坊",
        "黑龙江省医院（香坊",
    ],
    "黑龙江省社会康复医院": ["社会康复", "省康复", "监狱", "康复医院", "康复"],
    "黑龙江省第二医院（南岗院区）": [
        "省二院南岗",
        "省二南岗",
        "二院南岗",
        "省二院（南岗",
        "黑龙江省第二医院（南岗",
    ],
    "黑龙江省第二医院（松北院区）": [
        "省二院松北",
        "省二松北",
        "二院松北",
        "省二院（松北",
        "黑龙江省第二医院（松北",
    ],
    "黑龙江省远东心脑血管医院": ["远东心脑血管", "远东", "远东医院"],
    "黑龙江维多利亚妇产医院": [
        "维多利亚",
        "维多",
        "黑龙江维多利亚",
        "维多利亚妇产",
    ],
}

# Disambiguation: if text contains key, only these hospitals may match.
EXCLUSIVE_KEYWORDS: dict[str, list[str]] = {
    "哈南": ["黑龙江中医药大学附属第二医院（哈南分院）"],
    "（哈南": ["黑龙江中医药大学附属第二医院（哈南分院）"],
    "南岗省医院": ["黑龙江省医院（南岗院区）"],
    "香坊省医院": ["黑龙江省医院（香坊院区）"],
    "省二院南岗": ["黑龙江省第二医院（南岗院区）"],
    "省二院松北": ["黑龙江省第二医院（松北院区）"],
    "省二院（南岗": ["黑龙江省第二医院（南岗院区）"],
    "省二院（松北": ["黑龙江省第二医院（松北院区）"],
    "二门诊": ["哈尔滨市第五医院（二门诊）"],
    "呼兰第一人民": ["哈尔滨市呼兰区第一人民医院"],
    "第一专科": ["哈尔滨市第一专科医院"],
    "专科水泥": ["哈尔滨市第一专科医院"],
    "专科宏伟": ["哈尔滨市第一专科医院"],
    "呼兰红十字": ["呼兰区红十字医院"],
    "哈尔滨红十字": ["哈尔滨市红十字妇产医院"],
    "新发红十字": ["新发红十字医院"],
    "三辅社区、香坊中医院": ["香坊中医院", "祖研-黑龙江省中医医院（三辅院区）"],
    "汽轮机": ["国药总医院主院区"],
    "锅炉厂": ["国药总医院第三院区"],
    "电机厂": ["国药总医院第二院区"],
    "监狱": ["黑龙江省社会康复医院"],
    "康复4月": ["黑龙江省社会康复医院"],
    "康复5月": ["黑龙江省社会康复医院"],
    "康复6月": ["黑龙江省社会康复医院"],
    "南岗账单": ["黑龙江省医院（南岗院区）"],
    "香坊账单": ["黑龙江省医院（香坊院区）"],
    "南岗备货": ["黑龙江省医院（南岗院区）"],
    "香坊备货": ["黑龙江省医院（香坊院区）"],
}


def normalize(text: str) -> str:
    text = text.replace("（", "(").replace("）", ")").replace(" ", "")
    text = text.replace("-", "").replace("_", "").replace("·", "")
    return text


def collect_hospitals() -> list[str]:
    return sorted(d.name for d in REF_DIR.iterdir() if d.is_dir())


def collect_data_files(folder: Path, recursive: bool = False) -> list[Path]:
    if not folder.exists():
        return []
    if recursive:
        paths = folder.rglob("*")
    else:
        paths = folder.iterdir()
    return [
        p
        for p in paths
        if p.is_file() and p.suffix.lower() in DATA_EXTS
    ]


def reference_hints(hospital: str) -> list[str]:
    ref_path = REF_DIR / hospital
    hints: list[str] = []
    if ref_path.is_dir():
        for p in ref_path.iterdir():
            if p.is_file():
                hints.append(p.stem)
    return hints


def score_hospital(hospital: str, text: str, hints: list[str]) -> int:
    text_n = normalize(text)
    hospital_n = normalize(hospital)

    # Exclusive keyword filter
    for keyword, allowed in EXCLUSIVE_KEYWORDS.items():
        if keyword in text and hospital not in allowed:
            return 0
        if keyword in text and hospital in allowed:
            return 120 + len(keyword)

    if hospital_n in text_n:
        return 110 + len(hospital_n)

    best = 0
    for alias in HOSPITAL_ALIASES.get(hospital, []):
        alias_n = normalize(alias)
        if alias_n and alias_n in text_n:
            best = max(best, 95 + len(alias_n))

    for hint in hints:
        hint_n = normalize(hint)
        if len(hint_n) >= 4 and hint_n in text_n:
            best = max(best, 100 + len(hint_n))

    # Partial name chunks (>=4 chars)
    for chunk in re.split(r"[()（）、，,\-]", hospital):
        chunk = chunk.strip()
        if len(chunk) >= 4:
            chunk_n = normalize(chunk)
            if chunk_n in text_n:
                best = max(best, 75 + len(chunk_n))

    # Penalize ambiguous short alias collisions
    if "哈南" in text_n and hospital == "黑龙江中医药大学附属第二医院（南岗）":
        return 0
    if "哈南" not in text_n and hospital == "黑龙江中医药大学附属第二医院（哈南分院）":
        if "中医附二" in text_n or "附二" in text_n:
            return 0

    return best


def match_file(path: Path, hospitals: list[str], hint_map: dict[str, list[str]]) -> tuple[str | None, int]:
    path_parts = [p for p in path.parts[-5:] if p not in {".", ".."}]
    text = " ".join([path.stem, *path_parts])
    scores = [
        (h, score_hospital(h, text, hint_map[h]))
        for h in hospitals
    ]
    scores.sort(key=lambda x: (-x[1], x[0]))
    best_h, best_s = scores[0]
    second_s = scores[1][1] if len(scores) > 1 else 0

    if best_s >= 75 and best_s >= second_s:
        return best_h, best_s
    if best_s >= 90 and best_s > second_s - 3:
        return best_h, best_s
    return None, best_s


def unique_dest_name(dest_dir: Path, filename: str) -> str:
    dest = dest_dir / filename
    if not dest.exists():
        return filename
    stem = Path(filename).stem
    suffix = Path(filename).suffix
    i = 1
    while True:
        candidate = f"{stem}_{i}{suffix}"
        if not (dest_dir / candidate).exists():
            return candidate
        i += 1


def copy_files(files: list[Path], dest_dir: Path, rel_base: Path | None = None) -> int:
    dest_dir.mkdir(parents=True, exist_ok=True)
    copied = 0
    for src in files:
        if rel_base and src.is_relative_to(rel_base):
            rel = src.relative_to(rel_base)
            if len(rel.parts) > 1:
                name = "__".join(rel.parts)
            else:
                name = rel.name
        else:
            name = src.name
        name = unique_dest_name(dest_dir, name)
        shutil.copy2(src, dest_dir / name)
        copied += 1
    return copied


def main() -> None:
    hospitals = collect_hospitals()
    hint_map = {h: reference_hints(h) for h in hospitals}

    raw_files = collect_data_files(RAW_DIR, recursive=False)
    proc_files = collect_data_files(PROC_DIR, recursive=True)

    raw_map: dict[str, list[Path]] = defaultdict(list)
    proc_map: dict[str, list[Path]] = defaultdict(list)
    raw_orphans: list[tuple[str, int]] = []
    proc_orphans: list[tuple[str, int]] = []

    for p in raw_files:
        h, score = match_file(p, hospitals, hint_map)
        if h:
            raw_map[h].append(p)
        else:
            raw_orphans.append((p.name, score))

    for p in proc_files:
        h, score = match_file(p, hospitals, hint_map)
        if h:
            proc_map[h].append(p)
        else:
            proc_orphans.append((str(p.relative_to(PROC_DIR)), score))

    if TARGET_DIR.exists():
        shutil.rmtree(TARGET_DIR)
    TARGET_DIR.mkdir(parents=True)

    stats = {
        "both": [],
        "only_raw": [],
        "only_processed": [],
        "neither": [],
    }
    copy_stats = {"raw": 0, "processed": 0}

    for hospital in hospitals:
        hospital_dir = TARGET_DIR / hospital
        hospital_dir.mkdir(parents=True, exist_ok=True)

        raw_list = raw_map.get(hospital, [])
        proc_list = proc_map.get(hospital, [])

        raw_folder = "原始表格" if raw_list else "【缺】原始表格"
        proc_folder = "处理后表格" if proc_list else "【缺】处理后表格"

        (hospital_dir / raw_folder).mkdir(exist_ok=True)
        (hospital_dir / proc_folder).mkdir(exist_ok=True)

        if raw_list:
            copy_stats["raw"] += copy_files(raw_list, hospital_dir / raw_folder)
        if proc_list:
            copy_stats["processed"] += copy_files(
                proc_list, hospital_dir / proc_folder, rel_base=PROC_DIR
            )

        if raw_list and proc_list:
            stats["both"].append(hospital)
        elif raw_list:
            stats["only_raw"].append(hospital)
        elif proc_list:
            stats["only_processed"].append(hospital)
        else:
            stats["neither"].append(hospital)

    report = {
        "total_hospitals": len(hospitals),
        "both_count": len(stats["both"]),
        "only_raw_count": len(stats["only_raw"]),
        "only_processed_count": len(stats["only_processed"]),
        "neither_count": len(stats["neither"]),
        "raw_source_files": len(raw_files),
        "processed_source_files": len(proc_files),
        "raw_copied": copy_stats["raw"],
        "processed_copied": copy_stats["processed"],
        "raw_orphans": raw_orphans,
        "processed_orphans": proc_orphans,
        "stats": stats,
    }

    report_path = TARGET_DIR / "reorganization_report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    readme = f"""# 测试用例目录

本目录由 `scripts/reorganize_test_cases.py` 自动生成，用于按医院组织账单测试数据。

## 目录结构

```
测试用例/
  {{医院名称}}/
    原始表格/          # 来自 铂康/AI账单（原始未处理的）/（系统导入格式）
    处理后表格/        # 来自 铂康/2026年账单(正确的)/（人工处理导出格式）
```

若某医院缺少某一类文件，对应子目录命名为 `【缺】原始表格` 或 `【缺】处理后表格`。

## 统计摘要

| 项目 | 数量 |
|------|------|
| 医院总数 | {len(hospitals)} |
| 原始+处理后均有 | {len(stats['both'])} |
| 仅有原始表格 | {len(stats['only_raw'])} |
| 仅有处理后表格 | {len(stats['only_processed'])} |
| 两类均缺 | {len(stats['neither'])} |
| 已复制原始文件 | {copy_stats['raw']} |
| 已复制处理后文件 | {copy_stats['processed']} |
| 未匹配原始文件 | {len(raw_orphans)} |
| 未匹配处理后文件 | {len(proc_orphans)} |

详细匹配结果见 `reorganization_report.json`。

## 数据来源

- 医院列表：`铂康/参考文件（按照医院）/` 下的 42 个子文件夹
- 原始表格：`铂康/AI账单（原始未处理的）/`（已归档至本目录或 `待匹配/原始表格/`）
- 处理后表格：`铂康/2026年账单(正确的)/`（已归档至本目录或 `待匹配/处理后表格/`）

原始文件未被移动，仅复制到本目录。无法匹配的文件见 `待匹配/`。
"""
    (TARGET_DIR / "README.md").write_text(readme, encoding="utf-8")

    print(json.dumps({k: v for k, v in report.items() if k != "stats"}, ensure_ascii=False, indent=2))
    print("\n--- 分类详情 ---")
    for key, label in [
        ("both", "原始+处理后均有"),
        ("only_raw", "仅有原始"),
        ("only_processed", "仅有处理后"),
        ("neither", "两类均缺"),
    ]:
        print(f"\n{label} ({len(stats[key])}):")
        for h in stats[key]:
            print(f"  - {h}")


if __name__ == "__main__":
    main()
