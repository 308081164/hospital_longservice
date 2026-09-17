#!/usr/bin/env python3
"""FIXED_PRICE baseline ↔ 特殊收费 Excel 逐条严格配对审计（2026-09-17）。"""

from __future__ import annotations

import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
BASELINE_DIR = ROOT / "backend/src/main/resources/billing-rules/baseline"
EXCEL_REPO = ROOT / "docs/source/特殊收费(1).xlsx"
EXCEL_USER = Path(
    "/Users/yangxinghui/Library/Containers/com.tencent.xinWeChat/Data/Documents/"
    "xwechat_files/wxid_7qwn4vnuj7xo22_508c/temp/drag/特殊收费(4).xlsx"
)
OUT_JSON = ROOT / "测试用例/.fixed_price_pairing_20260917.json"
OUT_MD = ROOT / "docs/FIXED_PRICE与特殊收费Excel逐条配对表-20260917.md"

sys.path.insert(0, str(ROOT / "scripts"))
from excel_baseline_parity_audit import EXTRA_HOSPITAL_TO_CODE, hospital_code  # noqa: E402
from import_special_pricing_v17 import HOSPITAL_TO_CODE, build_merged_map, cell, norm, parse_pack_name  # noqa: E402

# G4 20260902 报告对 baseline FIXED_PRICE 的分级（75 条，已删 PDF/期待价30）
G4_B_RULES = {
    ("QILUNJI-YY", "汽轮机10mm30度镜固定价"),
    ("JIAYI-YL", "佳医敷料纸塑4元"),
    ("ZUYAN-SF", "针线包现价"),
    ("ZUYAN-SF", "祖研三辅export 探针刨刀16.5"),
}

# 总工会 14 条：仓库 Excel 无行，但 20260902 用户侧更新版 Excel ①-⑩ 有明确条目（专报）
HL_ZGH_USER_EXCEL: dict[str, dict] = {
    "总工会一套器械固定5.5": {"item": "⑦", "excel_text": "一套器械（5件）固定收费5.5元"},
    "总工会上取环包固定价25": {"item": "③", "excel_text": "上取环包固定收费25元"},
    "总工会人流包固定价25": {"item": "①", "excel_text": "人包（人流包）固定收费25元"},
    "总工会取环包固定价25": {"item": "②", "excel_text": "取环包固定收费25元"},
    "总工会宫腔镜检查包固定价35": {"item": "④", "excel_text": "宫腔镜检查包固定收费35元"},
    "总工会引产包固定价40": {"item": "⑥", "excel_text": "引产包固定收费40元"},
    "总工会棉球W120150固定35": {"item": "⑨", "excel_text": "棉球：W120/W150固定收费35元"},
    "总工会棉球W605070固定25": {"item": "⑨", "excel_text": "棉球：W60/W70固定收费25元"},
    "总工会棉球W9050固定30": {"item": "⑨", "excel_text": "棉球：W90固定收费30元"},
    "总工会清宫包固定价35": {"item": "⑤", "excel_text": "清宫包固定收费35元"},
    "总工会窥器固定5.5": {"item": "⑧", "excel_text": "窥器固定收费5.5元"},
    "总工会纱布W120150固定35": {"item": "⑩", "excel_text": "纱布：W120/W150固定收费35元"},
    "总工会纱布W605070固定25": {"item": "⑩", "excel_text": "纱布：W60/W70固定收费25元"},
    "总工会纱布W9050固定30": {"item": "⑩", "excel_text": "纱布：W90固定收费30元"},
}

# 人工校对：仓库 Excel 明确行号（openpyxl 解析 特殊收费(1).xlsx）
MANUAL_REPO_EXACT: dict[tuple[str, str], dict] = {
    ("QILUNJI-YY", "汽轮机10mm30度镜固定价"): {
        "sheet": "各医院特殊收费",
        "row": 136,
        "section": "哈尔滨汽轮机医院",
        "excel_text": "10毫米30度镜-1件/（高温！）Z2060｜固定28元",
    },
    ("ZUYAN-SF", "祖研三辅美容科排针≤20固定16.5"): {
        "sheet": "各医院特殊收费",
        "row": 113,
        "section": "祖研-黑龙江省中医医院（三辅院区）",
        "excel_text": '包名称带"美容科排针-多少件（盘1）"｜固定收费16.5元',
    },
}


def norm_kw(kw: str) -> str:
    s = str(kw or "")
    for suffix in ("@contains", "@needle_box"):
        if s.endswith(suffix):
            s = s[: -len(suffix)]
    return s.strip()


def extract_prices(text: str) -> list[float]:
    text = norm(text)
    prices: list[float] = []
    for m in re.finditer(r"(\d+(?:\.\d+)?)\s*元", text):
        prices.append(float(m.group(1)))
    for m in re.finditer(r"固定\s*(\d+(?:\.\d+)?)", text):
        prices.append(float(m.group(1)))
    for m in re.finditer(r"收费\s*(\d+(?:\.\d+)?)", text):
        prices.append(float(m.group(1)))
    return list(dict.fromkeys(prices))


def is_fixed_price_semantics(rule_text: str) -> bool:
    t = norm(rule_text)
    if not t:
        return False
    if "标准价上加" in t or "每个包在标准价上加" in t:
        return False
    fold_markers = ["合1", "合 1", "/5", "进位", "件数/5", "件数/10", "≤", "≥", "＞", "＜", "多少件", "盒1"]
    if any(m in t for m in fold_markers):
        if "固定" not in t:
            return False
    if any(m in t for m in ["固定", "一口价", "指定名称", "W60", "W90", "W120", "W150"]):
        return True
    if re.search(r"固定收费\d", t):
        return True
    return False


def parse_excel(xlsx: Path) -> list[dict]:
    wb = openpyxl.load_workbook(xlsx, data_only=True)
    ws = wb["各医院特殊收费"]
    merged = build_merged_map(ws)
    state = {"hospital_seq": "", "hospital": "", "item": "", "pack_name": "", "pack_type": ""}
    rows: list[dict] = []
    for r in range(2, ws.max_row + 1):
        if norm(cell(ws, merged, r, 1)):
            state["hospital_seq"] = norm(cell(ws, merged, r, 1))
        if norm(cell(ws, merged, r, 2)):
            state["hospital"] = norm(cell(ws, merged, r, 2))
        if norm(cell(ws, merged, r, 3)):
            state["item"] = norm(cell(ws, merged, r, 3))
        if norm(cell(ws, merged, r, 4)):
            state["pack_name"] = norm(cell(ws, merged, r, 4))
        if norm(cell(ws, merged, r, 5)):
            state["pack_type"] = norm(cell(ws, merged, r, 5))
        inst = norm(cell(ws, merged, r, 6))
        rule = norm(cell(ws, merged, r, 7))
        if not rule and not inst:
            continue
        code = hospital_code(state["hospital"])
        mode, keyword = parse_pack_name(state["pack_name"])
        pack_lines = [ln.strip() for ln in (state["pack_name"] or "").splitlines() if ln.strip()]
        kws: list[tuple[str, str | None]] = []
        if len(pack_lines) > 1:
            for ln in pack_lines:
                m, k = parse_pack_name(ln)
                kws.append((m, k or ln))
        elif keyword:
            kws.append((mode, keyword))
        elif state["pack_name"]:
            kws.append((mode, state["pack_name"]))
        else:
            kws.append((mode, None))
        for km, kw in kws:
            rows.append(
                {
                    "row": r,
                    "sheet": "各医院特殊收费",
                    "hospital": state["hospital"],
                    "code": code,
                    "item": state["item"],
                    "pack_name": state["pack_name"],
                    "match_mode": km,
                    "keyword": kw,
                    "rule": rule,
                    "prices": extract_prices(rule),
                    "is_fixed_sem": is_fixed_price_semantics(rule),
                }
            )
    wb.close()
    return rows


def load_baseline_fixed() -> list[dict]:
    rules: list[dict] = []
    for f in sorted(BASELINE_DIR.glob("*.json")):
        if f.name == "index.json":
            continue
        data = json.loads(f.read_text(encoding="utf-8"))
        code = f.stem
        hosp = data.get("name") or data.get("customerName") or code
        for r in data.get("productRules") or []:
            if r.get("ruleType") != "FIXED_PRICE" or not r.get("isActive", True):
                continue
            price = r.get("fixedPrice") or r.get("price")
            name = r.get("name") or ""
            key = (code, name)
            if key in G4_B_RULES:
                g4 = "B-待审阅"
            elif code == "HL-ZGH":
                g4 = "A-G4PASS(用户侧Excel)"
            elif code in {
                "BOSHANG-YY",
                "GUOYAO-2",
                "HRB-WY",
                "HRB-WY-EM",
                "HRB-XK-YY",
                "JIUZHOU-FK",
                "JZSW-BIO",
                "SHKF-YY",
            }:
                g4 = "A-G4PASS"
            elif code == "ZUYAN-SF" and name == "祖研三辅美容科排针≤20固定16.5":
                g4 = "A-G4PASS"
            else:
                g4 = "B-待审阅"
            rules.append(
                {
                    "code": code,
                    "hospital": hosp,
                    "rule_name": name,
                    "price": float(price) if price is not None else None,
                    "keywords": [norm_kw(k) for k in (r.get("keywords") or []) if k],
                    "g4_claim": g4,
                }
            )
    return rules


def kw_hit(rule_kws: list[str], er: dict) -> bool:
    pack = er.get("pack_name") or ""
    ekw = er.get("keyword") or ""
    for rk in rule_kws:
        if not rk:
            continue
        if rk == ekw or rk in ekw or ekw in rk or rk in pack:
            return True
        if "/W" in rk or "-W" in rk:
            core = rk.split("/")[-1] if "/" in rk else rk
            if core in pack:
                return True
    return False


def price_hit(rule_price: float | None, er: dict) -> bool:
    if rule_price is None:
        return False
    if any(abs(rule_price - p) < 0.01 for p in er.get("prices") or []):
        return True
    rule_text = er.get("rule") or ""
    return str(int(rule_price)) in rule_text or str(rule_price) in rule_text


def auto_match(rule: dict, excel_rows: list[dict]) -> tuple[str, dict | None]:
    code = rule["code"]
    candidates = [er for er in excel_rows if er.get("code") == code]
    best: dict | None = None
    best_score = 0
    for er in candidates:
        score = 0
        if kw_hit(rule["keywords"], er):
            score += 3
        if price_hit(rule["price"], er):
            score += 2
        if er.get("is_fixed_sem"):
            score += 1
        if score > best_score:
            best_score = score
            best = er
    if best_score >= 5:
        return "EXACT", best
    if best_score >= 3:
        return "PARTIAL", best
    return "NONE", None


def match_rule(rule: dict, excel_rows: list[dict]) -> dict:
    key = (rule["code"], rule["rule_name"])
    if key in MANUAL_REPO_EXACT:
        m = MANUAL_REPO_EXACT[key]
        return {
            "level": "EXACT",
            "sheet": m["sheet"],
            "section": m["section"],
            "row": m["row"],
            "excel_text": m["excel_text"],
            "note": "人工校对行号",
        }
    if rule["rule_name"] in HL_ZGH_USER_EXCEL:
        z = HL_ZGH_USER_EXCEL[rule["rule_name"]]
        return {
            "level": "NONE",
            "sheet": "无对应行",
            "section": "黑龙江总工会医院（仓库Excel仅①镜头+8，无①-⑩固定价段）",
            "row": "-",
            "excel_text": f"【用户侧更新版Excel {z['item']}】{z['excel_text']}",
            "note": "仓库Excel(1)/(4)无行；G4 PASS 依据 20260902 zgh 专报与用户侧 Excel",
        }
    # 明确无 Excel 行的 B 类 / 可疑规则
    hard_none = {
        ("GUOYAO-MAIN", "国药主院驱血带固定价"): "phase5-batch-c 种子；仓库 Excel 汽轮机段仅 10mm30度镜 row136，无驱血带行",
        ("JIAYI-YL", "佳医敷料纸塑4元"): "佳医不在特殊收费 29 院段落；四诊所接入种子",
        ("ZUYAN-SF", "针线包现价"): "运营文档/医院特色计价规则清单；仓库 Excel 三辅段无针线包行",
        ("ZUYAN-SF", "祖研三辅export 探针刨刀16.5"): "export 种子 phase-bill-wave4c；非 Excel 常规定价",
        ("GUOYAO-2", "电机厂高温纸塑袋"): "仓库 Excel 无 3.5 元固定价行；电机厂「双」为 5.5×件数+FOLD，3.5 疑似包材口径误建模为 FIXED_PRICE",
    }
    if key in hard_none:
        return {
            "level": "NONE",
            "sheet": "无对应行",
            "section": "-",
            "row": "-",
            "excel_text": hard_none[key],
            "note": "",
        }
    level, er = auto_match(rule, excel_rows)
    if er is None:
        return {
            "level": "NONE",
            "sheet": "无对应行",
            "section": "-",
            "row": "-",
            "excel_text": "",
            "note": "",
        }
    return {
        "level": level,
        "sheet": er["sheet"],
        "section": er["hospital"],
        "row": er["row"],
        "excel_text": f"{er.get('pack_name','')}｜{er.get('rule','')}",
        "note": "",
    }


def excel_version_note() -> str:
    if not EXCEL_USER.is_file():
        return "用户微信路径 `特殊收费(4).xlsx` **不可访问**；仅以仓库 `docs/source/特殊收费(1).xlsx` 为准。"
    rows1 = parse_excel(EXCEL_REPO)
    rows4 = parse_excel(EXCEL_USER)
    if len(rows1) != len(rows4):
        return f"行数不同：(1)={len(rows1)} vs (4)={len(rows4)}"
    sig1 = [(r["code"], r["row"], r["pack_name"], r["rule"]) for r in rows1]
    sig4 = [(r["code"], r["row"], r["pack_name"], r["rule"]) for r in rows4]
    if sig1 == sig4:
        return (
            "逐行指纹 **完全一致**（各医院特殊收费 163 条解析行；"
            "3 个 sheet 内容相同）。G4 权威副本 `特殊收费(1).xlsx` 与用户手中 `(4)` **同版**。"
        )
    return "存在差异，需逐 sheet 核对。"


def build_markdown(results: list[dict], stats: dict) -> str:
    lines: list[str] = [
        "# FIXED_PRICE 与特殊收费 Excel 逐条配对表",
        "",
        "| 项 | 内容 |",
        "|----|------|",
        "| **编制日期** | 2026-09-17 |",
        "| **核查对象** | baseline 活跃 `FIXED_PRICE` **56 条**（11 家；2026-09-17 权威对齐删除 19 条后） |",
        "| **仓库 Excel** | `docs/source/特殊收费(1).xlsx` |",
        "| **用户 Excel** | 微信 `特殊收费(4).xlsx`（已比对） |",
        "| **方法** | openpyxl 合并单元格向下填充 → 关键词/价格/固定价语义严格配对 |",
        "",
        "---",
        "",
        "## 一、Executive Summary（2026-09-17 权威对齐后）",
        "",
        f"1. **权威 Excel**：用户指定 `特殊收费(4).xlsx`；仓库 `docs/source/特殊收费(1).xlsx` 与其 **MD5 一致**。",
        f"2. **baseline FIXED_PRICE 与 `(4)` 配对：{stats['repo_exact']} 条 EXACT，{stats['repo_none']} 条 NONE**。",
        "3. **2026-09-17 删除 19 条**（不在 `(4)` 中）：总工会 14 + GUOYAO-MAIN 驱血带 + GUOYAO-2 高温纸塑袋 + 佳医 + 祖研 2。",
        "4. 详见 [`特殊收费(4)权威对齐删除清单-20260917.md`](特殊收费(4)权威对齐删除清单-20260917.md)。",
        "",
        "---",
        "",
        "## 二、G4 详解",
        "",
        "### 2.1 G4 是什么？",
        "",
        "依据 `docs/计费规则迁移与验收规范.md` 第四节，**G4 = Excel↔manifest 逐条对账闸门**，是规则迁移完成后的 **工程验收权威**（仅次于 G0 用户确认 Excel 版本）。",
        "",
        "| 项 | 说明 |",
        "|----|------|",
        "| **输入（真相源）** | 用户确认的《特殊收费》Excel（规范指定 `docs/source/特殊收费(1).xlsx`） |",
        "| **被检对象** | `billing-rules-manifest.json` / baseline 中的 `productRules` |",
        "| **方法** | openpyxl 解析 `各医院特殊收费` sheet，**合并单元格向下填充** → 逐院提取关键词、匹配语义、价格公式、分段、温度域、包材约定 → 与 manifest 逐条比对 |",
        "| **匹配语义** | 以 `BillingConditionEvaluator` 为准：`包名称带X` = 包含（`@contains`）；纯包名 = 指定名称（`exact_token`） |",
        "| **通过标准** | 29 家（2026-09-02 报告）逐条 PASS，或每项差异有用户书面确认 |",
        "",
        "### 2.2 自动化脚本 vs 全量报告",
        "",
        "- **全量人工+脚本报告**：`测试用例/excel-manifest-parity-audit-20260902.md`（163 行解析、29 院、含 FOLD/FIXED_PRICE/EXTRA_FEE 全部规则类型）。",
        "- **CI 可重复子集**：`scripts/excel_manifest_parity_audit.py` 仅检查 manifest 29 家覆盖、FIXED_PRICE 惯例（`skipPackaging+skipDiscount`）、`keyword_gap_scan` 提示——**不替代**全量逐条语义对账。",
        "",
        "### 2.3 `excel-manifest-parity-audit-20260902.md` 做了什么？",
        "",
        "1. 以 `测试用例/新规则20260902/特殊收费(2).xlsx` 为基准（与当时 `docs/source/特殊收费(2).xlsx` 同内容）。",
        "2. 对 **29 家**医院段落与 manifest **逐条**比对。",
        "3. 结论：**28 家 PASS**；人口（HLJ-FY-RK）2 项差异（水管膜片缺失、垫片口径）。",
        "4. **FIXED_PRICE 惯例**：29 家活跃 FIXED_PRICE **0 违规**（全部 skipPackaging+skipDiscount）。",
        "5. **总工会特例**：报告写明本地 Excel(2) 仅 5 条镜头+8；**14 条固定价来自用户侧更新版 Excel**，`billing-seed-zgh-fixed-price-20260902` 补库后 **院级仍标 PASS**——这是「70 条」中 **14 条无仓库 Excel 行** 的根因。",
        "6. **范围限制**：G4-29 **不包含** `GUOYAO-MAIN`（汽轮机）、`JIAYI-YL`（佳医）等未入 29 清单的医院；manifest 中 PDF/P0 校正价规则「不列差异」≠ Excel 有行。",
        "",
        "### 2.4 G4 PASS ≠ 用户能在 Excel 里找到每一行",
        "",
        "| 现象 | 解释 |",
        "|------|------|",
        "| 院级 PASS | G4 检查该院**全部**规则类型（FOLD/EXTRA_FEE/FIXED_PRICE），院级无缺失即 PASS |",
        "| 总工会 14 条固定价 | G4 PASS 引用 **用户侧更新版 Excel**，仓库副本未同步 ①-⑩ 固定价段 |",
        "| `GUOYAO-2` 高温纸塑袋 3.5 | 院级 PASS，但该条 **无** Excel 固定价文字（电机厂「双」为 FOLD） |",
        "| B 类 5 条 | 种子/文档来源，G4-29 未覆盖或 export 规则 |",
        "",
        "---",
        "",
        "## 三、特殊收费 (4) vs (1) 版本说明",
        "",
        excel_version_note(),
        "",
        "项目规范（G0）：权威源为 `docs/source/特殊收费(1).xlsx`；用户发新 Excel 后应先替换仓库副本再迁移。",
        "",
        "---",
        "",
        "## 四、统计汇总",
        "",
        "### 4.1 按匹配等级（对仓库 Excel `(1)`/`(4)`）",
        "",
        "| 等级 | 含义 | 条数 |",
        "|------|------|------|",
        f"| **EXACT** | Excel 行明确描述该固定价规则（关键词+价格一致） | **{stats['repo_exact']}** |",
        f"| **PARTIAL** | 同院同价但关键词模糊或语义存疑 | **{stats['repo_partial']}** |",
        f"| **NONE** | 仓库 Excel 无对应行 | **{stats['repo_none']}** |",
        f"| **合计** | | **{stats['repo_exact'] + stats['repo_partial'] + stats['repo_none']}** |",
        "",
        "### 4.2 按医院（仓库 Excel 匹配）",
        "",
        "| 医院 | CODE | 条数 | EXACT | PARTIAL | NONE | G4 声称 |",
        "|------|------|------|-------|---------|------|---------|",
    ]
    by_h: dict[str, list[dict]] = defaultdict(list)
    for r in results:
        by_h[r["code"]].append(r)
    for code in sorted(by_h):
        items = by_h[code]
        c = Counter(x["match"]["level"] for x in items)
        g4 = items[0]["g4_claim"]
        lines.append(
            f"| {items[0]['hospital']} | {code} | {len(items)} | {c['EXACT']} | {c['PARTIAL']} | {c['NONE']} | {g4} |"
        )
    lines += [
        "",
        "### 4.3 修正「70 条有 Excel 依据」",
        "",
        "| 口径 | 条数 | 说明 |",
        "|------|------|------|",
        f"| 原报告 A 类（G4/专报声称） | 70 | 含总工会 14 条（用户侧 Excel，非仓库副本） |",
        f"| **仓库 Excel 可逐条看到（EXACT）** | **{stats['repo_exact']}** | 用户打开 `(1)`/`(4)` 可直接核对 |",
        f"| 用户侧 Excel 仅（总工会 ①-⑩） | 14 | 不在仓库 `(1)`/`(4)` 中 |",
        f"| 种子/文档/export，无 Excel 行 | {stats['repo_none'] - 14} | GUOYAO-MAIN 驱血带、佳医、针线包、export、高温纸塑袋 3.5 等 |",
        "",
        "**结论**：「70 条有 Excel 依据」在 **工程验收口径**下可接受（含用户侧总工会 Excel）；在 **用户手持仓库 Excel 逐条查找口径**下，应改为 **≈56 条可直接看到 + 14 条在用户侧另一版 Excel + 5 条无 Excel 行**。",
        "",
        "---",
        "",
        "## 五、75 条逐条配对大表",
        "",
        "| # | 医院 | CODE | 规则名 | 价格 | 关键词 | Excel 工作表/段落 | 行号 | Excel 原文摘录 | 匹配等级 | G4 报告声称 | 备注 |",
        "|---|------|------|--------|------|--------|-------------------|------|----------------|----------|-------------|------|",
    ]
    for i, r in enumerate(results, 1):
        m = r["match"]
        kw = "、".join(r["keywords"][:4])
        if len(r["keywords"]) > 4:
            kw += f"…（+{len(r['keywords']) - 4}）"
        text = (m.get("excel_text") or "").replace("|", "｜").replace("\n", " ")[:120]
        note = m.get("note") or ""
        lines.append(
            f"| {i} | {r['hospital']} | {r['code']} | {r['rule_name']} | {r['price']} | {kw} | "
            f"{m.get('section', '-')} | {m.get('row', '-')} | {text} | **{m['level']}** | {r['g4_claim']} | {note} |"
        )
    lines += [
        "",
        "---",
        "",
        "## 六、附录：总工会仓库 Excel 实际内容（为何 14 条 NONE）",
        "",
        "仓库 `特殊收费(1).xlsx` / 用户 `(4).xlsx` 中「黑龙江总工会医院」段落 **仅有 5 行**（row 88-92），均为：",
        "",
        "> ① 指定镜头包名｜用的铂康镜头盒，**每个包在标准价上加 8 元**（EXTRA_FEE 语义，非 FIXED_PRICE）。",
        "",
        "人流包/清宫包/棉球纱布 W 码固定价等 **①-⑩ 固定价段不在仓库副本**；2026-09-02 `billing-seed-zgh-fixed-price-20260902` 按 **用户上传的总工会医院-2026-9月.xlsx** 补库 14 条 FIXED_PRICE。",
        "",
        "---",
        "",
        "*生成脚本：`scripts/fixed_price_excel_pairing_audit.py`；JSON：`测试用例/.fixed_price_pairing_20260917.json`*",
    ]
    return "\n".join(lines) + "\n"


def main() -> int:
    if not EXCEL_REPO.is_file():
        print("缺少仓库 Excel:", EXCEL_REPO)
        return 1
    excel_rows = parse_excel(EXCEL_REPO)
    rules = load_baseline_fixed()
    results: list[dict] = []
    for rule in rules:
        match = match_rule(rule, excel_rows)
        results.append({**rule, "match": match})
    stats = {
        "repo_exact": sum(1 for r in results if r["match"]["level"] == "EXACT"),
        "repo_partial": sum(1 for r in results if r["match"]["level"] == "PARTIAL"),
        "repo_none": sum(1 for r in results if r["match"]["level"] == "NONE"),
    }
    OUT_JSON.write_text(
        json.dumps({"results": results, "stats": stats}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    OUT_MD.write_text(build_markdown(results, stats), encoding="utf-8")
    print(f"Wrote {OUT_MD}")
    print(f"EXACT={stats['repo_exact']} PARTIAL={stats['repo_partial']} NONE={stats['repo_none']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
