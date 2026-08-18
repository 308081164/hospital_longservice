#!/usr/bin/env python3
"""Compare customer Excel billing rules vs system manifest/seeds."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path
from typing import Any

import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "测试用例/billing_rules_manifest.json"
DEFAULT_TEMPLATE_JAVA = ROOT / "backend/src/main/java/com/hospital/backend/service/DefaultPricingTemplate.java"
OUTPUT = ROOT / "测试用例" / f"客户收费规则与系统规则对比分析-{date.today().strftime('%Y%m%d')}.md"

STANDARD_XLSX = Path(
    "/Users/yangxinghui/Library/Containers/com.tencent.xinWeChat/Data/Documents/"
    "xwechat_files/wxid_7qwn4vnuj7xo22_508c/temp/drag/标准收费部分(1).xlsx"
)
SPECIAL_XLSX = Path(
    "/Users/yangxinghui/Library/Containers/com.tencent.xinWeChat/Data/Documents/"
    "xwechat_files/wxid_7qwn4vnuj7xo22_508c/temp/drag/特殊收费(12).xlsx"
)
UNIFIED_SHEET = "通用特殊收费"
LT_GENERAL_SHEET = "环氧与低温通用收费"

# 客户简称 → (系统 code, 系统全称, 匹配置信度)
HOSPITAL_MAP: dict[str, tuple[str | None, str, str]] = {
    "冰城医美": ("BINGCHENG-YM", "哈尔滨冰城医疗美容医院", "已确认"),
    "电机厂医院": ("GUOYAO-2", "国药总医院第二院区", "已确认（导出别名）"),
    "方南南医院": ("FNN-YY", "方南南医院", "已确认"),
    "东北农业大学": ("NEAU-YY", "东北农业大学医院", "已确认"),
    "哈尔滨工程大学": ("HRB-HEU", "哈尔滨工程大学医院", "已确认"),
    "松电慢病": ("HRB-SD-MB", "哈尔滨道外区松电慢性病专科门诊部", "已确认"),
    "航天风华": ("HRB-HTFH", "哈尔滨航天风华医院", "已确认（HardcodedRules）"),
    "美意医疗": ("MEIYI-YL", "美意医疗", "已确认"),
    "易丽医疗": ("YILI-YL", "易丽医疗", "已确认"),
    "佳医医疗": ("JIAYI-YL", "佳医医疗", "已确认"),
    "市五院（二门诊）": ("HRB-WY-EM", "哈尔滨市第五医院（二门诊）", "已确认"),
    "九州医院": ("JIUZHOU-FK", "黑龙江九洲妇科医院", "已确认"),
    "博尚医院": ("BOSHANG-YY", "博尚医院", "v8新建"),
    "黑龙江省海员总医院（松北）": ("HAIYUAN-SB", "黑龙江省海员总医院（松北）", "v8新建"),
    "黑龙江省妇幼保健院（人口）": ("HLJ-FY-RK", "黑龙江省妇幼保健院（人口）", "v8新建"),
    "祖研-黑龙江省中医医院（南岗院区）": ("ZUYAN-NG", "祖研-黑龙江省中医医院（南岗院区）", "已确认"),
    "黑龙江省社会康复医院": ("SHKF-YY", "黑龙江省社会康复医院", "已确认"),
    "哈尔滨市道里区妇幼保健院": ("DL-FUCHAN", "哈尔滨市道里区妇幼保健院", "v8新建"),
    "春语医疗美容医院": ("CHUNYU-YL", "春语医疗美容医院", "v8新建"),
    "黑龙江总工会医院": ("HL-ZGH", "黑龙江总工会医院", "HardcodedRules"),
    "哈尔滨基准生物有限公司": ("JZSW-BIO", "哈尔滨基准生物科技有限公司", "已确认"),
    "索菲医疗美容门诊": ("SUOFEI-YL", "索菲医疗美容门诊", "v8新建"),
    "省监狱管理局医院": ("HLJ-JYGLJ-YY", "省监狱管理局医院", "已确认"),
    "呼兰中医院": ("HULAN-TCM", "呼兰中医院", "已确认"),
    "平房区人民医院": ("PFQ-RM", "哈尔滨市平房区人民医院", "Excel12新增"),
}

# HardcodedRulesMigrationRunner 中未完全写入 manifest 的规则
HARDCODED_SUPPLEMENT: dict[str, list[dict[str, Any]]] = {
    "HRB-HTFH": [
        {
            "ruleType": "FOLD",
            "name": "航天风华镍钛锉 5 件算 1 件",
            "keywords": ["镍钛锉"],
            "threshold": 5,
            "foldRatio": 5,
            "isActive": True,
            "source": "HardcodedRulesMigrationRunner",
        },
        {
            "ruleType": "PRICE_PER_INSTRUMENT",
            "name": "航天风华挖勺每件 5.5 元",
            "price": 5.5,
            "keywords": ["挖勺"],
            "isActive": True,
            "source": "HardcodedRulesMigrationRunner",
        },
    ],
    "NEAU-YY": [
        {
            "ruleType": "PRICE_PER_INSTRUMENT",
            "name": "东北农业大学医院洁牙机尖每件 5.5 元",
            "price": 5.5,
            "keywords": ["洁牙机尖"],
            "isActive": True,
            "source": "HardcodedRulesMigrationRunner",
        },
    ],
}

# 统一特殊收费关键词 → 系统默认 needle 配置
GLOBAL_NEEDLE_KEYWORDS = [
    "克氏针", "银质针", "内热针", "车针", "拔髓针", "扩大针", "缝合针", "卷棉子", "双",
]

SEVERITY = {"critical": "严重", "major": "主要", "minor": "次要", "info": "提示", "pending": "待确认"}


@dataclass
class Conflict:
    field: str
    customer_value: str
    system_value: str
    severity: str
    impact: str
    suggestion: str


@dataclass
class HospitalReport:
    customer_name: str
    system_code: str | None
    system_name: str
    match_status: str
    conflicts: list[Conflict] = field(default_factory=list)
    matched_rules: list[str] = field(default_factory=list)
    customer_rules: list[dict[str, Any]] = field(default_factory=list)


def load_manifest() -> dict[str, Any]:
    return json.loads(MANIFEST.read_text(encoding="utf-8"))


def system_rules_for(code: str | None, manifest: dict[str, Any]) -> list[dict[str, Any]]:
    if not code:
        return []
    entry = (manifest.get("customers") or {}).get(code) or {}
    rules = [r for r in (entry.get("productRules") or []) if r.get("isActive", True)]
    for extra in HARDCODED_SUPPLEMENT.get(code, []):
        if not any(r.get("name") == extra.get("name") for r in rules):
            rules.append(extra)
    return rules


def normalize_text(s: Any) -> str:
    if s is None or (isinstance(s, float) and pd.isna(s)):
        return ""
    return re.sub(r"\s+", "", str(s))


def extract_keyword(rule_text: str) -> str:
    m = re.search(r"[「\"“](.+?)[」\"”]", rule_text)
    return m.group(1) if m else rule_text.strip()


def parse_standard_excel() -> dict[str, Any]:
    sections: dict[str, list[dict[str, str]]] = {
        "系统标准价": [],
        "外来器械收费": [],
        "通用特殊收费": [],
    }
    if not STANDARD_XLSX.is_file():
        return sections
    df = pd.read_excel(STANDARD_XLSX, sheet_name="Sheet1", header=None)
    current = "系统标准价"
    for _, row in df.iterrows():
        c0, c1, c2 = row.get(0), row.get(1), row.get(2)
        c4, c5 = row.get(4), row.get(5)
        c8, c9 = row.get(8), row.get(9)
        if isinstance(c0, str) and "系统标准价" in c0:
            current = "系统标准价"
        if isinstance(c4, str) and "外来器械" in normalize_text(c4):
            current = "外来器械收费"
        if isinstance(c8, str) and "通用特殊收费" in normalize_text(c8):
            current = "通用特殊收费"
        label = ""
        value = ""
        if current == "系统标准价" and (c1 or c2):
            label = str(c1 or c0 or "").strip()
            value = str(c2 or "").strip()
        elif current == "外来器械收费" and (c5 or c6 if len(row) > 6 else None):
            label = str(c5 or c4 or "").strip()
            value = str(row.get(6) or "").strip()
        elif current == "通用特殊收费" and c9:
            label = str(c9 or "").strip()
            value = str(row.get(10) or "").strip()
        if label and label != "nan":
            sections[current].append({"label": label, "value": value})
    return sections


def parse_special_excel() -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    if not SPECIAL_XLSX.is_file():
        raise FileNotFoundError(f"客户特殊收费文件不可访问: {SPECIAL_XLSX}")
    per_hospital = pd.read_excel(SPECIAL_XLSX, sheet_name="各医院特殊收费")
    unified = pd.read_excel(SPECIAL_XLSX, sheet_name=UNIFIED_SHEET)
    per_hospital["医院名称"] = per_hospital["医院名称"].ffill()
    per_hospital["医院序号"] = per_hospital["医院序号"].ffill()
    hospitals: list[dict[str, Any]] = []
    for (seq, name), grp in per_hospital.groupby(["医院序号", "医院名称"], sort=False):
        rules = []
        for _, r in grp.iterrows():
            pkg = r.get("包名称（或包名中特殊信息）")
            if pd.isna(pkg) and pd.isna(r.get("收费规则")):
                continue
            rules.append(
                {
                    "seq": r.get("项目序号"),
                    "package": str(pkg or "").strip(),
                    "pack_type": str(r.get("包类型") or "").strip(),
                    "instrument_count": str(r.get("包内器械件数") or "").strip(),
                    "pricing_rule": str(r.get("收费规则") or "").strip(),
                    "note": str(r.get("备注") or "").strip(),
                }
            )
        hospitals.append({"seq": seq, "name": str(name).strip(), "rules": rules})
    unified_rules = []
    current_seq = None
    for _, r in unified.iterrows():
        if not pd.isna(r.get("序号")):
            current_seq = r.get("序号")
        pkg = r.get("包名称（或包名中特殊信息）")
        if pd.isna(pkg) and pd.isna(r.get("收费规则")):
            continue
        unified_rules.append(
            {
                "seq": current_seq,
                "package": str(pkg or "").strip(),
                "pack_type": str(r.get("包类型") or "").strip(),
                "instrument_count": str(r.get("包内器械件数") or "").strip(),
                "pricing_rule": str(r.get("收费规则") or "").strip(),
                "note": str(r.get("备注") or "").strip(),
            }
        )
    return hospitals, unified_rules


def parse_lt_general_excel() -> list[dict[str, Any]]:
    if not SPECIAL_XLSX.is_file():
        return []
    df = pd.read_excel(SPECIAL_XLSX, sheet_name=LT_GENERAL_SHEET, header=None)
    rows: list[dict[str, Any]] = []
    for _, r in df.iterrows():
        c0, c1 = r.get(0), r.get(1)
        c5, c6 = r.get(5), r.get(6)
        c7, c8 = r.get(7), r.get(8)
        if isinstance(c0, str) and "包内件数" in c0:
            continue
        if c0 and str(c0) != "nan":
            rows.append({"tier": str(c0).strip(), "price": str(c1 or "").strip(), "section": "tier"})
        if c5 and str(c5) != "nan" and "包内件数" not in str(c5):
            rows.append({"bagWidth": str(c5).strip(), "price": str(c6 or "").strip(), "note": str(c8 or "").strip(), "section": "singleBag"})
    return rows


def rule_desc(r: dict[str, Any]) -> str:
    rt = r.get("ruleType", "")
    kws = r.get("keywords") or []
    kw = "、".join(kws) if kws else "—"
    if rt == "FOLD":
        return f"FOLD 关键词[{kw}] {r.get('threshold')}件→计{r.get('foldRatio')}件"
    if rt == "FIXED_PRICE":
        return f"FIXED {r.get('price')}元 关键词[{kw}]"
    if rt == "PRICE_PER_INSTRUMENT":
        return f"按件{r.get('price')}元 关键词[{kw}] min={r.get('minInstrumentCount', '—')}"
    return f"{rt} {r.get('name', '')}"


def find_rule_by_keyword(rules: list[dict[str, Any]], keyword: str) -> list[dict[str, Any]]:
    out = []
    for r in rules:
        kws = r.get("keywords") or []
        name = str(r.get("name") or "")
        if keyword in kws or keyword in name or any(keyword in k for k in kws):
            out.append(r)
    return out


def compare_fold_rule(customer_rule: dict[str, Any], sys_rules: list[dict[str, Any]]) -> tuple[list[Conflict], list[str]]:
    kw = extract_keyword(customer_rule["package"])
    pricing = customer_rule["pricing_rule"]
    matches = find_rule_by_keyword(sys_rules, kw)
    conflicts: list[Conflict] = []
    matched: list[str] = []
    fold_price = 5.6 if "5.6" in pricing else 5.5
    if not matches:
        conflicts.append(
            Conflict(
                field=f"特殊规则·{kw}",
                customer_value=pricing,
                system_value="无对应 productRule",
                severity="critical",
                impact=f"包名含「{kw}」的折叠/特殊计价未配置",
                suggestion="新增 FOLD 或 FIXED_PRICE 规则",
            )
        )
        return conflicts, matched
    for m in matches:
        matched.append(m.get("name", ""))
        if m.get("ruleType") == "FOLD":
            if m.get("threshold") != 5 or m.get("foldRatio") != 5:
                conflicts.append(
                    Conflict(
                        field=f"折叠阈值·{kw}",
                        customer_value="5件算1件（进位）",
                        system_value=f"threshold={m.get('threshold')} foldRatio={m.get('foldRatio')}",
                        severity="major",
                        impact="小件合并件数不一致",
                        suggestion="对齐 threshold/foldRatio=5",
                    )
                )
            cond = m.get("conditionsJson")
            if cond and "department" in str(cond) and "口腔科" in str(cond):
                conflicts.append(
                    Conflict(
                        field=f"适用范围·{kw}",
                        customer_value="全院/无科室限制",
                        system_value=str(cond),
                        severity="major",
                        impact="非口腔科包可能未触发折叠规则",
                        suggestion="确认是否应去掉科室条件或补充全院规则",
                    )
                )
        elif m.get("ruleType") == "FIXED_PRICE":
            sys_p = float(m.get("price") or 0)
            if abs(sys_p - 8.0) < 0.01 and "5.5+2.5" in pricing.replace(" ", ""):
                matched.append("价格语义一致(8元)")
            else:
                conflicts.append(
                    Conflict(
                        field=f"计价方式·{kw}",
                        customer_value=pricing,
                        system_value=rule_desc(m),
                        severity="major",
                        impact="计价逻辑/单价可能不一致",
                        suggestion="核对 FIXED vs 公式计价",
                    )
                )
    if fold_price == 5.6 and matches and all(m.get("ruleType") == "FOLD" for m in matches):
        conflicts.append(
            Conflict(
                field=f"折叠单价·{kw}",
                customer_value="5.6元/折算件",
                system_value="5.5元/折算件（系统默认）",
                severity="major",
                impact="东北农大根管锉单价偏高0.1元",
                suggestion="新增 price=5.6 的 FOLD 或 FIXED 规则",
            )
        )
    return conflicts, matched


def compare_dressing_rule(customer_rule: dict[str, Any], sys_rules: list[dict[str, Any]], code: str | None) -> tuple[list[Conflict], list[str]]:
    kw = extract_keyword(customer_rule["package"])
    pricing = customer_rule["pricing_rule"]
    conflicts: list[Conflict] = []
    matched: list[str] = []
    if "≥20CM" in pricing or "≥20" in pricing:
        std = "标准模板 dressingPack.cottonPaperPlastic: ≥20cm=4元"
        if not find_rule_by_keyword(sys_rules, kw):
            if code:
                conflicts.append(
                    Conflict(
                        field=f"敷料纸塑·{kw}",
                        customer_value=pricing,
                        system_value=f"无 productRule；{std}",
                        severity="pending",
                        impact="若走标准模板可能等价，需实单验证",
                        suggestion="确认 hybrid/standard 模式下是否自动套用标准敷料价",
                    )
                )
            else:
                conflicts.append(
                    Conflict(
                        field=f"敷料纸塑·{kw}",
                        customer_value=pricing,
                        system_value="医院未接入系统",
                        severity="critical",
                        impact="客户有规则但系统无客户档案",
                        suggestion="先接入客户再配置 productRule 或确认标准价覆盖",
                    )
                )
        else:
            matched.append(f"关键词[{kw}]已有规则")
    if "W60" in pricing or "W50" in pricing:
        std_nw = "标准模板 dressingPack.nonWoven: W60/70=25, W90=30, W120/150=35"
        qx = find_rule_by_keyword(sys_rules, kw) or find_rule_by_keyword(sys_rules, "W60")
        if not qx:
            conflicts.append(
                Conflict(
                    field=f"无纺布敷料·{kw}",
                    customer_value=pricing,
                    system_value=f"无 productRule；{std_nw}",
                    severity="pending" if code == "HRB-WY-EM" else "major",
                    impact="驱血带/棉球/纱布按无纺布规格计价可能走默认价",
                    suggestion="为 HRB-WY-EM 补充无纺布分级 FIXED 规则或确认标准模板覆盖",
                )
            )
        else:
            matched.extend(r.get("name", "") for r in qx)
    return conflicts, matched


def compare_bingcheng(customer_rules: list[dict[str, Any]], sys_rules: list[dict[str, Any]]) -> tuple[list[Conflict], list[str]]:
    conflicts: list[Conflict] = []
    matched: list[str] = []
    per_piece_map = {
        "环钻包": ("冰城环钻包按件5.5", "冰城环钻包无纺布加价3", 3),
        "整形手术包": ("冰城整形手术包按件5.5", "冰城整形手术包无纺布加价3", 3),
        "脂充包": ("冰城脂充包按件5.5", "冰城脂充包无纺布加价5", 5),
    }
    for cr in customer_rules:
        pkg = cr["package"]
        pricing = cr["pricing_rule"]
        if pkg not in per_piece_map:
            continue
        pi_name, fee_name, addon = per_piece_map[pkg]
        pi = next((r for r in sys_rules if r.get("name") == pi_name and r.get("isActive", True)), None)
        fee = next((r for r in sys_rules if r.get("name") == fee_name and r.get("isActive", True)), None)
        if pi and fee:
            matched.extend([pi_name, fee_name])
            if not (pi.get("ruleType") == "PRICE_PER_INSTRUMENT" and float(pi.get("price") or 0) == 5.5):
                conflicts.append(
                    Conflict(
                        field=f"{pkg}按件",
                        customer_value=pricing,
                        system_value=rule_desc(pi),
                        severity="major",
                        impact="按件单价应为5.5",
                        suggestion="激活 PRICE_PER_INSTRUMENT 5.5",
                    )
                )
            if float(fee.get("fee") or 0) != addon:
                conflicts.append(
                    Conflict(
                        field=f"{pkg}无纺布加价",
                        customer_value=f"+{addon}元",
                        system_value=f"EXTRA_FEE {fee.get('fee')}元",
                        severity="major",
                        impact="无纺布附加费不一致",
                        suggestion=f"激活 EXTRA_FEE +{addon}",
                    )
                )
        else:
            fixed_map = {"环钻包": "环钻27.5", "整形手术包": "整形包58", "脂充包": "脂充包54.5"}
            fixed_name = fixed_map.get(pkg)
            fixed = next((r for r in sys_rules if r.get("name") == fixed_name and r.get("isActive", True)), None) if fixed_name else None
            if fixed:
                matched.append(fixed_name)
                conflicts.append(
                    Conflict(
                        field=f"{pkg}计价模型",
                        customer_value=pricing,
                        system_value=f"FIXED {fixed.get('price')}元 ({fixed_name})",
                        severity="critical",
                        impact="v8要求按件×5.5+无纺布附加费，当前为固定打包价",
                        suggestion="停用 FIXED，激活按件+EXTRA_FEE 规则",
                    )
                )
            else:
                conflicts.append(
                    Conflict(
                        field=f"{pkg}",
                        customer_value=pricing,
                        system_value="无 active 规则",
                        severity="critical",
                        impact="冰城特色包未配置",
                        suggestion="新增/激活按件+加价规则",
                    )
                )
    extra_pack = next(
        (r for r in sys_rules if r.get("name") == "冰城环钻包小件包装加价3" and r.get("isActive", True)),
        None,
    )
    if extra_pack:
        conflicts.append(
            Conflict(
                field="环钻包小件包装加价",
                customer_value="件数×5.5+3（仅一项附加费）",
                system_value=f"EXTRA_FEE +{extra_pack.get('fee')}元 ({extra_pack.get('name')})",
                severity="critical",
                impact="多计 3 元/包（如 5 件应为 30.5 而非 33.5）",
                suggestion="停用 冰城环钻包小件包装加价3",
            )
        )
    bad_active = next((r for r in sys_rules if r.get("name") == "≥3件按件5.5元" and r.get("isActive", True)), None)
    if bad_active and not bad_active.get("excludeKeywords"):
        conflicts.append(
            Conflict(
                field="通用≥3件规则",
                customer_value="无（客户未列）",
                system_value="PRICE_PER_INSTRUMENT 5.5元 HT ≥3件 无排除词",
                severity="major",
                impact="可能误伤特色器械包",
                suggestion="为≥3件规则添加 excludeKeywords 或 deactivate",
            )
        )
    return conflicts, matched


def compare_guoyao2(customer_rules: list[dict[str, Any]], sys_rules: list[dict[str, Any]]) -> tuple[list[Conflict], list[str]]:
    conflicts: list[Conflict] = []
    matched: list[str] = []
    for cr in customer_rules:
        kw = extract_keyword(cr["package"]) if "包名称" in cr["package"] else cr["package"]
        if kw == "缝合针":
            sr = find_rule_by_keyword(sys_rules, "缝合针")
            if sr:
                matched.append(sr[0].get("name", ""))
                if sr[0].get("price") == 8.0:
                    matched.append("8元语义一致")
                kws = sr[0].get("keywords") or []
                if set(kws) <= {"缝合针-6", "缝合针-8"}:
                    conflicts.append(
                        Conflict(
                            field="缝合针匹配范围",
                            customer_value="包名称带「缝合针」→ 1×5.5+2.5=8元",
                            system_value=f"仅匹配 {kws}",
                            severity="major",
                            impact="其他规格缝合针可能未按8元计费",
                            suggestion="扩展 keywords 为通用「缝合针」或补充规则",
                        )
                    )
            else:
                conflicts.append(
                    Conflict(
                        field="缝合针",
                        customer_value=cr["pricing_rule"],
                        system_value="无",
                        severity="critical",
                        impact="缝合针特殊价缺失",
                        suggestion="新增 FIXED 8元",
                    )
                )
        elif kw in ("双", "指针", "棉球", "纱布"):
            if not find_rule_by_keyword(sys_rules, kw):
                conflicts.append(
                    Conflict(
                        field=f"特殊规则·{kw}",
                        customer_value=cr["pricing_rule"],
                        system_value="无对应 productRule",
                        severity="critical",
                        impact=f"电机厂{kw}规则未入库",
                        suggestion="按客户 Excel 新增 productRules",
                    )
                )
        elif "5件" in cr["pricing_rule"] or "/5" in cr["pricing_rule"]:
            c, m = compare_fold_rule(cr, sys_rules)
            conflicts.extend(c)
            matched.extend(m)
    return conflicts, matched


def compare_hospital(h: dict[str, Any], manifest: dict[str, Any]) -> HospitalReport:
    name = h["name"]
    code, sys_name, status = HOSPITAL_MAP.get(name, (None, "—", "未映射"))
    sys_rules = system_rules_for(code, manifest)
    report = HospitalReport(name, code, sys_name, status, customer_rules=h["rules"])
    if code is None:
        for cr in h["rules"]:
            report.conflicts.append(
                Conflict(
                    field=f"特殊规则·{cr['package']}",
                    customer_value=cr["pricing_rule"],
                    system_value="系统无此客户",
                    severity="critical",
                    impact="客户已定义规则但未接入 billing-seeds/manifest",
                    suggestion="创建客户档案并导入 productRules",
                )
            )
        return report

    if name == "冰城医美":
        c, m = compare_bingcheng(h["rules"], sys_rules)
    elif name == "电机厂医院":
        c, m = compare_guoyao2(h["rules"], sys_rules)
    else:
        c, m = [], []
        for cr in h["rules"]:
            pkg = cr["package"]
            if "包名称" in pkg:
                kw = extract_keyword(pkg)
            else:
                kw = pkg
            if any(x in cr.get("pack_type", "") for x in ("敷料", "无纺布")) or "≥20CM" in cr["pricing_rule"] or "W" in cr["pricing_rule"]:
                cc, mm = compare_dressing_rule(cr, sys_rules, code)
            elif "/5" in cr["pricing_rule"] or "5件" in cr["pricing_rule"]:
                cc, mm = compare_fold_rule(cr, sys_rules)
            elif "固定收费4元" in cr["pricing_rule"]:
                cc, mm = compare_dressing_rule(cr, sys_rules, code)
            else:
                cc, mm = compare_fold_rule(cr, sys_rules)
            c.extend(cc)
            m.extend(mm)
    report.conflicts = c
    report.matched_rules = sorted(set(m))
    return report


def compare_global_unified(unified_rules: list[dict[str, Any]], manifest: dict[str, Any]) -> list[Conflict]:
    conflicts: list[Conflict] = []
    # 默认 needle 配置
    default_needle = {
        "threshold": 5,
        "foldRatio": 5,
        "keywords": [
            "针", "小件", "探针", "穿刺针", "缝合针", "车针", "拔髓针", "成型片",
            "根管针", "根管锉", "支抗钉", "洁牙机尖", "球钻", "挖勺", "手术针",
        ],
    }
    seen_seq: set[Any] = set()
    for ur in unified_rules:
        if ur["seq"] in seen_seq:
            continue
        if pd.isna(ur["seq"]):
            continue
        seen_seq.add(ur["seq"])
        kw = extract_keyword(ur["package"]) if ur["package"] else ""
        if not kw:
            continue
        if kw in ("克氏针", "银质针", "内热针", "车针", "拔髓针", "扩大针", "卷棉子"):
            if kw not in default_needle["keywords"] and not any(kw in k for k in default_needle["keywords"]):
                conflicts.append(
                    Conflict(
                        field=f"全局·{kw}",
                        customer_value=ur["pricing_rule"],
                        system_value="DefaultPricingTemplate.needle 未单列",
                        severity="pending",
                        impact="可能已被通用「针/小件」关键词覆盖",
                        suggestion="实单验证或补充 needle.keywords",
                    )
                )
        elif kw == "缝合针":
            conflicts.append(
                Conflict(
                    field="全局·缝合针",
                    customer_value=ur["pricing_rule"],
                    system_value="系统默认：needle 5件合并 + 敷料纸塑分级价",
                    severity="pending",
                    impact="与逐院规则可能叠加或冲突",
                    suggestion="确认全局 vs 电机厂8元优先级",
                )
            )
        elif kw == "双":
            conflicts.append(
                Conflict(
                    field="全局·双（低温等离子）",
                    customer_value="固定35元（市二院除外）",
                    system_value="LT nonWoven minSingleCharge=35（DefaultPricingTemplate）",
                    severity="pending",
                    impact="低温双层袋35元可能与全局规则一致；市二院除外需 policies",
                    suggestion="确认 HRB-2ND 排除逻辑是否已配置",
                )
            )
    return conflicts


def compare_standard_pricing(sections: dict[str, list[dict[str, str]]]) -> list[Conflict]:
    conflicts: list[Conflict] = []
    checks = [
        ("高温纸塑 10cm", "2.5元", "DefaultPricingTemplate HT bag 10cm=2.5", "2.5", "minor"),
        ("高温纸塑 15cm", "5.5元", "DefaultPricingTemplate HT bag 15cm=5.5", "5.5", "minor"),
        ("高温纸塑 ≥20cm", "7.5元", "DefaultPricingTemplate HT bag 20cm=7.5", "7.5", "minor"),
        ("高温纸塑 ≥25cm", "10.5元", "DefaultPricingTemplate HT bag 25cm=10.5", "10.5", "minor"),
        ("高温按件", "5.5元", "perPackagePrice", "5.5", "minor"),
        ("≤3件封顶", "16.5元", "minCharge/freeBagFeeThreshold", "16.5", "minor"),
        ("低温 5/10/20件", "88/165/300元", "LT tierPrices", "88/165/300", "minor"),
        ("低温单件", "22元", "remainderPerPiecePrice", "22", "minor"),
        ("低温双层最低", "35元", "LT nonWoven minSingleCharge", "35", "minor"),
        ("无纺布 W60/70", "25元", "dressingPack.nonWoven.below90", "25", "minor"),
        ("无纺布 W90", "30元", "dressingPack.nonWoven.equals90", "30", "minor"),
        ("无纺布 W120/150", "35元", "dressingPack.nonWoven.range12to15", "35", "minor"),
        ("棉球纸塑 ≤15cm", "2.5元", "dressingPack.cottonPaperPlastic.15", "2.5", "minor"),
        ("棉球纸塑 ≥20cm", "4元", "dressingPack.cottonPaperPlastic.20", "4", "minor"),
    ]
    std_text = json.dumps(sections.get("系统标准价", []), ensure_ascii=False)
    for label, cust, sys_desc, sys_val, sev in checks:
        if sys_val.replace("/", "") not in normalize_text(std_text):
            conflicts.append(
                Conflict(
                    field=label,
                    customer_value=cust,
                    system_value=f"系统={sys_desc}({sys_val})",
                    severity=sev,
                    impact="标准价表不一致或 Excel 未明确列出",
                    suggestion="核对 DefaultPricingTemplate 与客户表",
                )
            )
    # 通用特殊收费 1-4 与 needle 对照
    general = sections.get("通用特殊收费", [])
    for item in general:
        txt = item.get("label", "")
        if "5件算一件" in txt or "5件算1" in txt:
            conflicts.append(
                Conflict(
                    field="通用特殊·小件合并",
                    customer_value=txt,
                    system_value="needle threshold=5 foldRatio=5",
                    severity="info",
                    impact="语义一致",
                    suggestion="无需变更",
                )
            )
    return conflicts


def render_report(
    standard_sections: dict[str, Any],
    hospitals: list[dict[str, Any]],
    unified_rules: list[dict[str, Any]],
    hospital_reports: list[HospitalReport],
    global_conflicts: list[Conflict],
    standard_conflicts: list[Conflict],
) -> str:
    manifest = load_manifest()
    total_h = len(hospital_reports)
    with_conflict = [r for r in hospital_reports if r.conflicts]
    no_conflict = [r for r in hospital_reports if not r.conflicts and r.system_code]
    unmapped = [r for r in hospital_reports if r.system_code is None]
    sev_count = {"critical": 0, "major": 0, "minor": 0, "pending": 0, "info": 0}
    for r in hospital_reports:
        for c in r.conflicts:
            sev_count[c.severity] = sev_count.get(c.severity, 0) + 1
    for c in global_conflicts + standard_conflicts:
        sev_count[c.severity] = sev_count.get(c.severity, 0) + 1

    lines: list[str] = []
    lines.append("# 客户收费规则与系统规则对比分析\n")
    lines.append(f"> 生成日期：{date.today().isoformat()}  ")
    lines.append(f"> Manifest：`{manifest.get('manifest_hash', '')[:16]}…`（`测试用例/billing_rules_manifest.json`）\n")

    lines.append("## 一、执行摘要\n")
    lines.append("| 指标 | 数值 |")
    lines.append("|------|------|")
    lines.append(f"| 客户 Excel 涉及医院 | {total_h} 家 |")
    lines.append(f"| 存在冲突/缺口医院 | {len(with_conflict)} 家 |")
    lines.append(f"| 无冲突（已映射且规则一致/待实单） | {len(no_conflict)} 家 |")
    lines.append(f"| 系统未接入医院 | {len(unmapped)} 家 |")
    lines.append(f"| 严重冲突 | {sev_count.get('critical', 0)} 项 |")
    lines.append(f"| 主要冲突 | {sev_count.get('major', 0)} 项 |")
    lines.append(f"| 待确认 | {sev_count.get('pending', 0)} 项 |")
    lines.append("")

    lines.append("### 主要发现\n")
    findings = [
        "冰城医美：客户要求「件数×5.5+无纺布附加费」，系统为固定打包价（27.5~58元），计价模型根本冲突。",
        "电机厂（国药二院）：客户列 5 类规则，系统仅入库缝合针 8 元且 keyword 过窄；双/指针/棉球/纱布缺失。",
        "方南南、美意、易丽、佳医 4 家已完成客户建档，特色规则待后续录入。",
        "东北农大：客户要求根管锉按 5.6 元/折算件，系统 FOLD 仍走默认 5.5 元。",
        "统一/标准section 与 DefaultPricingTemplate 大体一致；低温双层35元、needle 5合1 等待实单/policy 确认。",
    ]
    for i, f in enumerate(findings, 1):
        lines.append(f"{i}. {f}")
    lines.append("")

    lines.append("## 二、对比方法与数据来源\n")
    lines.append("| 来源 | 路径/说明 |")
    lines.append("|------|-----------|")
    lines.append(f"| 客户标准收费 | `{STANDARD_XLSX}` · Sheet1 |")
    lines.append(f"| 客户特殊收费 | `{SPECIAL_XLSX}` · 各医院特殊收费 / {UNIFIED_SHEET} / {LT_GENERAL_SHEET} |")
    lines.append("| 系统 productRules | `测试用例/billing_rules_manifest.json`（billing-seeds 合并） |")
    lines.append("| 系统标准价 | `DefaultPricingTemplate.java` v2.0 |")
    lines.append("| 补充硬编码 | `HardcodedRulesMigrationRunner.java`（HTFH/NEAU 等） |")
    lines.append("| 院名别名 | `scripts/generate_hospital_requirement_docs.py`、`phase7-batch-e.json` |")
    lines.append("")

    lines.append("## 三、标准收费与统一规则\n")
    lines.append("### 3.1 系统标准价（客户 Sheet1 左栏）\n")
    lines.append("| 类别 | 客户条目数 | 与系统 DefaultPricingTemplate |")
    lines.append("|------|------------|-------------------------------|")
    lines.append(f"| 系统标准价 | {len(standard_sections.get('系统标准价', []))} | 逐项对照见下 |")
    lines.append(f"| 外来器械（市二院） | {len(standard_sections.get('外来器械收费', []))} | 系统无全局模板，需 HRB-2ND policies |")
    lines.append(f"| 通用特殊收费 | {len(standard_sections.get('通用特殊收费', []))} | 与 needle/LT minSingleCharge 对照 |")
    lines.append("")
    if standard_conflicts:
        lines.append("**标准价差异/备注：**\n")
        lines.append("| 字段 | 客户值 | 系统值 | 级别 | 建议 |")
        lines.append("|------|--------|--------|------|------|")
        for c in standard_conflicts[:20]:
            lines.append(f"| {c.field} | {c.customer_value} | {c.system_value} | {SEVERITY[c.severity]} | {c.suggestion} |")
        lines.append("")

    lines.append("### 3.2 通用特殊收费（客户「通用特殊收费」sheet）\n")
    lines.append("| 序号 | 包/关键词 | 包类型 | 件数条件 | 客户收费规则 | 备注 |")
    lines.append("|------|-----------|--------|----------|--------------|------|")
    for ur in unified_rules:
        if ur.get("package"):
            lines.append(
                f"| {ur.get('seq', '')} | {ur['package']} | {ur['pack_type']} | {ur['instrument_count']} | {ur['pricing_rule']} | {ur.get('note') or ''} |"
            )
    lines.append("")
    if global_conflicts:
        lines.append("**与系统全局配置差异：**\n")
        lines.append("| 字段 | 客户值 | 系统值 | 级别 | 影响 | 建议 |")
        lines.append("|------|--------|--------|------|------|------|")
        for c in global_conflicts:
            lines.append(f"| {c.field} | {c.customer_value} | {c.system_value} | {SEVERITY[c.severity]} | {c.impact} | {c.suggestion} |")
        lines.append("")

    lines.append("## 四、逐院明细\n")
    for r in hospital_reports:
        lines.append(f"### {r.customer_name}")
        lines.append(f"- **系统映射**：`{r.system_code or '—'}` · {r.system_name} · {r.match_status}")
        lines.append(f"- **客户规则数**：{len(r.customer_rules)} · **系统 active productRules**：{len(system_rules_for(r.system_code, manifest))}")
        if r.matched_rules:
            lines.append(f"- **已匹配规则**：{', '.join(r.matched_rules)}")
        lines.append("")
        lines.append("**客户规则：**\n")
        lines.append("| 项目 | 包/关键词 | 类型 | 收费规则 | 备注 |")
        lines.append("|------|-----------|------|----------|------|")
        for cr in r.customer_rules:
            if cr.get("package"):
                lines.append(f"| {cr.get('seq', '')} | {cr['package']} | {cr.get('pack_type', '')} | {cr['pricing_rule']} | {cr.get('note', '')} |")
        lines.append("")
        if r.conflicts:
            lines.append("**冲突项：**\n")
            lines.append("| 字段 | 客户值 | 系统值 | 级别 | 影响 | 建议 |")
            lines.append("|------|--------|--------|------|------|------|")
            for c in r.conflicts:
                lines.append(
                    f"| {c.field} | {c.customer_value} | {c.system_value} | {SEVERITY[c.severity]} | {c.impact} | {c.suggestion} |"
                )
        else:
            lines.append("*未发现明确冲突（或规则已被标准模板覆盖，建议实单抽验）。*\n")
        lines.append("")

    lines.append("## 五、无冲突院列表\n")
    if no_conflict:
        for r in no_conflict:
            lines.append(f"- {r.customer_name}（`{r.system_code}`）")
    else:
        lines.append("- （无）")
    lines.append("")

    lines.append("## 六、客户有但系统未覆盖的院\n")
    for r in unmapped:
        lines.append(f"- **{r.customer_name}**：{len(r.customer_rules)} 条规则，账单样本在 `测试用例/待匹配/`")
    lines.append("")

    lines.append("## 附录 A：未完全匹配院名\n")
    lines.append("| 客户简称 | 建议系统 code | 状态 |")
    lines.append("|----------|---------------|------|")
    for name, (code, sys_name, st) in HOSPITAL_MAP.items():
        lines.append(f"| {name} | {code or '—'} | {st} |")
    lines.append("")

    lines.append("## 附录 B：字段映射表\n")
    lines.append("| 客户 Excel 列 | 含义 | 系统字段 |")
    lines.append("|---------------|------|----------|")
    mapping = [
        ("医院名称", "客户简称", "customers.name / aliases"),
        ("包名称（或包名中特殊信息）", "匹配关键词或包名", "productRules.keywords / name"),
        ("包类型", "器械包/敷料/额外包", "temperature / materials / billingMode"),
        ("包内器械件数", "折叠阈值条件", "threshold / conditionsJson"),
        ("收费规则", "业务口径描述", "ruleType + price + foldRatio + policies"),
        ("备注", "变更说明", "seed notes / issue tracker"),
    ]
    for a, b, c in mapping:
        lines.append(f"| {a} | {b} | {c} |")
    lines.append("")

    return "\n".join(lines)


def main() -> None:
    standard_sections = parse_standard_excel()
    hospitals, unified_rules = parse_special_excel()
    lt_general = parse_lt_general_excel()
    manifest = load_manifest()
    hospital_reports = [compare_hospital(h, manifest) for h in hospitals]
    global_conflicts = compare_global_unified(unified_rules, manifest)
    standard_conflicts = compare_standard_pricing(standard_sections)
    md = render_report(
        standard_sections, hospitals, unified_rules, hospital_reports, global_conflicts, standard_conflicts
    )
    OUTPUT.write_text(md, encoding="utf-8")
    print(f"Wrote {OUTPUT}")
    with_conflict = sum(1 for r in hospital_reports if r.conflicts)
    crit = sum(1 for r in hospital_reports for c in r.conflicts if c.severity == "critical")
    print(f"Hospitals: {len(hospital_reports)}, with conflicts: {with_conflict}, critical items: {crit}")


if __name__ == "__main__":
    main()
