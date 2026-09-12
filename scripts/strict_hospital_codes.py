#!/usr/bin/env python3
"""29 家特殊计价医院权威清单（路径 A / manifest / Java STRICT_KEEP_CODES 须一致）。"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class StrictHospital:
    code: str
    label: str
    folder: str | None
    testable: bool = True
    skip_reason: str = ""


# 与 BillingSeedMigrationRunner.STRICT_KEEP_CODES / billing_rules_manifest.STRICT_KEEP_CODES 逐字一致
STRICT_KEEP_CODES: list[str] = [
    "BINGCHENG-YM",
    "GUOYAO-2",
    "FNN-YY",
    "JIAYI-YL",
    "NEAU-YY",
    "HRB-WY",
    "HRB-SD-MB",
    "HRB-HTFH",
    "HRB-WY-EM",
    "JIUZHOU-FK",
    "BOSHANG-YY",
    "HAIYUAN-SB",
    "HLJ-FY-RK",
    "ZUYAN-NG",
    "SHKF-YY",
    "DL-FUCHAN",
    "CHUNYU-YL",
    "HL-ZGH",
    "JZSW-BIO",
    "SUOFEI-YL",
    "HLJ-JYGLJ-YY",
    "HULAN-TCM",
    "PFQ-RM",
    "HULAN-RM",
    "XINFA-HSZ",
    "YUANDONG-XN",
    "ZUYAN-SF",
    "AOLAN-YY",
    "HRB-XK-YY",
    "SENHAI-YY",
]

STRICT_HOSPITALS: list[StrictHospital] = [
    StrictHospital("BINGCHENG-YM", "冰城医美", "哈尔滨冰城医疗美容医院", True),
    StrictHospital("GUOYAO-2", "电机厂", "国药总医院第二院区", True),
    StrictHospital("FNN-YY", "方南南", "方南南医院", True),
    StrictHospital("JIAYI-YL", "佳医医疗", "佳医医疗", True),
    StrictHospital("NEAU-YY", "东北农大", "东北农业大学", True),
    StrictHospital("HRB-WY", "市五院主院区", "哈尔滨市第五医院", False, "ground truth 陈旧待更新"),
    StrictHospital("HRB-SD-MB", "松电慢病", "松电慢病", True),
    StrictHospital("HRB-HTFH", "航天风华", "航天风华", True),
    StrictHospital("HRB-WY-EM", "市五院二门诊", "哈尔滨市第五医院（二门诊）", True),
    StrictHospital("JIUZHOU-FK", "九州", "黑龙江九洲妇科医院", True),
    StrictHospital("BOSHANG-YY", "博尚", "博尚医院", True),
    StrictHospital("HAIYUAN-SB", "海员松北", "黑龙江省海员总医院（松北）", True),
    StrictHospital("HLJ-FY-RK", "省妇幼人口", "黑龙江省妇幼保健院（人口）", True),
    StrictHospital("ZUYAN-NG", "祖研南岗", "祖研-黑龙江省中医医院（南岗院区）", True),
    StrictHospital("SHKF-YY", "社会康复", "黑龙江省社会康复医院", True),
    StrictHospital("DL-FUCHAN", "道里妇幼", "道里区妇幼保健院", True),
    StrictHospital("CHUNYU-YL", "春语医美", "春语医美", True),
    StrictHospital("HL-ZGH", "总工会", "总工会", True),
    StrictHospital("JZSW-BIO", "基准生物", "基准生物", False, "无原始表格"),
    StrictHospital("SUOFEI-YL", "索菲医美", "索菲医美", True),
    StrictHospital("HLJ-JYGLJ-YY", "省监狱管理局", "省监狱管理局医院", True),
    StrictHospital("HULAN-TCM", "呼兰中医", "呼兰中医院", False, "ground truth 陈旧待更新"),
    StrictHospital("PFQ-RM", "平房区人民", "哈尔滨市平房区人民医院", True),
    StrictHospital("HULAN-RM", "呼兰区人民", "哈尔滨市呼兰区第一人民医院", True),
    StrictHospital("XINFA-HSZ", "新发红十字", "新发红十字", True),
    StrictHospital("YUANDONG-XN", "远东心脑血管", "远东心脑血管", True),
    StrictHospital("ZUYAN-SF", "祖研三辅", "祖研-黑龙江省中医医院（三辅院区）", True),
    StrictHospital("AOLAN-YY", "奥兰医院", "奥兰医院", True),
    StrictHospital("HRB-XK-YY", "胸科医院", "哈尔滨胸科医院", True),
    StrictHospital("SENHAI-YY", "森海医院", "森海医院", True),
]

STRICT_BY_CODE = {h.code: h for h in STRICT_HOSPITALS}
STRICT_BY_FOLDER = {h.folder: h for h in STRICT_HOSPITALS if h.folder}
