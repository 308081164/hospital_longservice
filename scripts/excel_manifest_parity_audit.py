#!/usr/bin/env python3
"""G4：权威 Excel ↔ baseline/manifest 逐条对账。缺可表达规则即失败。

权威文件固定为 docs/source/特殊收费20261026.xlsx，并校验 SHA-256。
表里有、baseline 没有（或关键词/类型对不上）的行不能再静默漏掉。
引擎表达不了的行在 ENGINE_UNEXPRESSIBLE 里点名，打印后不失败，也不算已上线。
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "backend/src/main/resources/billing-seeds/billing-rules-manifest.json"
BASELINE_DIR = ROOT / "backend/src/main/resources/billing-rules/baseline"
TEMPLATE_JAVA = (
    ROOT / "backend/src/main/java/com/hospital/backend/service/DefaultPricingTemplate.java"
)
AUTHORITY_XLSX = ROOT / "docs/source/特殊收费20261026.xlsx"
AUTHORITY_SHA256 = "90daf08012c9da0ca9d27105eaf8a9a89d36588ca372fd1516218348874fc8de"

sys.path.insert(0, str(ROOT / "scripts"))
from import_special_pricing_v17 import (  # noqa: E402
    HOSPITAL_TO_CODE,
    cell,
    norm,
    parse_generic_sheet,
    parse_hospital_sheet,
    parse_pack_name,
)
from strict_hospital_codes import FUYI_BASELINE_CODES, STRICT_KEEP_CODES  # noqa: E402

# 引擎没有对应公式，不能写进 manifest 假装上线。键是 (医院序号, Excel 行号)。
ENGINE_UNEXPRESSIBLE: dict[tuple[str, int], str] = {
    ("42", 242): "外架钢针＞6：件数/6进位×5.5＋内层纸塑袋＋0.3，没有「折算后再加袋和固定加价」",
    ("42", 243): "钢针减扳手：(件数-1)/6进位×5.5＋5.5＋内层袋＋0.3",
    ("42", 248): "钉盒按组件拆分：只有植入物-钉收 50，有其他组件再加件数×5.5",
    ("42", 249): "钉盒按组件拆分：只有植入物-钉收 50，有其他组件再加件数×5.5",
    ("42", 250): "钉盒按组件拆分：只有植入物-钉收 50，有其他组件再加件数×5.5",
}

# 底价已经在 baseline，双层袋再加 0.3 要和包名同时成立，引擎做不到这句「再加」。
PARTIAL_RESIDUAL: dict[tuple[str, int], str] = {
    ("42", row): "克氏针≤6 的 5.5/8 元已上线；「双层袋再加 0.3」无法和该固定价同时成立"
    for row in range(231, 240)
}

# 通用表里不是产品规则、由标准计价表达的行（通用 sheet 行号）。
GENERIC_STANDARD_ROWS = {
    19: "包名称带「双」且 1 件低温：标准低温单件双层 35 元（市二另有硬编码不加 35）",
    22: "敷料包纸塑袋宽＜20cm 固定 2.5：标准敷料包",
    23: "敷料包纸塑袋宽≥20cm 固定 4：标准敷料包",
}
GENERIC_UNEXPRESSIBLE = {
    24: "骨科租赁不带植入物：件数含篮筐×5.5，引擎没有篮筐计数",
    25: "骨科租赁带植入物：(件数-钉件数)×5.5+50，引擎没有组件拆分",
}

# 市二「/双 低温不多收、一件一包不收 35」写在 PricingEngine.isHrb2ndHospital，不是产品规则。
ENGINE_BEHAVIOR_ROWS = {
    ("41", 214): "市二低温 /双 按纸塑宽度收、双层不多收、一件一包不收 35（isHrb2ndHospital）",
}

GLOBAL_FOLD_KEYWORDS = (
    "克氏针",
    "银质针",
    "内热针",
    "车针",
    "拔髓针",
    "扩大针",
    "根扩针",
    "卷棉子",
)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def load_baseline(code: str) -> dict:
    path = BASELINE_DIR / f"{code}.json"
    if not path.is_file():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def active_rules(code: str | None) -> list[dict]:
    if not code:
        return []
    return [r for r in (load_baseline(code).get("productRules") or []) if r.get("isActive") is not False]


def split_keyword(raw: str) -> tuple[str, str | None]:
    text = str(raw or "").strip()
    for suffix in ("contains", "exact_token", "exact", "regex", "needle_box"):
        mark = "@" + suffix
        if text.endswith(mark):
            return text[: -len(mark)], suffix
    return text, None


def norm_kw(text: str) -> str:
    return (
        text.replace("（", "(")
        .replace("）", ")")
        .replace(" ", "")
        .replace("\n", "")
        .strip()
        .lower()
    )


def stem(text: str) -> str:
    return norm_kw(text).replace("多少件", "").replace("几件", "")


def rule_keywords(rule: dict) -> list[tuple[str, str | None]]:
    out: list[tuple[str, str | None]] = []
    for raw in rule.get("keywords") or []:
        if raw:
            out.append(split_keyword(str(raw)))
    return out


def regex_hits(pattern: str, sample: str) -> bool:
    try:
        return re.search(pattern, sample, re.IGNORECASE) is not None
    except re.error:
        return False


def keyword_covered(rules: list[dict], mode: str, keyword: str) -> bool:
    target = norm_kw(keyword)
    target_stem = stem(keyword)
    if not target and not target_stem:
        return False
    sample = norm_kw(keyword).replace("多少件", "1件").replace("几件", "1件")
    for rule in rules:
        for bare, suffix in rule_keywords(rule):
            bare_n = norm_kw(bare)
            bare_stem = stem(bare)
            if not bare_n:
                continue
            if suffix == "regex":
                if regex_hits(bare, sample) or regex_hits(bare, target) or regex_hits(bare, target_stem):
                    return True
                continue
            if bare_n == target or bare_stem == target_stem or bare_n == target_stem:
                return True
            if mode == "contains" or suffix == "contains":
                if target_stem.startswith(bare_n) or bare_n.startswith(target_stem):
                    if min(len(bare_n), len(target_stem)) >= 1:
                        return True
            if mode == "exact" and target.startswith(bare_n) and len(bare_n) >= 4:
                return True
            if suffix == "contains" and len(bare_n) >= 2 and bare_n in target:
                return True
            # 精确词作为独立片段出现在更长的指定包名里（铂康-外科包 ← 外科包）
            if mode == "exact" and bare_n in target and len(bare_n) >= 2:
                idx = target.find(bare_n)
                prev = target[idx - 1] if idx > 0 else ""
                if idx == 0 or not ("\u4e00" <= prev <= "\u9fff"):
                    return True
    return False


def type_on_rule(rule: dict, pack_type: str) -> bool:
    types = rule.get("acceptedTypes") or []
    if not types:
        return True
    wanted = norm_kw(pack_type)
    return any(norm_kw(str(t)) == wanted for t in types)


def irrelevant_covered(rules: list[dict], pack_type: str) -> bool:
    for rule in rules:
        if rule.get("keywords"):
            continue
        if type_on_rule(rule, pack_type):
            return True
    return False


def global_fold_covers(keyword: str) -> bool:
    return stem(keyword) in {norm_kw(k) for k in GLOBAL_FOLD_KEYWORDS}


def template_fold_scoped_to_paper() -> bool:
    text = TEMPLATE_JAVA.read_text(encoding="utf-8")
    start = text.find("private static List<Map<String, Object>> genericSmallItemFoldRules()")
    end = text.find("private static Map<String, Object> bag(", start)
    body = text[start:end]
    if body.count('List.of("额外包（纸塑袋）")') < 2:
        return False
    return all(k in body or k in text for k in GLOBAL_FOLD_KEYWORDS)


QUOTE_RE = re.compile(r'[“"]([^”"]+)[”"]')


def quoted_keywords(text: str) -> list[str]:
    return [q.strip() for q in QUOTE_RE.findall(text or "") if q.strip()]


def keyword_or_global(rules: list[dict], mode: str, keyword: str, pack_type: str) -> bool:
    if keyword_covered(rules, mode, keyword):
        return True
    # 通用 8 词只在纸塑袋上由模板承接（市二内热针等院表重复声明）。
    if mode == "contains" and global_fold_covers(keyword) and "纸塑" in pack_type:
        return template_fold_scoped_to_paper()
    return False


def row_covered(row: dict) -> bool:
    code = HOSPITAL_TO_CODE.get(row["hospital"])
    rules = active_rules(code)
    mode = row["match_mode"]
    keyword = row.get("keyword") or ""
    pack_type = row.get("pack_type") or ""
    if mode == "irrelevant" or "无关" in (row.get("pack_name") or ""):
        return irrelevant_covered(rules, pack_type)
    if mode == "generic":
        return True
    # 「包名称带“橄榄头”/“冲洗头”」要两个词都有规则，不能只盖住引号里的第一个。
    quotes = quoted_keywords(row.get("pack_name") or "")
    if mode == "contains" and len(quotes) > 1:
        return all(keyword_or_global(rules, mode, q, pack_type) for q in quotes)
    return keyword_or_global(rules, mode, keyword, pack_type)


def check_authority() -> list[str]:
    if not AUTHORITY_XLSX.is_file():
        return [f"缺少权威表 {AUTHORITY_XLSX}"]
    actual = sha256_file(AUTHORITY_XLSX)
    if actual != AUTHORITY_SHA256:
        return [f"权威表 SHA-256 不符：{actual}"]
    return []


def check_hospital_rows() -> tuple[list[str], list[str]]:
    errors: list[str] = []
    notes: list[str] = []
    wb = openpyxl.load_workbook(AUTHORITY_XLSX, data_only=True)
    rows = parse_hospital_sheet(wb["各医院特殊收费"])
    wb.close()
    seen_hospitals: set[str] = set()
    for row in rows:
        hospital = row["hospital"]
        if hospital not in seen_hospitals:
            seen_hospitals.add(hospital)
            if not HOSPITAL_TO_CODE.get(hospital):
                errors.append(f"序号 {row['hospital_seq']} 医院未映射编码：{hospital.replace(chr(10), ' / ')}")
        key = (str(row["hospital_seq"]), int(row["row"]))
        if key in ENGINE_UNEXPRESSIBLE:
            notes.append(f"引擎做不到 r{row['row']} 序号{row['hospital_seq']}：{ENGINE_UNEXPRESSIBLE[key]}")
            continue
        if key in ENGINE_BEHAVIOR_ROWS:
            notes.append(f"引擎行为 r{row['row']}：{ENGINE_BEHAVIOR_ROWS[key]}")
            continue
        code = HOSPITAL_TO_CODE.get(hospital)
        if not code:
            errors.append(f"r{row['row']} 无编码，无法对账：{hospital}")
            continue
        if not (BASELINE_DIR / f"{code}.json").is_file():
            errors.append(f"r{row['row']} {code} 没有 billing baseline")
            continue
        if not row_covered(row):
            errors.append(
                f"r{row['row']} {code} 表里有、baseline 未覆盖："
                f"{(row.get('pack_name') or '').replace(chr(10), ' / ')[:80]}"
                f" | {row.get('pack_type')} | {(row.get('rule') or '')[:40]}"
            )
        elif key in PARTIAL_RESIDUAL:
            notes.append(f"未完全上线 r{row['row']}：{PARTIAL_RESIDUAL[key]}")
    return errors, notes


def check_generic_rows() -> tuple[list[str], list[str]]:
    errors: list[str] = []
    notes: list[str] = []
    if not template_fold_scoped_to_paper():
        errors.append("通用小件 5合1 未限制在额外包（纸塑袋），无纺布克氏针会再被 5.5 盖住")
    wb = openpyxl.load_workbook(AUTHORITY_XLSX, data_only=True)
    rows = parse_generic_sheet(wb["通用特殊收费"])
    wb.close()
    template = TEMPLATE_JAVA.read_text(encoding="utf-8")
    for row in rows:
        excel_row = int(row["row"])
        if excel_row in GENERIC_STANDARD_ROWS:
            notes.append(f"通用表标准计价 r{excel_row}：{GENERIC_STANDARD_ROWS[excel_row]}")
            continue
        if excel_row in GENERIC_UNEXPRESSIBLE:
            notes.append(f"引擎做不到 通用 r{excel_row}：{GENERIC_UNEXPRESSIBLE[excel_row]}")
            continue
        keyword = row.get("keyword") or ""
        if row["match_mode"] == "contains" and global_fold_covers(keyword):
            if "纸塑" not in (row.get("pack_type") or ""):
                errors.append(f"通用 r{excel_row} {keyword} 不是纸塑袋，不能只靠纸塑折算规则覆盖")
            elif norm_kw(keyword) not in template:
                errors.append(f"通用 r{excel_row} 模板缺少关键词 {keyword}")
            continue
        if keyword and norm_kw(keyword) in template:
            continue
        errors.append(
            f"通用 r{excel_row} 未覆盖：{row.get('pack_name')} | {row.get('pack_type')} | {row.get('rule')}"
        )
    return errors, notes


def check_manifest_customers() -> list[str]:
    errors: list[str] = []
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    customers = manifest.get("customers") or {}
    manifest_codes = set(customers.keys())
    expected = set(STRICT_KEEP_CODES)
    allowed_extra = set(FUYI_BASELINE_CODES)
    missing = expected - manifest_codes
    extra = manifest_codes - expected - allowed_extra
    if missing:
        errors.append(f"manifest 缺失客户: {sorted(missing)}")
    if extra:
        errors.append(f"manifest 多余客户: {sorted(extra)}")
    for code in STRICT_KEEP_CODES:
        rules = customers.get(code, {}).get("productRules") or []
        if not rules and code not in {"GUOYAO-2"}:
            errors.append(f"{code} manifest 无 productRules")
        baseline_rules = active_rules(code)
        manifest_names = {r.get("name") for r in rules}
        for rule in baseline_rules:
            if rule.get("name") not in manifest_names:
                errors.append(f"{code} baseline 规则未进入 manifest：{rule.get('name')}")
    return errors


def check_fixed_price_convention() -> list[str]:
    errors: list[str] = []
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    for code, customer in (manifest.get("customers") or {}).items():
        for rule in customer.get("productRules") or []:
            if rule.get("ruleType") != "FIXED_PRICE" or rule.get("isActive") is False:
                continue
            if not rule.get("skipPackaging") or not rule.get("skipDiscount"):
                errors.append(f"{code} FIXED_PRICE「{rule.get('name')}」须 skipPackaging+skipDiscount")
    return errors


def main() -> int:
    errors = check_authority()
    hospital_errors, hospital_notes = check_hospital_rows()
    generic_errors, generic_notes = check_generic_rows()
    errors.extend(hospital_errors)
    errors.extend(generic_errors)
    errors.extend(check_manifest_customers())
    errors.extend(check_fixed_price_convention())

    for note in hospital_notes + generic_notes:
        print("G4 例外:", note)

    if errors:
        print("G4 Excel↔manifest 逐条对账失败：")
        for err in errors:
            print(" -", err)
        return 1

    print(
        f"G4 OK: {AUTHORITY_XLSX.name} 逐条覆盖 {len(STRICT_KEEP_CODES)} 家，"
        f"例外 {len(hospital_notes) + len(generic_notes)} 条已点名"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
