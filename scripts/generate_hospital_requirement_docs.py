#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate per-hospital requirement registration documents from template + rules."""

import os
import re
from pathlib import Path
from datetime import date

ROOT = Path(__file__).resolve().parent.parent
TEMPLATE_PATH = ROOT / "docs" / "特色账单系统-医院功能需求通用登记表（模板）.md"
OUTPUT_DIR = ROOT / "docs" / "逐院需求登记表"
REF_BASE = ROOT / "铂康" / "参考文件（按照医院）"

# 42 hospitals from template section 1.5
HOSPITALS = [
    "三精肾病医院",
    "南岗区先锋路社区卫生服务中心",
    "南岗区妇产医院",
    "呼兰中医院",
    "呼兰区红十字医院",
    "哈尔滨工业大学医院",
    "哈尔滨工程大学医院",
    "哈尔滨仁胜医院",
    "哈尔滨冰城医疗美容医院",
    "哈尔滨华夏眼科医院",
    "哈尔滨市南岗区人民医院（九院）",
    "哈尔滨市呼兰区第一人民医院",
    "哈尔滨市第二医院",
    "哈尔滨市第五医院",
    "哈尔滨市第五医院（二门诊）",
    "哈尔滨市红十字妇产医院",
    "哈尔滨市骨伤科医院",
    "国药总医院主院区",
    "国药总医院第三院区",
    "国药总医院第二院区",
    "太平人民医院",
    "奥兰医院",
    "悦美芳华医疗门诊医院",
    "新发红十字医院",
    "武警黑龙江省总队医院",
    "祖研-黑龙江省中医医院（三辅院区）",
    "祖研-黑龙江省中医医院（南岗院区）",
    "祖研-黑龙江省中医医院（香安院区）",
    "道外区人民医院",
    "香坊中医院",
    "黑龙江东大肛肠",
    "黑龙江中医药大学附属第一医院",
    "黑龙江中医药大学附属第二医院（南岗）",
    "黑龙江中医药大学附属第二医院（哈南分院）",
    "黑龙江九洲妇科医院",
    "黑龙江省中医药大学附属第三医院（电力）",
    "黑龙江省医院（南岗院区）",
    "黑龙江省医院（香坊院区）",
    "黑龙江省社会康复医院",
    "黑龙江省第二医院（南岗院区）",
    "黑龙江省第二医院（松北院区）",
    "黑龙江省远东心脑血管医院",
    "黑龙江维多利亚妇产医院",
]

# Known customer codes from codebase
CUSTOMER_CODES = {
    "黑龙江省第二医院（南岗院区）": "ERYY-NG",
    "黑龙江省第二医院（松北院区）": "ERYY-SB",
    "哈尔滨市呼兰区第一人民医院": "HULAN-RM",
    "哈尔滨市第五医院": "HRB-WY",
    "哈尔滨工业大学医院": "HRB-HIT",
    "哈尔滨工程大学医院": "HRB-HEU",
}

# System module implementation status (global, from codebase)
MODULE_SYSTEM_STATUS = {
    "M1": "部分实现",
    "M2": "部分实现",
    "M3": "部分实现",
    "M4": "部分实现",
    "M5": "部分实现",
    "M6": "部分实现",
    "M7": "未实现",
    "M8": "部分实现",
    "M9": "未实现",
    "M10": "未实现",
    "M11": "未实现",
    "M12": "未实现",
    "M13": "未实现",
    "M14": "未实现",
    "INT": "部分实现",
    "CFG": "部分实现",
}

# FR row -> (module, default enabled when hospital uses module)
FR_ROWS = [
    ("FR-M1-01", "M1", "启用特色账单开关"),
    ("FR-M1-02", "M1", "客户规范名与编码"),
    ("FR-M1-03", "M1", "客户别名（contains/exact）"),
    ("FR-M1-04", "M1", "计价模式 standard/special_only/hybrid"),
    ("FR-M1-05", "M1", "路径覆盖 pathOverride"),
    ("FR-M1-06", "M1", "导出名称替换"),
    ("FR-M1-07", "M1", "多院区独立建档"),
    ("FR-M1-08", "M1", "结款函合并客户组"),
    ("FR-M1-09", "M1", "客户备注与业务说明"),
    ("FR-M2-01", "M2", "整单折扣（账单明细）"),
    ("FR-M2-02", "M2", "分温折扣（高温/低温）"),
    ("FR-M2-03", "M2", "结款函独立折扣"),
    ("FR-M2-04", "M2", "导出阶段折扣"),
    ("FR-M2-05", "M2", "按把数/件数分段折扣"),
    ("FR-M2-06", "M2", "固定小数位数"),
    ("FR-M2-07", "M2", "特定原价覆盖价"),
    ("FR-M2-08", "M2", "skipDiscount 跳过折扣"),
    ("FR-M2-09", "M2", "折扣优先级与互斥"),
    ("FR-M2-10", "M2", "品类/科室范围折扣"),
    ("FR-M3-01", "M3", "关键词匹配（包名）"),
    ("FR-M3-02", "M3", "排除关键词"),
    ("FR-M3-03", "M3", "产品 ID 精确匹配"),
    ("FR-M3-04", "M3", "包装材料条件"),
    ("FR-M3-05", "M3", "灭菌温别条件"),
    ("FR-M3-06", "M3", "件数/把数区间"),
    ("FR-M3-07", "M3", "袋尺寸条件"),
    ("FR-M3-08", "M3", "科室条件"),
    ("FR-M3-09", "M3", "原价匹配条件"),
    ("FR-M3-10", "M3", "FIXED_PRICE 固定价"),
    ("FR-M3-11", "M3", "PRICE_PER_INSTRUMENT 按件计价"),
    ("FR-M3-12", "M3", "MULTIPLIER 倍率"),
    ("FR-M3-13", "M3", "FOLD N件算1件"),
    ("FR-M3-14", "M3", "EXTRA_FEE / ADD_FEE 加收"),
    ("FR-M3-15", "M3", "行拆分 SPLIT_ROW"),
    ("FR-M3-16", "M3", "MATERIAL_BRANCH 材料分支价"),
    ("FR-M3-17", "M3", "ZERO_PRICE_OVERRIDE 0元覆盖"),
    ("FR-M3-18", "M3", "规则优先级排序"),
    ("FR-M3-19", "M3", "skipPackaging 跳过包装费"),
    ("FR-M3-20", "M3", "小件优先再打折"),
    ("FR-M3-21", "M3", "导出列删增（I/J/K/M）"),
    ("FR-M3-22", "M3", "导出插列（材料/件数/单价）"),
    ("FR-M3-23", "M3", "保留包装/把数列（例外）"),
    ("FR-M3-24", "M3", "结构化产品匹配"),
    ("FR-M3-25", "M3", "通用规则库继承"),
    ("FR-M3-26", "M3", "规则试算/预览"),
    ("FR-M3-27", "M3", "规则批量导入导出"),
    ("FR-M4-01", "M4", "多报价配置 acceptedPrices"),
    ("FR-M4-02", "M4", "matchMode=any_price"),
    ("FR-M4-03", "M4", "对账多报价命中展示"),
    ("FR-M4-04", "M4", "未命中列出全部报价"),
    ("FR-M4-05", "M4", "规则命中追溯"),
    ("FR-M4-06", "M4", "期望价/实际价/差额"),
    ("FR-M4-07", "M4", "折扣链展示"),
    ("FR-M4-08", "M4", "未匹配产品提示"),
    ("FR-M5-01", "M5", "月度最低消费 minCharge"),
    ("FR-M5-02", "M5", "月度封顶 maxCap"),
    ("FR-M5-03", "M5", "低消/封顶展示"),
    ("FR-M5-04", "M5", "品类范围低消"),
    ("FR-M5-05", "M5", "独立收费项不计入低消基数"),
    ("FR-M5-06", "M5", "对账 Job 自动应用低消"),
    ("FR-M6-01", "M6", "客户级物流单价"),
    ("FR-M6-02", "M6", "按发货日期计趟次"),
    ("FR-M6-03", "M6", "不收物流费"),
    ("FR-M6-04", "M6", "物流独立导入"),
    ("FR-M6-05", "M6", "按科室消毒费比例分摊"),
    ("FR-M6-06", "M6", "跨院区/跨客户同日合并"),
    ("FR-M6-07", "M6", "按星期/日期计费"),
    ("FR-M6-08", "M6", "供应中心销器械免物流"),
    ("FR-M6-09", "M6", "物流费写入 Job"),
    ("FR-M7-01", "M7", "物流卡账户维护"),
    ("FR-M7-02", "M7", "月度物流费卡内扣减"),
    ("FR-M7-03", "M7", "账单/结款函展示卡余额"),
    ("FR-M7-04", "M7", "多院区独立物流卡"),
    ("FR-M7-05", "M7", "充值/调整记录"),
    ("FR-M8-01", "M8", "已改账单 Excel 导出"),
    ("FR-M8-02", "M8", "结款函 Excel 导出"),
    ("FR-M8-03", "M8", "结款函 HTML/打印"),
    ("FR-M8-04", "M8", "分科室汇总表"),
    ("FR-M8-05", "M8", "价格汇总表"),
    ("FR-M8-06", "M8", "包数据汇总（含/不含金额）"),
    ("FR-M8-07", "M8", "总汇总表（多院区）"),
    ("FR-M8-08", "M8", "多 Sheet 导出（低温敷料分科室）"),
    ("FR-M8-09", "M8", "费用调整 Sheet"),
    ("FR-M8-10", "M8", "结款函独立收费行"),
    ("FR-M8-11", "M8", "低消/补差行"),
    ("FR-M8-12", "M8", "汽轮机核算数量算法"),
    ("FR-M8-13", "M8", "导出名称/产品名替换"),
    ("FR-M8-14", "M8", "导出日志"),
    ("FR-M8-15", "M8", "自定义导出模板绑定"),
    ("FR-M8-16", "M8", "分温结款函分栏"),
    ("FR-M8-17", "M8", "设备抵扣行"),
    ("FR-M9-01", "M9", "行级加急标记"),
    ("FR-M9-02", "M9", "通用加急灭菌费 125%"),
    ("FR-M9-03", "M9", "医院专属加急减免"),
    ("FR-M9-04", "M9", "加急物流费"),
    ("FR-M9-05", "M9", "结款函加急独立行"),
    ("FR-M9-06", "M9", "加急来源（车间/MES）"),
    ("FR-M10-01", "M10", "费用调整（不移除原行）"),
    ("FR-M10-02", "M10", "调整关键词配置"),
    ("FR-M10-03", "M10", "按花名册医生分配"),
    ("FR-M10-04", "M10", "低温拆分至科室 Sheet"),
    ("FR-M10-05", "M10", "供应室借调分摊"),
    ("FR-M10-06", "M10", "科室送手术室分配"),
    ("FR-M10-07", "M10", "两手术室分科室汇总"),
    ("FR-M10-08", "M10", "总汇总（含二门诊/外来）"),
    ("FR-M10-09", "M10", "市二院科室合并/特殊收费"),
    ("FR-M10-10", "M10", "多院区账单分开、结款合并"),
    ("FR-M11-01", "M11", "花名册 CRUD"),
    ("FR-M11-02", "M11", "花名册 Excel 导入"),
    ("FR-M11-03", "M11", "对账时姓名命中提示"),
    ("FR-M11-04", "M11", "人工 override 归属"),
    ("FR-M11-05", "M11", "未匹配标记待处理"),
    ("FR-M12-01", "M12", "外来器械独立维护"),
    ("FR-M12-02", "M12", "独立导入通道"),
    ("FR-M12-03", "M12", "包类别号主键计价"),
    ("FR-M12-04", "M12", "计入总结款函"),
    ("FR-M12-05", "M12", "总汇总外来器械勾稽"),
    ("FR-M12-06", "M12", "维护界面与导入模板"),
    ("FR-M13-01", "M13", "器械把数表导出"),
    ("FR-M13-02", "M13", "灭菌包装表导出"),
    ("FR-M13-03", "M13", "三件以上特殊行反填"),
    ("FR-M13-04", "M13", "器械量表（科室月汇总）"),
    ("FR-M13-05", "M13", "按科室汇总包/把数"),
    ("FR-M14-01", "M14", "按发货日期拆分日账单"),
    ("FR-M14-02", "M14", "日结独立计价规则"),
    ("FR-M14-03", "M14", "多文件导入 / 单月自动拆分"),
    ("FR-M14-04", "M14", "日结与结款函勾稽"),
]

MAT_KEYWORDS = {
    "MAT-01": ["未改", "原始", "系统导出"],
    "MAT-02": ["已改", "特色账单", "账单"],
    "MAT-03": ["结款", "结款函"],
    "MAT-04": ["分科室", "科室汇总"],
    "MAT-05": ["价格汇总"],
    "MAT-06": ["包数据", "包数汇总"],
    "MAT-07": ["总汇总"],
    "MAT-08": ["把数", "器械量"],
    "MAT-09": ["灭菌包装", "包装表"],
    "MAT-10": ["外来器械"],
    "MAT-11": ["费用调整", "调整表"],
    "MAT-12": ["花名册", "医生"],
    "MAT-13": ["借调", "科室使用", "供应室"],
    "MAT-14": ["物流", "路线", "次数"],
    "MAT-15": ["价格表", "报价"],
    "MAT-16": ["日结"],
    "MAT-17": ["规则", "合同", "说明"],
}

MAT_NAMES = {
    "MAT-01": "未改账单",
    "MAT-02": "已改账单",
    "MAT-03": "结款函",
    "MAT-04": "分科室汇总表",
    "MAT-05": "价格汇总表",
    "MAT-06": "包数据汇总",
    "MAT-07": "总汇总表",
    "MAT-08": "器械把数表/器械量表",
    "MAT-09": "灭菌包装表",
    "MAT-10": "外来器械表",
    "MAT-11": "费用调整表",
    "MAT-12": "花名册",
    "MAT-13": "科室使用/供应室借调表",
    "MAT-14": "物流路线/次数表",
    "MAT-15": "价格表/报价单",
    "MAT-16": "日结单",
    "MAT-17": "规则说明/合同",
}


def build_hospital_data():
    """Structured rules per hospital from 特色账单规则.txt + FRD."""
    data = {h: {
        "aliases": [],
        "billing_enabled": "待确认",
        "pricing_mode": "待确认",
        "stage": "待确认",
        "complexity": "待确认",
        "notes": [],
        "modules": set(),
        "fr_config": {},
        "special_rules": [],
        "mat_hints": set(),
    } for h in HOSPITALS}

    def add(h, **kwargs):
        d = data[h]
        for k, v in kwargs.items():
            if k == "modules":
                d["modules"].update(v)
            elif k == "mat_hints":
                d["mat_hints"].update(v)
            elif k == "aliases":
                d["aliases"].extend(v)
            elif k == "notes":
                d["notes"].append(v)
            elif k == "special_rules":
                d["special_rules"].append(v)
            elif k.startswith("fr_"):
                d["fr_config"][k[3:]] = v
            else:
                d[k] = v

    def fr(h, fr_id, enabled, config="", mat="", remark=""):
        data[h]["fr_config"][fr_id] = {
            "enabled": enabled,
            "config": config,
            "mat": mat,
            "remark": remark,
        }

    # ---- A 省二院 ----
    for h in ["黑龙江省第二医院（南岗院区）", "黑龙江省第二医院（松北院区）"]:
        add(h, billing_enabled="是", pricing_mode="hybrid", stage="L1", complexity="高",
            modules={"M1", "M2", "M3", "M4", "M6", "M8", "INT", "CFG"},
            mat_hints={"MAT-01", "MAT-02", "MAT-03", "MAT-15"})
        add(h, special_rules="折扣：7折（账单明细）")
        add(h, special_rules="物流费用：80.5元/次（各院区独立配置）")
        add(h, special_rules="「xx钉」（排除「空心钉」）：多报价 200元 / 50元")
        add(h, special_rules="软镜类：300元")
        add(h, special_rules="3.6空心钉：19元；7.3空心钉：19元")
        add(h, special_rules="泌尿显微镜头：300元")
        add(h, special_rules="小腔包：多报价 71元 / 76.5元")
        add(h, special_rules="辅料包（手术衣）：无纺布38元 / 纸塑袋40元")
        add(h, special_rules="3.6空心钉工具包：多报价 293.5元 / 271.5元")
        fr(h, "FR-M1-01", "是", "启用特色账单")
        fr(h, "FR-M1-07", "是", "南岗/松北独立客户建档")
        fr(h, "FR-M2-01", "是", "整单7折，作用于账单明细", "MAT-02")
        fr(h, "FR-M3-01", "是", "关键词匹配包名", "MAT-15")
        fr(h, "FR-M3-02", "是", "「xx钉」排除「空心钉」", "MAT-15")
        fr(h, "FR-M3-04", "是", "手术衣按无纺布/纸塑袋分支", "MAT-02")
        fr(h, "FR-M3-10", "是", "软镜/空心钉/泌尿显微镜头/小腔包等固定价", "MAT-15")
        fr(h, "FR-M3-16", "是", "手术衣材料分支价", "MAT-02")
        fr(h, "FR-M4-01", "是", "xx钉/小腔包/空心钉工具包多报价", "MAT-15")
        fr(h, "FR-M4-02", "是", "matchMode=any_price", "MAT-01")
        fr(h, "FR-M6-01", "是", "物流80.5元/次", "MAT-03")

    # B 呼兰一院
    h = "哈尔滨市呼兰区第一人民医院"
    add(h, billing_enabled="是", pricing_mode="standard", stage="L1", complexity="低",
        modules={"M1", "M2", "M8", "INT", "CFG"}, mat_hints={"MAT-01", "MAT-02", "MAT-03"})
    add(h, special_rules="折扣：7折（账单明细）")
    fr(h, "FR-M1-01", "是", "启用特色账单")
    fr(h, "FR-M2-01", "是", "整单7折，账单明细", "MAT-02")

    # C 红十字妇产
    h = "哈尔滨市红十字妇产医院"
    add(h, billing_enabled="是", pricing_mode="special_only", stage="L1", complexity="中",
        modules={"M1", "M3", "M8", "INT", "CFG"},
        mat_hints={"MAT-01", "MAT-02", "MAT-03", "MAT-15"})
    add(h, special_rules="低温器械：包内数量=1，统一22元（不论包装材料）")
    add(h, special_rules="湿化瓶：2件22元")
    add(h, special_rules="纤维喉镜/气管镜/软管：300元/件")
    add(h, special_rules="T型管：25元")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M3-05", "是", "低温器械条件", "MAT-02")
    fr(h, "FR-M3-06", "是", "包内数量=1", "MAT-02")
    fr(h, "FR-M3-10", "是", "湿化瓶/T型管/软镜类固定价", "MAT-02")
    fr(h, "FR-M3-11", "是", "纤维喉镜等300元/件", "MAT-02")

    # D 中医三院（非电力）- only in txt, map note to 电力 variant separately
    # 电力 branch
    h = "黑龙江省中医药大学附属第三医院（电力）"
    add(h, billing_enabled="是", pricing_mode="hybrid", stage="L4", complexity="高",
        modules={"M1", "M2", "M3", "M8", "M13", "INT", "CFG"},
        mat_hints={"MAT-02", "MAT-03", "MAT-08", "MAT-09", "MAT-15"})
    add(h, special_rules="等离子镜：36元（低温）")
    add(h, special_rules="小件盒：25元（高温）")
    add(h, special_rules="灭菌费用包装表体现7折（账单不体现、结款函体现）")
    add(h, special_rules="三件以上（含三件）器械：存在小件计费，先处理小件再打折/逆向推算")
    add(h, special_rules="需额外创建器械把数表（保留器械数栏，与账单器械数一致）")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M2-03", "是", "结款函7折，账单原价", "MAT-03")
    fr(h, "FR-M3-05", "是", "等离子镜低温36元", "MAT-15")
    fr(h, "FR-M3-10", "是", "小件盒25元", "MAT-15")
    fr(h, "FR-M3-20", "是", "小件优先再打折/逆向推算", "MAT-09")
    fr(h, "FR-M13-01", "是", "器械把数表导出", "MAT-08")
    fr(h, "FR-M13-02", "是", "灭菌包装表7折后单价", "MAT-09")
    fr(h, "FR-M13-03", "是", "三件以上特殊行反填", "MAT-09")

    # E 呼兰中医
    h = "呼兰中医院"
    add(h, billing_enabled="是", pricing_mode="hybrid", stage="L2", complexity="中",
        modules={"M1", "M3", "M5", "M6", "M8", "INT", "CFG"},
        mat_hints={"MAT-03", "MAT-04", "MAT-15"})
    add(h, special_rules="外科包：249.5元/个")
    add(h, special_rules="阑尾包：288元/个")
    add(h, special_rules="胸腔止血钳：16.5元/个")
    add(h, special_rules="物流：185元/次")
    add(h, special_rules="低消：10000元")
    add(h, special_rules="手术室（备包）科室：外科包/阑尾包单独列于结款函（不计入灭菌费合计）")
    add(h, notes="源材料标注暂时缺少文件")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M3-10", "是", "外科包249.5/阑尾包288/胸腔止血钳16.5", "MAT-15")
    fr(h, "FR-M5-01", "是", "月度低消10000元", "MAT-03")
    fr(h, "FR-M5-05", "是", "备包科室独立收费不计入低消基数", "MAT-03")
    fr(h, "FR-M6-01", "是", "物流185元/次", "MAT-03")
    fr(h, "FR-M8-10", "是", "结款函外科包/阑尾包独立行", "MAT-03")

    # F 维多利亚
    h = "黑龙江维多利亚妇产医院"
    add(h, billing_enabled="是", pricing_mode="standard", stage="L4", complexity="中",
        modules={"M1", "M2", "M5", "M8", "INT", "CFG"},
        mat_hints={"MAT-02", "MAT-03"})
    add(h, special_rules="账单正常计费；结款函区分高温/低温收费（后续导出升级，本阶段暂不考虑分栏）")
    add(h, special_rules="高温5折、低温7折")
    add(h, special_rules="月合计≤8000收8000（低消/封顶）")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M2-02", "是", "高温5折/低温7折", "MAT-03")
    fr(h, "FR-M5-01", "是", "低消8000", "MAT-03")
    fr(h, "FR-M5-02", "是", "封顶8000", "MAT-03")
    fr(h, "FR-M8-16", "是", "分温结款函（后续阶段）", "MAT-03", "O3范围外")

    # G 九州妇科
    h = "黑龙江九洲妇科医院"
    add(h, billing_enabled="是", pricing_mode="standard", stage="L4", complexity="中",
        modules={"M1", "M2", "M3", "M8", "INT", "CFG"},
        mat_hints={"MAT-02", "MAT-03", "MAT-15"})
    add(h, special_rules="账单正常计费；结款函区分高温/低温（后续导出升级）")
    add(h, special_rules="高温5折、低温7折")
    add(h, special_rules="方盘：5.5元")
    add(h, special_rules="物流减免4次/月、低消3000：当前版本不考虑")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M2-02", "是", "高温5折/低温7折", "MAT-03")
    fr(h, "FR-M3-10", "是", "方盘5.5元", "MAT-15")

    # H 工大医院
    h = "哈尔滨工业大学医院"
    add(h, billing_enabled="是", pricing_mode="hybrid", stage="L2", complexity="中",
        modules={"M1", "M2", "M3", "M8", "INT", "CFG"},
        mat_hints={"MAT-01", "MAT-02", "MAT-03"})
    add(h, aliases=["哈尔滨工业大学医院"])
    add(h, special_rules="口腔类所有「针类」：5.5元")
    add(h, special_rules="洁牙尖、成型片：5件算1件计费（5.5元）")
    add(h, notes="与哈尔滨工程大学医院（HRB-HEU）为独立客户")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M3-01", "是", "口腔针类关键词", "MAT-15")
    fr(h, "FR-M3-11", "是", "针类5.5元/件", "MAT-02")
    fr(h, "FR-M3-13", "是", "洁牙尖/成型片5件算1件", "MAT-02")

    # H2 工程大学医院（独立客户）
    h = "哈尔滨工程大学医院"
    add(h, billing_enabled="是", pricing_mode="standard", stage="L2", complexity="低",
        modules={"M1", "M2", "M8", "INT", "CFG"},
        mat_hints={"MAT-01", "MAT-02", "MAT-03"})
    add(h, aliases=["哈尔滨工程大学医院", "工程大学医院"])
    add(h, special_rules="结款函：灭菌费用9折（单独打折），物流不变，账单计费正常")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M2-03", "是", "结款函灭菌费9折", "MAT-03")

    # I 道外区人民
    h = "道外区人民医院"
    add(h, billing_enabled="是", pricing_mode="hybrid", stage="L2", complexity="中",
        modules={"M1", "M3", "M8", "INT", "CFG"},
        mat_hints={"MAT-01", "MAT-02", "MAT-15"})
    add(h, special_rules="没有低温，所有高温价格3元/件")
    add(h, special_rules="敷料正常价格")
    add(h, notes="缺少辅料包价格明细")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M1-05", "是", "pathOverride：无低温、强制高温", "MAT-17")
    fr(h, "FR-M3-11", "是", "高温3元/件", "MAT-02")

    # Settlement-only discount hospitals
    settlement_discount = {
        "哈尔滨市南岗区人民医院（九院）": ("9折", "结款函灭菌费9折，账单正常计费，物流不变"),
        "黑龙江东大肛肠": ("75折", "结款函灭菌费75折，账单正常计费，物流不变"),
        "南岗区先锋路社区卫生服务中心": ("8折", "结款函灭菌费8折，账单正常计费，物流不变"),
        "黑龙江省社会康复医院": ("75折", "结款函灭菌费75折；存在单独收费项（待明细）"),
        "黑龙江省远东心脑血管医院": ("9折", "结款函9折；账单按日拆分录入"),
    }
    for h, info in settlement_discount.items():
        if info is None:
            continue
        rate, desc = info
        add(h, billing_enabled="是", pricing_mode="standard", stage="L2", complexity="低",
            modules={"M1", "M2", "M8", "INT", "CFG"}, mat_hints={"MAT-01", "MAT-02", "MAT-03"})
        add(h, special_rules=desc)
        fr(h, "FR-M1-01", "是")
        fr(h, "FR-M2-03", "是", f"结款函灭菌费{rate}，账单正常计费", "MAT-03")
    # 华夏眼科
    h = "哈尔滨华夏眼科医院"
    add(h, billing_enabled="是", pricing_mode="hybrid", stage="L2", complexity="低",
        modules={"M1", "M3", "M8", "INT", "CFG"}, mat_hints={"MAT-01", "MAT-02"})
    add(h, special_rules="3件（含）以上器械包单价2.75元，其他正常收费")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M3-06", "是", "≥3件器械包", "MAT-02")
    fr(h, "FR-M3-12", "是", "≥3件单价2.75", "MAT-02")

    # 三精肾病
    h = "三精肾病医院"
    add(h, billing_enabled="是", pricing_mode="hybrid", stage="L2", complexity="低",
        modules={"M1", "M3", "M8", "INT", "CFG"}, mat_hints={"MAT-01", "MAT-02"})
    add(h, special_rules="3件（含）以上器械包单价3元")
    add(h, special_rules="存在单独收费（打印价格为准）")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M3-06", "是", "≥3件器械包", "MAT-02")
    fr(h, "FR-M3-12", "是", "≥3件单价3元", "MAT-02")

    # 冰城医疗美容
    h = "哈尔滨冰城医疗美容医院"
    add(h, billing_enabled="是", pricing_mode="special_only", stage="L1", complexity="低",
        modules={"M1", "M3", "M8", "INT", "CFG"}, mat_hints={"MAT-01", "MAT-02", "MAT-15"})
    add(h, special_rules="整形包/脂充包：54.5元")
    add(h, special_rules="环钻：27.5元")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M3-10", "是", "整形包/脂充包54.5；环钻27.5", "MAT-15")

    # 省医院
    for h in ["黑龙江省医院（南岗院区）", "黑龙江省医院（香坊院区）"]:
        add(h, billing_enabled="是", pricing_mode="hybrid", stage="L3", complexity="高",
            modules={"M1", "M3", "M8", "M11", "INT", "CFG"},
            mat_hints={"MAT-01", "MAT-02", "MAT-03", "MAT-12"})
        add(h, special_rules="不需要删除JK行（保留包装/把数列）")
        add(h, special_rules="存在单独收费（待明细）")
        fr(h, "FR-M1-01", "是")
        fr(h, "FR-M3-21", "否", "不删除I/J/K/M等列（例外）", "MAT-01")
        fr(h, "FR-M3-23", "是", "保留包装/把数列", "MAT-02")
        fr(h, "FR-M11-03", "是", "按医生分配（待确认）", "MAT-12")

    # 中医大二院
    for h in ["黑龙江中医药大学附属第二医院（南岗）", "黑龙江中医药大学附属第二医院（哈南分院）"]:
        add(h, billing_enabled="是", pricing_mode="standard", stage="L3", complexity="中",
            modules={"M1", "M8", "INT", "CFG"},
            mat_hints={"MAT-05", "MAT-06", "MAT-07"})
        add(h, special_rules="需制作：1)价格汇总表 2)包数据+金额汇总表 3)不加金额汇总表")
        add(h, notes="参考企业微信文件")
        fr(h, "FR-M1-01", "是")
        fr(h, "FR-M8-05", "是", "价格汇总表", "MAT-05")
        fr(h, "FR-M8-06", "是", "包数据汇总（含/不含金额）", "MAT-06")

    # 太平人民
    h = "太平人民医院"
    add(h, billing_enabled="是", pricing_mode="hybrid", stage="L2", complexity="高",
        modules={"M1", "M2", "M3", "M6", "M8", "INT", "CFG"},
        mat_hints={"MAT-02", "MAT-15"})
    add(h, special_rules="参照价格表；原价导入，导出阶段折扣")
    add(h, special_rules="1把器械按价格表，保留1位小数")
    add(h, special_rules="2把、4把及以上75折，保留2位小数")
    add(h, special_rules="3把器械：所有原价16.5元收8.91元")
    add(h, special_rules="2把及以上：所有原价16.5元收8.91元")
    add(h, special_rules="不收物流费")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M2-04", "是", "导出阶段折扣", "MAT-02")
    fr(h, "FR-M2-05", "是", "按把数分段折扣", "MAT-02")
    fr(h, "FR-M2-06", "是", "1把1位小数/2把+2位小数", "MAT-02")
    fr(h, "FR-M2-07", "是", "原价16.5→8.91", "MAT-15")
    fr(h, "FR-M6-03", "是", "不收物流费", "MAT-03")

    # 武警总队
    h = "武警黑龙江省总队医院"
    add(h, billing_enabled="是", pricing_mode="special_only", stage="L2", complexity="中",
        modules={"M1", "M3", "INT", "CFG"}, mat_hints={"MAT-01", "MAT-02"})
    add(h, special_rules="可能出现0元计费，按下列规则单独收费：")
    add(h, special_rules="无纺布包装：20元")
    add(h, special_rules="纸塑袋包装：8元")
    add(h, special_rules="过氧化氢（包名）：35元")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M3-04", "是", "按包装材料分支", "MAT-02")
    fr(h, "FR-M3-17", "是", "0元导入按包装/包名覆盖", "MAT-01")

    # 呼兰区红十字
    h = "呼兰区红十字医院"
    add(h, billing_enabled="是", pricing_mode="standard", stage="L2", complexity="低",
        modules={"M1", "M5", "M8", "INT", "CFG"}, mat_hints={"MAT-03"})
    add(h, special_rules="低消1500元")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M5-01", "是", "低消1500元", "MAT-03")

    # 新发红十字
    h = "新发红十字医院"
    add(h, billing_enabled="是", pricing_mode="hybrid", stage="L2", complexity="高",
        modules={"M1", "M3", "M8", "M9", "INT", "CFG"},
        mat_hints={"MAT-02", "MAT-03", "MAT-11"})
    add(h, aliases=["新发（不带红十字的为未改状态账单）"])
    add(h, special_rules="穿刺器帽-3：22元")
    add(h, special_rules="每个科室低温敷料单独Sheet")
    add(h, special_rules="设备抵扣每月减免3270元")
    add(h, special_rules="加急：总价×125%，减免后约102.5%")
    add(h, special_rules="加急物流150元/次，减免后9折")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M1-03", "是", "区分「新发红十字」与「新发」", "MAT-01")
    fr(h, "FR-M3-10", "是", "穿刺器帽-3：22元", "MAT-02")
    fr(h, "FR-M8-08", "是", "低温敷料按科室独立Sheet", "MAT-02")
    fr(h, "FR-M8-17", "是", "设备抵扣-3270/月", "MAT-03")
    fr(h, "FR-M9-03", "是", "加急102.5%/物流9折", "MAT-03")

    # 悦美芳华
    h = "悦美芳华医疗门诊医院"
    add(h, billing_enabled="是", pricing_mode="standard", stage="L2", complexity="低",
        modules={"M1", "M5", "M8", "INT", "CFG"}, mat_hints={"MAT-03"})
    add(h, special_rules="低消1000元")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M5-01", "是", "低消1000元", "MAT-03")

    # 市五院 + 二门诊
    for h in ["哈尔滨市第五医院", "哈尔滨市第五医院（二门诊）"]:
        add(h, billing_enabled="是", pricing_mode="hybrid", stage="L3", complexity="极高",
            modules={"M1", "M5", "M6", "M8", "M10", "M11", "M12", "INT", "CFG"},
            mat_hints={"MAT-02", "MAT-03", "MAT-04", "MAT-07", "MAT-10", "MAT-11", "MAT-12", "MAT-13", "MAT-14"})
        add(h, special_rules="账单单独出，总结款函合并（与二门诊）")
        add(h, special_rules="需分科室汇总")
        add(h, special_rules="器械消杀后放手术室，按花名册分发至科室")
        add(h, special_rules="费用调整：x电钻、肖啸等；手术室表展示原价")
        add(h, special_rules="低温和高温不能放在同一表单")
        add(h, special_rules="供应室借调行为")
        add(h, special_rules="物流独立导入，50元/次，按比例分摊到各科室")
        add(h, special_rules="外来器械独立表，计入总结款函")
        add(h, notes="需要回顾视频录制")
        fr(h, "FR-M1-01", "是")
        fr(h, "FR-M1-08", "是", "与二门诊合并结款函", "MAT-03, MAT-07")
        fr(h, "FR-M6-04", "是", "物流次数独立导入", "MAT-14")
        fr(h, "FR-M6-05", "是", "按科室消毒费比例分摊", "MAT-04, MAT-07")
        fr(h, "FR-M8-04", "是", "分科室汇总表", "MAT-04")
        fr(h, "FR-M8-07", "是", "总汇总表（含二门诊/外来）", "MAT-07")
        fr(h, "FR-M8-09", "是", "费用调整Sheet", "MAT-11")
        fr(h, "FR-M10-01", "是", "费用调整不移除原行", "MAT-11")
        fr(h, "FR-M10-02", "是", "电钻/肖/啸等关键词", "MAT-11")
        fr(h, "FR-M10-03", "是", "花名册医生分配", "MAT-12")
        fr(h, "FR-M10-04", "是", "高低温分表", "MAT-04")
        fr(h, "FR-M10-05", "是", "供应室借调分摊", "MAT-13")
        fr(h, "FR-M10-08", "是", "总汇总勾稽", "MAT-07")
        fr(h, "FR-M11-01", "是", "花名册维护", "MAT-12")
        fr(h, "FR-M12-01", "是", "外来器械独立维护", "MAT-10")
        fr(h, "INT-06", "是", "外来器械独立导入", "MAT-10")
        fr(h, "INT-07", "是", "物流数据独立导入", "MAT-14")
        fr(h, "INT-12", "是", "花名册提示", "MAT-12")
        fr(h, "INT-18", "是", "多 Sheet 导出", "MAT-02")
        fr(h, "INT-20", "是", "总汇总与结款函勾稽", "MAT-07")

    # 香坊中医院 + 三辅社区 alias
    h = "香坊中医院"
    add(h, billing_enabled="是", pricing_mode="standard", stage="L2", complexity="中",
        modules={"M1", "M6", "M8", "M10", "INT", "CFG"},
        mat_hints={"MAT-02", "MAT-03", "MAT-07"})
    add(h, aliases=["三辅社区医院"])
    add(h, special_rules="正常价格")
    add(h, special_rules="与三辅社区：费用（账单）单独计算，结款函使用同一个")
    add(h, special_rules="同一天物流费用只算一个（50元）")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M1-08", "是", "与三辅社区合并结款函", "MAT-03")
    fr(h, "FR-M6-06", "是", "同日物流只计一次50元", "MAT-07")

    # 仁胜
    h = "哈尔滨仁胜医院"
    add(h, billing_enabled="是", pricing_mode="standard", stage="L2", complexity="中",
        modules={"M1", "M7", "M8", "INT", "CFG"}, mat_hints={"MAT-02", "MAT-03"})
    add(h, special_rules="正常价格")
    add(h, special_rules="减免物流费（物流卡余额扣除，账单标注剩余金额）")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M7-01", "是", "物流卡账户", "MAT-03")
    fr(h, "FR-M7-03", "是", "账单展示卡余额", "MAT-02")

    # 国药总院三院区
    guoyao = {
        "国药总医院主院区": ("哈尔滨汽轮机医院", "10mm 30度镜（高温）28元；驱血带13元；汽轮机核算数量算法；物流卡"),
        "国药总医院第二院区": ("电机厂医院", "导出名称替换；物流卡"),
        "国药总医院第三院区": ("哈尔滨锅炉厂医院", "导出名称替换；物流卡"),
    }
    for h, (old_name, rules) in guoyao.items():
        add(h, billing_enabled="是", pricing_mode="hybrid", stage="L3", complexity="高",
            modules={"M1", "M3", "M7", "M8", "INT", "CFG"},
            mat_hints={"MAT-02", "MAT-03", "MAT-04", "MAT-07"})
        add(h, aliases=[old_name])
        add(h, special_rules=f"原名：{old_name}，导出时替换为国药总院各院区名称")
        add(h, special_rules=rules)
        add(h, special_rules="手术室和其他科室单独计费；妇科手术室算在手术室内")
        add(h, special_rules="存在物流卡减免物流费（各院区独立）")
        add(h, notes="参考企业微信文件")
        fr(h, "FR-M1-01", "是")
        fr(h, "FR-M1-06", "是", f"{old_name}→{h}", "MAT-02")
        fr(h, "FR-M7-04", "是", "多院区独立物流卡", "MAT-03")
        if h == "国药总医院主院区":
            fr(h, "FR-M3-05", "是", "10mm 30度镜高温28元", "MAT-15")
            fr(h, "FR-M3-10", "是", "驱血带13元", "MAT-15")
            fr(h, "FR-M8-12", "是", "汽轮机核算数量算法", "MAT-03")

    # 祖研三院区
    zuyan_rules = {
        "祖研-黑龙江省中医医院（南岗院区）": "周一/三/五有发货则计物流（135收费）",
        "祖研-黑龙江省中医医院（三辅院区）": "与香安同日发货则物流50元平分（各25）；仅三辅则50元",
        "祖研-黑龙江省中医医院（香安院区）": "与三辅同日发货则物流50元平分（各25）；仅香安则50元",
    }
    for h, logistics in zuyan_rules.items():
        add(h, billing_enabled="是", pricing_mode="hybrid", stage="L2", complexity="极高",
            modules={"M1", "M3", "M6", "M8", "INT", "CFG"},
            mat_hints={"MAT-04", "MAT-05", "MAT-07", "MAT-14"})
        add(h, special_rules="特殊科室美容科/妇科：排针/排针包单独计费")
        add(h, special_rules="排针：10个按1件5.5元；11-20：5.5+5.5+2.5；21-30：5.5×3；盘每个5.5元")
        add(h, special_rules="各院区独立价格汇总文件")
        add(h, special_rules=f"物流：{logistics}；各科室物流按器械消毒费比例分摊")
        fr(h, "FR-M1-01", "是")
        fr(h, "FR-M1-07", "是", "三院区独立建档", "MAT-07")
        fr(h, "FR-M3-08", "是", "美容科/妇科科室条件", "MAT-04")
        fr(h, "FR-M3-13", "是", "排针10个算1件/FOLD", "MAT-02")
        fr(h, "FR-M6-05", "是", "物流按科室消毒费比例分摊", "MAT-04, MAT-07")
        fr(h, "FR-M6-06", "是", "跨院区同日物流合并/拆分", "MAT-07")
        fr(h, "FR-M6-07", "是", logistics, "MAT-14")
        fr(h, "FR-M8-05", "是", "价格汇总表（各院区）", "MAT-05")

    # 市二院
    h = "哈尔滨市第二医院"
    add(h, billing_enabled="是", pricing_mode="hybrid", stage="L3", complexity="高",
        modules={"M1", "M3", "M8", "M10", "INT", "CFG"},
        mat_hints={"MAT-02", "MAT-04"})
    add(h, special_rules="部分科室有特殊收费")
    add(h, special_rules="部分科室需要合并")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M10-09", "是", "科室合并/特殊收费", "MAT-04")

    # 远东
    h = "黑龙江省远东心脑血管医院"
    add(h, billing_enabled="是", pricing_mode="standard", stage="L4", complexity="高",
        modules={"M1", "M2", "M8", "M14", "INT", "CFG"},
        mat_hints={"MAT-02", "MAT-03", "MAT-16"})
    add(h, special_rules="账单每月录入（30天统一录入/每天录入一个），需拆分成每日计费")
    fr(h, "FR-M1-01", "是")
    fr(h, "FR-M2-03", "是", "结款函9折", "MAT-03")
    fr(h, "FR-M14-01", "是", "按发货日期拆分日账单", "MAT-16")
    fr(h, "FR-M14-03", "是", "30个日Excel或总表拆分", "MAT-16")

    # 南岗区妇产 - minimal
    h = "南岗区妇产医院"
    add(h, billing_enabled="待确认", pricing_mode="待确认", stage="L2", complexity="待确认",
        modules={"M1", "M8", "INT", "CFG"}, mat_hints={"MAT-15"})
    add(h, special_rules="有单独报价单图片参考；等待账单表格详细对比验证；结款函正常")
    add(h, notes="规则待账单样表验证")

    # 中医一院
    h = "黑龙江中医药大学附属第一医院"
    add(h, billing_enabled="待确认", pricing_mode="待确认", stage="L4", complexity="待确认",
        modules={"M1", "INT", "CFG"})
    add(h, special_rules="需要单独沟通")
    add(h, notes="规则待业务沟通")

    # 骨伤科、奥兰 - no rules in txt
    for h in ["哈尔滨市骨伤科医院", "奥兰医院"]:
        add(h, billing_enabled="待确认", modules={"M1", "INT", "CFG"})

    return data


def infer_mat_from_filename(filename):
    fn = filename
    matched = set()
    # Order-sensitive: more specific patterns first
    rules = [
        ("MAT-10", ["外来器械"]),
        ("MAT-11", ["费用调整", "调整表"]),
        ("MAT-08", ["把数表", "器械量表", "器械量"]),
        ("MAT-09", ["灭菌包装", "包装表"]),
        ("MAT-04", ["分科室", "科室汇总"]),
        ("MAT-05", ["价格汇总"]),
        ("MAT-06", ["包数据", "包数汇总"]),
        ("MAT-07", ["总汇总"]),
        ("MAT-12", ["花名册"]),
        ("MAT-13", ["借调", "科室使用", "供应室"]),
        ("MAT-14", ["物流路线", "物流次数", "路线表"]),
        ("MAT-16", ["日结单", "日结表"]),
        ("MAT-15", ["价格表", "报价单", "报价"]),
        ("MAT-03", ["结款函", "结款"]),
        ("MAT-01", ["未改", "原始导出", "系统导出"]),
        ("MAT-02", ["已改", "特色账单"]),
        ("MAT-17", ["规则说明", "合同"]),
    ]
    for code, keywords in rules:
        for kw in keywords:
            if kw in fn:
                matched.add(code)
                break
    if not matched and fn.lower().endswith(('.xlsx', '.xls', '.xlsm')):
        if "账单" in fn and "未改" not in fn:
            matched.add("MAT-02")
    return matched


def list_reference_files(hospital):
    folder = REF_BASE / hospital
    if not folder.is_dir():
        return []
    files = []
    for p in sorted(folder.rglob('*')):
        if p.is_file() and not p.name.startswith('.'):
            files.append(p.relative_to(folder).as_posix())
    return files


def system_status_for_fr(fr_id, module):
    """Map FR to implementation status from codebase."""
    implemented = {
        "FR-M1-01", "FR-M1-02", "FR-M1-03", "FR-M1-04", "FR-M1-05", "FR-M1-06", "FR-M1-09",
        "FR-M2-01", "FR-M2-08", "FR-M2-09",
        "FR-M3-01", "FR-M3-02", "FR-M3-04", "FR-M3-05", "FR-M3-06", "FR-M3-10", "FR-M3-11",
        "FR-M3-12", "FR-M3-13", "FR-M3-14", "FR-M3-17", "FR-M3-18", "FR-M3-19", "FR-M3-24", "FR-M3-25", "FR-M3-26",
        "FR-M4-01", "FR-M4-02", "FR-M4-03", "FR-M4-04", "FR-M4-05", "FR-M4-06", "FR-M4-08",
        "FR-M5-01", "FR-M5-02", "FR-M5-06",
        "FR-M6-01", "FR-M6-02", "FR-M6-03", "FR-M6-09",
        "FR-M8-01", "FR-M8-02", "FR-M8-03", "FR-M8-13", "FR-M8-14",
        "INT-01", "INT-02", "INT-03", "INT-04", "INT-05", "INT-08", "INT-09", "INT-10",
        "INT-13", "INT-14", "INT-15", "INT-16", "INT-17", "INT-19",
        "CFG-01", "CFG-02", "CFG-04", "CFG-09", "CFG-10",
        "NFR-01", "NFR-02", "NFR-04", "NFR-06",
    }
    partial = {
        "FR-M2-02", "FR-M2-03", "FR-M2-04", "FR-M2-05", "FR-M2-06", "FR-M2-07", "FR-M2-10",
        "FR-M3-07", "FR-M3-08", "FR-M3-09", "FR-M3-15", "FR-M3-16", "FR-M3-20", "FR-M3-21", "FR-M3-22", "FR-M3-23",
        "FR-M4-07",
        "FR-M5-03", "FR-M5-04", "FR-M5-05",
        "FR-M6-05", "FR-M6-06", "FR-M6-07", "FR-M6-08",
        "FR-M8-04", "FR-M8-05", "FR-M8-06", "FR-M8-07", "FR-M8-08", "FR-M8-09", "FR-M8-10", "FR-M8-11",
        "FR-M8-12", "FR-M8-15", "FR-M8-16", "FR-M8-17",
        "FR-M1-07", "FR-M1-08",
        "INT-11", "INT-20",
        "CFG-05",
        "NFR-03", "NFR-05",
    }
    not_impl_prefixes = ("FR-M7", "FR-M9", "FR-M10", "FR-M11", "FR-M12", "FR-M13", "FR-M14")
    if fr_id in implemented:
        return "已实现"
    if fr_id in partial:
        return "部分实现"
    if any(fr_id.startswith(p) for p in not_impl_prefixes):
        return "未实现"
    if fr_id.startswith("CFG"):
        return "部分实现"
    return MODULE_SYSTEM_STATUS.get(module, "待填写")


def module_cover(modules, mod):
    return "●" if mod in modules else "○"


def priority_for_module(modules, mod):
    if mod not in modules:
        return "—"
    if mod in {"M1", "M2", "M3", "M4", "M8", "INT", "CFG"}:
        return "P0"
    if mod in {"M5", "M6", "M7"}:
        return "P1"
    if mod in {"M9", "M10", "M11", "M12"}:
        return "P2"
    return "P3"


def sanitize_filename(name):
    return re.sub(r'[<>:"/\\|?*]', '_', name) + ".md"


def build_mat_table(hospital, ref_files, mat_hints):
    rows = []
    file_mats = set()
    file_by_mat = {code: [] for code in MAT_NAMES}
    for f in ref_files:
        mats = infer_mat_from_filename(f)
        file_mats.update(mats)
        for m in mats:
            file_by_mat[m].append(f)

    all_mats = file_mats | mat_hints
    for code in MAT_NAMES:
        has = code in all_mats
        check = "☑" if has else "☐"
        examples = ", ".join(file_by_mat[code][:3]) if file_by_mat[code] else ("待填写" if has else "")
        note = ""
        if has and not file_by_mat[code]:
            note = "规则推断需样表，文件夹暂无对应文件"
        rows.append(f"| {code} | {MAT_NAMES[code]} | {check} | {examples or '待填写'} | 待填写 | {note} |")
    return "\n".join(rows)


def build_module_matrix(hospital, data):
    d = data[hospital]
    mods = d["modules"]
    lines = []
    module_names = {
        "M1": "特色账单开关与客户档案", "M2": "折扣体系", "M3": "特殊计费规则引擎",
        "M4": "多报价与对账提示", "M5": "低消/封顶", "M6": "物流独立计费与均摊",
        "M7": "物流卡额度", "M8": "账单/结款函/汇总导出", "M9": "加急收费与减免",
        "M10": "科室借调与费用调整", "M11": "花名册管理", "M12": "外来器械计价",
        "M13": "器械量表/把数表", "M14": "日结拆分", "INT": "流程集成（导入/对账/导出）",
        "CFG": "配置管理 UI",
    }
    for mod, name in module_names.items():
        cover = module_cover(mods, mod)
        pri = priority_for_module(mods, mod)
        status = MODULE_SYSTEM_STATUS.get(mod, "待填写") if cover == "●" else "—"
        gap = "待填写" if cover == "●" else "—"
        if cover == "●" and status == "未实现":
            gap = f"需新开发{mod}模块"
        elif cover == "●" and status == "部分实现":
            gap = "部分能力已有，需扩展配置/导出"
        elif cover == "●" and status == "已实现":
            gap = "以配置录入为主"
        lines.append(f"| {mod} | {name} | {cover} | {pri} | {status} | {gap} |")
    return "\n".join(lines)


CODE_MAP = {
    "FR-M1-01": "`Customer.billingEnabled`",
    "FR-M2-01": "`CustomerBillingPolicy`；`PricingEngine`",
    "FR-M3-10": "`CustomerProductRule`；`PricingEngine`",
    "FR-M4-01": "`CustomerProductRule.acceptedPrices`",
    "FR-M5-01": "`MonthlySettlementCalculator`",
    "FR-M6-01": "`LogisticsFeeCalculator`",
    "FR-M8-01": "`HospitalReconciliationServiceImpl`",
    "INT-01": "`CustomerResolver`；`BokangDataImportRunner`",
    "INT-03": "`PricingEngine.processRow()`",
}


def build_fr_section(section_title, section_num, fr_ids, hospital, data):
    d = data[hospital]
    lines = [f"## {section_num}、{section_title}", "",
             "| 功能项ID | 功能模块 | 子功能/配置项 | 代码映射(类/模块/表) | 是否启用 | 配置参数/规则描述 | 参考材料类型 | 验收标准 | 备注 |",
             "|----------|----------|---------------|----------------------|:--------:|-------------------|--------------|----------|------|"]
    for fr_id, module, subtitle in FR_ROWS:
        if fr_id not in fr_ids:
            continue
        cfg = d["fr_config"].get(fr_id, {})
        enabled = cfg.get("enabled", "待确认")
        config = cfg.get("config", "待填写")
        mat = cfg.get("mat", "")
        remark = cfg.get("remark", "")
        sys_st = system_status_for_fr(fr_id, module)
        if remark:
            remark = f"{remark}；系统：{sys_st}"
        else:
            remark = f"系统：{sys_st}"
        code_map = CODE_MAP.get(fr_id, "待填写")
        lines.append(f"| {fr_id} | {module} | {subtitle} | {code_map} | {enabled} | {config} | {mat} | 待填写 | {remark} |")
    lines.append("")
    lines.append("---")
    lines.append("")
    return "\n".join(lines)


INT_ROWS = [
    ("INT-01", "INT", "发货表医院名→客户解析"),
    ("INT-02", "INT", "规则编译加载"),
    ("INT-03", "INT", "行级计价与 policy_traces"),
    ("INT-04", "INT", "导入列预处理"),
    ("INT-05", "INT", "0元行覆盖计价"),
    ("INT-06", "INT", "外来器械独立导入"),
    ("INT-07", "INT", "物流数据独立导入"),
    ("INT-08", "INT", "对账差异展示"),
    ("INT-09", "INT", "规则追溯展示"),
    ("INT-10", "INT", "多报价展示"),
    ("INT-11", "INT", "加急标记操作"),
    ("INT-12", "INT", "花名册提示"),
    ("INT-13", "INT", "对账版本管理"),
    ("INT-14", "INT", "复核/导出状态流转"),
    ("INT-15", "INT", "导出模板引擎"),
    ("INT-16", "INT", "折扣时机（账单 vs 导出）"),
    ("INT-17", "INT", "导出名称替换"),
    ("INT-18", "INT", "多 Sheet 导出"),
    ("INT-19", "INT", "结款函 Word/Excel 填充"),
    ("INT-20", "INT", "汇总勾稽"),
]

CFG_ROWS = [
    ("CFG-01", "CFG", "客户管理集中入口"),
    ("CFG-02", "CFG", "产品规则表单"),
    ("CFG-03", "CFG", "规则优先级拖拽"),
    ("CFG-04", "CFG", "规则试算"),
    ("CFG-05", "CFG", "规则批量导入导出"),
    ("CFG-06", "CFG", "花名册管理页"),
    ("CFG-07", "CFG", "物流卡维护"),
    ("CFG-08", "CFG", "导出模板上传映射"),
    ("CFG-09", "CFG", "通用计价规则库"),
    ("CFG-10", "CFG", "客户升级/迁移"),
]

NFR_ROWS = [
    ("NFR-01", "NFR", "行级规则追溯字段"),
    ("NFR-02", "NFR", "导出日志"),
    ("NFR-03", "NFR", "规则变更审计"),
    ("NFR-04", "NFR", "大账单性能"),
    ("NFR-05", "NFR", "配置无需发版（已支持类型内）"),
    ("NFR-06", "NFR", "结果可解释"),
]


def build_extra_section(title, rows, hospital, data):
    d = data[hospital]
    mods = d["modules"]
    lines = [f"## {title}", "",
             "| 功能项ID | 功能模块 | 子功能/配置项 | 代码映射(类/模块/表) | 是否启用 | 配置参数/规则描述 | 参考材料类型 | 验收标准 | 备注 |",
             "|----------|----------|---------------|----------------------|:--------:|-------------------|--------------|----------|------|"]
    int_defaults = {
        "INT-06": "M12" in mods,
        "INT-07": "M6" in mods,
        "INT-11": "M9" in mods,
        "INT-12": "M11" in mods,
        "INT-18": "M8" in mods and ("M10" in mods or hospital == "新发红十字医院"),
        "INT-20": "M8" in mods and ("M10" in mods or "M7" in mods),
    }
    for fr_id, module, subtitle in rows:
        cfg = d["fr_config"].get(fr_id, {})
        if fr_id in cfg:
            enabled = cfg.get("enabled", "待确认")
        elif module == "NFR":
            enabled = "是"
        elif module == "CFG":
            enabled = "是" if "CFG" in mods else "待确认"
        elif module == "INT":
            if fr_id in int_defaults:
                enabled = "是" if int_defaults[fr_id] else "否"
            else:
                enabled = "是" if "INT" in mods else "待确认"
        else:
            enabled = "待确认"
        config = cfg.get("config", "待填写")
        mat = cfg.get("mat", "")
        sys_st = system_status_for_fr(fr_id, module)
        remark = cfg.get("remark", f"系统：{sys_st}")
        if "系统：" not in remark:
            remark = f"{remark}；系统：{sys_st}"
        code_map = CODE_MAP.get(fr_id, "待填写")
        lines.append(f"| {fr_id} | {module} | {subtitle} | {code_map} | {enabled} | {config} | {mat} | 待填写 | {remark} |")
    lines.append("")
    lines.append("---")
    lines.append("")
    return "\n".join(lines)


SECTION_FR_MAP = {
    "五、M1 特色账单开关与客户档案": [f"FR-M1-{i:02d}" for i in range(1, 10)],
    "六、M2 折扣体系": [f"FR-M2-{i:02d}" for i in range(1, 11)],
    "七、M3 特殊计费规则引擎": [f"FR-M3-{i:02d}" for i in range(1, 28)],
    "八、M4 多报价与对账提示": [f"FR-M4-{i:02d}" for i in range(1, 9)],
    "九、M5 低消/封顶": [f"FR-M5-{i:02d}" for i in range(1, 7)],
    "十、M6 物流独立计费与均摊": [f"FR-M6-{i:02d}" for i in range(1, 10)],
    "十一、M7 物流卡额度": [f"FR-M7-{i:02d}" for i in range(1, 6)],
    "十二、M8 账单/结款函/汇总导出": [f"FR-M8-{i:02d}" for i in range(1, 18)],
    "十三、M9 加急收费与减免": [f"FR-M9-{i:02d}" for i in range(1, 7)],
    "十四、M10 科室借调与费用调整": [f"FR-M10-{i:02d}" for i in range(1, 11)],
    "十五、M11 花名册管理": [f"FR-M11-{i:02d}" for i in range(1, 6)],
    "十六、M12 外来器械计价": [f"FR-M12-{i:02d}" for i in range(1, 7)],
    "十七、M13 器械量表/把数表": [f"FR-M13-{i:02d}" for i in range(1, 6)],
    "十八、M14 日结拆分": [f"FR-M14-{i:02d}" for i in range(1, 5)],
}


def build_pending_sections(hospital, data, ref_files):
    d = data[hospital]
    pending = []
    if d["billing_enabled"] == "待确认":
        pending.append("二、医院基础信息（部分字段）")
    if not ref_files:
        pending.append("三、参考材料清单（无参考文件夹或文件夹为空，文件名/月份待补充）")
    if not d["special_rules"]:
        pending.append("二十四、特色计费规则摘要（无已知规则）")
    if len(d["fr_config"]) < 5:
        pending.append("五–十八、功能清单明细（大部分配置参数待填写）")
    pending.append("二十二、差距分析与可行性评估")
    pending.append("验收标准列（各功能项）")
    return pending


def generate_doc(hospital, data):
    d = data[hospital]
    ref_files = list_reference_files(hospital)
    code = CUSTOMER_CODES.get(hospital, "待填写")
    aliases = d.get("aliases", [])
    alias_str = "; ".join(aliases) if aliases else "待填写"
    notes = d.get("notes", [])
    special = d.get("special_rules", [])
    today = date.today().isoformat()

    ref_list = "\n".join(f"- `{f}`" for f in ref_files) if ref_files else "- （参考文件夹不存在或为空，待补充）"

    special_section = ""
    if special:
        special_section = "## 二十四、特色计费规则摘要（来源：`铂康/特色账单规则.txt`）\n\n"
        for i, rule in enumerate(special, 1):
            special_section += f"{i}. {rule}\n"
        special_section += "\n> 以上规则来自现有源材料；未列出的细项请勿自行推断，待业务补充。\n\n---\n\n"
    else:
        special_section = "## 二十四、特色计费规则摘要\n\n（暂无已知规则，待填写）\n\n---\n\n"

    pending = build_pending_sections(hospital, data, ref_files)
    pending_section = "## 二十五、待用户补充章节清单\n\n"
    for p in pending:
        pending_section += f"- [ ] {p}\n"

    # Build all FR sections
    fr_sections = []
    for title, fr_ids in SECTION_FR_MAP.items():
        short_title = title.split("、", 1)[1]
        fr_sections.append(build_fr_section(short_title, title.split("、")[0], fr_ids, hospital, data))
    fr_sections.append(build_extra_section("十九、流程集成点（INT）", INT_ROWS, hospital, data))
    fr_sections.append(build_extra_section("二十、配置管理需求（CFG）", CFG_ROWS, hospital, data))
    fr_sections.append(build_extra_section("二十一、非功能需求（NFR）", NFR_ROWS, hospital, data))

    doc = f"""# 特色账单系统 — 医院功能需求登记表

## {hospital}

## 文档信息

| 项目 | 内容 |
|------|------|
| **文档名称** | 特色账单系统 — {hospital} 功能需求登记表 |
| **版本** | v1.0 |
| **编制日期** | {today} |
| **文档性质** | 单院需求登记 · 基于模板与源材料自动填充已知项 |
| **关联文档** | `docs/7.17特色账单系统功能需求说明书.md`、`docs/特色账单系统-医院功能需求通用登记表（模板）.md` |
| **源材料** | `铂康/特色账单规则.txt`、`铂康/参考文件（按照医院）/{hospital}/` |
| **适用范围** | {hospital} 消毒供应账单全流程（导入 → 计价 → 对账 → 月度结算 → 导出） |

---

## 一、使用说明

本文件由通用模板复制生成。**已填内容**均来自 `铂康/特色账单规则.txt`、`docs/7.17特色账单系统功能需求说明书.md` 及代码库可推断项；**未填/待填写**项需业务人员后续补充。

功能清单「是否启用」= 该医院业务需求；「备注」中「系统：xxx」= 当前代码实现状态（已实现/部分实现/未实现/不适用）。

---

## 二、医院基础信息

| 字段 | 内容 |
|------|------|
| **医院规范名称** | {hospital} |
| **系统客户编码** | {code} |
| **是否启用特色账单** | {d['billing_enabled']} |
| **计价模式** | {d['pricing_mode']} |
| **关联院区/别名** | {alias_str} |
| **业务负责人** | 待填写 |
| **规则配置员** | 待填写 |
| **需求填写日期** | {today} |
| **目标上线阶段** | {d.get('stage', '待确认')} |
| **复杂度自评** | {d.get('complexity', '待确认')} |
| **每月账单量级（行数约）** | 待填写 |
| **特殊说明** | {'；'.join(notes) if notes else '待填写'} |

---

## 三、参考材料清单

> 文件夹内实际文件见下方列表；MAT 类型由文件名推断或规则提示勾选。

### 3.1 文件夹内参考文件

{ref_list}

### 3.2 材料类型勾选

| 材料代码 | 材料类型 | 是否有样表 | 文件名示例 | 月份/版本 | 备注 |
|----------|----------|:--------:|------------|-----------|------|
{build_mat_table(hospital, ref_files, d.get('mat_hints', set()))}

---

## 四、模块覆盖总览矩阵

| 模块 | 模块名称 | 覆盖(●/○/△/—) | 优先级(P0–P3) | 系统现状(已支持/部分/未支持) | 差距摘要 |
|:----:|----------|:-------------:|:-------------:|:--------------------------:|----------|
{build_module_matrix(hospital, data)}

---

{chr(10).join(fr_sections)}

{special_section}

## 二十二、差距分析与可行性评估

| 评估项 | 结论 | 说明 |
|--------|:----:|------|
| **整体可行** | 待填写 | |
| **P0 模块可交付** | 待填写 | M1–M4, M8 基础导出 |
| **需新开发模块** | 待填写 | 见模块矩阵「未实现」项 |
| **需业务确认项** | 待填写 | |
| **参考材料完整度** | {'部分' if ref_files else '缺失'} | {'已有 ' + str(len(ref_files)) + ' 个参考文件' if ref_files else '参考文件夹不存在或为空'} |
| **预估配置工作量（人天）** | 待填写 | |
| **预估开发工作量（人天）** | 待填写 | |
| **风险与依赖** | 待填写 | |
| **建议上线阶段** | {d.get('stage', '待确认')} | |

---

{pending_section}

---

*本文档由脚本自动生成于 {today}；请勿将「待填写」项当作已确认需求。*
"""
    return doc


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    data = build_hospital_data()

    rich = []
    sparse = []
    txt_only = []

    txt_hospitals_not_in_folder = [
        "黑龙江中医药大学附属第三医院",  # 非电力，txt 单独提及
        "三辅社区医院",  # 香坊中医院别名
        "哈尔滨工程大学医院",
        "电机厂医院", "哈尔滨锅炉厂医院", "哈尔滨汽轮机医院",  # 国药别名
        "哈尔滨市呼兰区中医医院",  # 文件夹名：呼兰中医院
    ]

    for hospital in HOSPITALS:
        content = generate_doc(hospital, data)
        path = OUTPUT_DIR / sanitize_filename(hospital)
        path.write_text(content, encoding="utf-8")
        d = data[hospital]
        score = len(d["special_rules"]) + len(d["fr_config"])
        if score >= 8:
            rich.append(hospital)
        elif score <= 2:
            sparse.append(hospital)
        else:
            pass

    # Also note hospitals only in txt
    folder_set = set(HOSPITALS)
    for name in txt_hospitals_not_in_folder:
        if name not in folder_set:
            txt_only.append(name)

    print(f"Created {len(HOSPITALS)} files in {OUTPUT_DIR}")
    print(f"Rich data ({len(rich)}): {rich}")
    print(f"Mostly empty ({len(sparse)}): {sparse}")
    print(f"Txt-only not in folder ({len(txt_only)}): {txt_only}")
    print(f"Reference base exists: {REF_BASE.is_dir()}")


if __name__ == "__main__":
    main()
