# -*- coding: utf-8 -*-
"""正式 G2 门禁：所有严格医院 exact_token 规则关键词 vs 真实账单包名的词中失配。

复刻后端 exact_token 语义：关键词出现位置前后必须是边界（非 CJK）。
CJK 范围与后端 isCjkChar 一致：U+4E00-9FFF、U+3400-4DBF。
"""
import glob
import json
import re
import sys
import unicodedata
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = json.load(open(ROOT / "backend/src/main/resources/billing-seeds/billing-rules-manifest.json"))

sys.path.insert(0, str(ROOT / "scripts"))
from strict_hospital_codes import STRICT_BY_CODE  # noqa: E402

HOSPITAL_DIRS = {
    code: h.folder
    for code, h in STRICT_BY_CODE.items()
    if h.folder
}


def is_cjk(ch: str) -> bool:
    o = ord(ch)
    return (0x4E00 <= o <= 0x9FFF) or (0x3400 <= o <= 0x4DBF)


def normalize(text: str) -> str:
    return (
        text.replace("（", "(").replace("）", ")").replace("【", "[").replace("】", "]").replace(" ", "")
    )


def exact_token_match(text: str, kw: str) -> bool:
    text = normalize(text)
    kw = normalize(kw)
    if not kw:
        return False
    start = 0
    while True:
        i = text.find(kw, start)
        if i < 0:
            return False
        prev_ok = i == 0 or not is_cjk(text[i - 1])
        j = i + len(kw)
        next_ok = j >= len(text) or not is_cjk(text[j])
        if prev_ok and next_ok:
            return True
        start = i + 1


def rule_mode(rule: dict) -> str:
    m = rule.get("keywordMatchMode")
    if m:
        return m
    return "exact_token" if rule.get("ruleType") == "FOLD" else "contains"


def collect_pack_names(dirname: str) -> set:
    names = set()
    for f in glob.glob(str(ROOT / "测试用例" / dirname / "**" / "*.xlsx"), recursive=True):
        try:
            wb = openpyxl.load_workbook(f, data_only=True, read_only=True)
        except Exception:
            continue
        for ws in wb.worksheets:
            for row in ws.iter_rows(values_only=True):
                for v in row:
                    if isinstance(v, str) and 2 <= len(v) <= 60 and not v.startswith("="):
                        names.add(v)
        wb.close()
    return names


def main() -> int:
    findings = []
    for code, c in MANIFEST["customers"].items():
        dirname = HOSPITAL_DIRS.get(code)
        if not dirname or not (ROOT / "测试用例" / dirname).exists():
            continue
        names = collect_pack_names(dirname)
        for rule in c.get("productRules", []):
            if not rule.get("isActive", True):
                continue
            mode = rule_mode(rule)
            for kw in rule.get("keywords") or []:
                if "@contains" in kw or "@exact" in kw:
                    continue
                if mode != "exact_token":
                    continue
                for name in names:
                    n = normalize(name)
                    k = normalize(kw)
                    if k in n and not exact_token_match(name, kw):
                        findings.append((code, c.get("name"), rule.get("name"), kw, name))
    seen = set()
    for code, cname, rname, kw, name in sorted(findings):
        key = (code, rname, kw, name)
        if key in seen:
            continue
        seen.add(key)
        print(f"{code} | {rname} | 关键词[{kw}] | 真实包名[{name}]")
    return 1 if seen else 0


if __name__ == "__main__":
    raise SystemExit(main())
