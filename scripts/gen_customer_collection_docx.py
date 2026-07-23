#!/usr/bin/env python3
"""生成「测试材料补充清单」Word 文档（客户阅读版）。"""

from __future__ import annotations

from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "测试用例" / "特色账单测试材料补充清单（客户版）.docx"

FONT = "微软雅黑"
COLOR_TITLE = RGBColor(0x1A, 0x47, 0x7A)
COLOR_H1 = RGBColor(0xC0, 0x39, 0x2B)
COLOR_H2 = RGBColor(0x1A, 0x47, 0x7A)
COLOR_H3 = RGBColor(0x2E, 0x86, 0xAB)


def set_run(run, size=11, bold=False, color=None):
    run.font.name = FONT
    run.font.size = Pt(size)
    run.bold = bold
    run._element.rPr.rFonts.set(qn("w:eastAsia"), FONT)
    if color:
        run.font.color.rgb = color


def add_para(doc, text, *, size=11, bold=False, color=None, align=None, space_after=6):
    p = doc.add_paragraph()
    if align:
        p.alignment = align
    p.paragraph_format.space_after = Pt(space_after)
    run = p.add_run(text)
    set_run(run, size=size, bold=bold, color=color)
    return p


def add_bullets(doc, items, size=11):
    for item in items:
        p = doc.add_paragraph(style="List Bullet")
        p.paragraph_format.space_after = Pt(3)
        run = p.add_run(item)
        set_run(run, size=size)


def set_cell_shading(cell, fill: str):
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        from docx.oxml import OxmlElement

        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)
    shd.set(qn("w:val"), "clear")


def add_table(doc, headers: list[str], rows: list[list[str]], col_widths_cm: list[float] | None = None):
    table = doc.add_table(rows=1 + len(rows), cols=len(headers))
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    hdr = table.rows[0].cells
    for j, h in enumerate(headers):
        hdr[j].text = ""
        p = hdr[j].paragraphs[0]
        run = p.add_run(h)
        set_run(run, size=10, bold=True, color=RGBColor(0xFF, 0xFF, 0xFF))
        set_cell_shading(hdr[j], "1A477A")
    for i, row in enumerate(rows):
        for j, val in enumerate(row):
            cell = table.rows[i + 1].cells[j]
            cell.text = ""
            p = cell.paragraphs[0]
            run = p.add_run(val)
            set_run(run, size=10)
            if i % 2 == 1:
                set_cell_shading(cell, "F2F6FA")
    if col_widths_cm:
        for row in table.rows:
            for j, w in enumerate(col_widths_cm):
                row.cells[j].width = Cm(w)
    doc.add_paragraph()
    return table


def build():
    doc = Document()
    sec = doc.sections[0]
    sec.top_margin = Cm(2)
    sec.bottom_margin = Cm(2)
    sec.left_margin = Cm(2.2)
    sec.right_margin = Cm(2.2)

    add_para(doc, "特色账单系统", size=22, bold=True, color=COLOR_TITLE, align=WD_ALIGN_PARAGRAPH.CENTER, space_after=4)
    add_para(doc, "测试材料补充清单", size=18, bold=True, color=COLOR_TITLE, align=WD_ALIGN_PARAGRAPH.CENTER, space_after=4)
    add_para(doc, "（给客户）", size=12, color=RGBColor(0x66, 0x66, 0x66), align=WD_ALIGN_PARAGRAPH.CENTER, space_after=12)
    add_para(doc, "更新日期：2026-07-21", size=10, align=WD_ALIGN_PARAGRAPH.CENTER, space_after=18)

    # 说明
    add_para(doc, "说明", size=14, bold=True, color=COLOR_H2, space_after=8)
    add_para(
        doc,
        "我们在用 Excel 账单核对「特色计价」是否正确。每家医院、每个账期需要两套表：",
        space_after=4,
    )
    add_bullets(doc, [
        "原始账单 — 系统计价前导出的 Excel（未做特色调价）",
        "处理后账单 — 贵院确认过的正确结果 Excel",
    ])
    add_para(doc, "两套表必须是同一账期（起止日期一致），同一发货单、同一包名、同一包数能对应上。", space_after=4)
    add_bullets(doc, [
        "若某行做了特色调价 → 处理后单价/总价应与原始不同",
        "若某行没做特色调价 → 两表价格应相同",
    ])
    add_para(
        doc,
        "部分医院 6 月原始与处理后价格完全一样，只能证明「6 月没有报错」，无法验证特色规则是否算对。"
        "这类医院需要再提供其它月份、且两表有差别的账单，才能做完整测试。",
        space_after=14,
    )

    # 第一节
    add_para(doc, "一、最优先：缺 Excel，导致测试做不完（共 2 家）", size=14, bold=True, color=COLOR_H1, space_after=6)
    add_para(doc, "请优先补齐下列文件，否则这两家无法完成逐月测试。", bold=True, space_after=10)

    add_para(doc, "1. 哈尔滨市红十字妇产医院", size=12, bold=True, color=COLOR_H3, space_after=4)
    add_para(doc, "当前状态：6 月已测完；4 月、5 月测不了 — 缺处理后账单", bold=True, space_after=4)
    add_para(doc, "已有文件：", bold=True, space_after=2)
    add_bullets(doc, [
        "原始：哈尔滨红十字4月账单.xlsx",
        "原始：哈尔滨红十字5月账单.xlsx",
        "原始：哈尔滨红十字6月账单.xlsx",
        "处理后：6月__哈尔滨红十字妇产医院6月账单.xlsx（及分科室、结款函）",
    ])
    add_para(doc, "还缺什么：", bold=True, space_after=4)
    add_table(doc, ["月份", "缺少的文件", "说明"], [
        ["4 月", "4 月处理后账单", "原始已有，不用重给"],
        ["5 月", "5 月处理后账单", "原始已有，不用重给"],
    ], [2.5, 5.5, 7.5])
    add_para(doc, "处理后账单应满足：", bold=True, space_after=2)
    add_bullets(doc, [
        "与对应月份原始表同一账期",
        "按发货单号、包名、包数能逐行对应",
        "做了特色调价的行，单价/总价与原始不同（如：低温 1 件 22 元、湿化瓶、T 型管、喉镜/软管等）",
        "建议 4、5 月尽量包含低温或外来器械相关明细",
    ], size=10)
    doc.add_page_break()

    add_para(doc, "2. 哈尔滨工业大学医院", size=12, bold=True, color=COLOR_H3, space_after=4)
    add_para(doc, "当前状态：4 月、5 月已测完；6 月测不了 — 原始与处理后不是同一账期", bold=True, space_after=4)
    add_para(doc, "已有文件：", bold=True, space_after=2)
    add_bullets(doc, [
        "原始：工业大学4.15-5.14账单.xlsx、5.15-6.14账单.xlsx 等",
        "处理后：6月__哈尔滨工业大学医院6.15-7.14月账单.xlsx",
    ])
    add_para(doc, "还缺什么：", bold=True, space_after=4)
    add_table(doc, ["账期", "缺少的文件", "说明"], [
        ["6 月 15 日～7 月 14 日", "该账期的原始账单", "处理后 6.15–7.14 已有，不用重给"],
    ], [4.5, 4.5, 6.5])
    add_para(doc, "为什么不能测 6 月：现有原始最晚只到 5.15–6.14，与处理后 6.15–7.14 不是同一批业务数据，无法逐行对比。", space_after=4)
    add_para(doc, "原始账单应满足：", bold=True, space_after=2)
    add_bullets(doc, [
        "账期必须是 2026-06-15 至 2026-07-14（与已有处理后表一致）",
        "建议包含口腔相关明细（针类、洁牙尖、成型片、克氏针、车针等）",
    ], size=10)
    doc.add_page_break()

    # 第二节
    add_para(doc, "二、优先：6 月两表无差别，需补「有差别月份」的账单（共 10 家）", size=14, bold=True, color=COLOR_H1, space_after=6)
    add_para(
        doc,
        "下列医院 6 月原始与处理后价格相同，测试只能确认「没报错」，不能验证特色规则。"
        "我们已在其它月份发现「原始 ≠ 处理后」的记录，请优先补下表所列月份（每月仍需：原始 + 处理后，两套齐全）。",
        space_after=8,
    )
    add_table(
        doc,
        ["序号", "医院名称", "建议补哪个月", "约多少处价格差别"],
        [
            ["1", "太平人民医院", "5 月", "约 101 处（建议最优先）"],
            ["2", "南岗区妇产医院", "5 月", "约 10 处"],
            ["3", "国药总医院第二院区", "2 月", "约 3 处"],
            ["4", "祖研-黑龙江省中医医院（香安院区）", "4 月", "约 3 处"],
            ["5", "黑龙江维多利亚妇产医院", "5 月", "约 2 处"],
            ["6", "呼兰区红十字医院", "5 月", "约 2 处"],
            ["7", "国药总医院主院区", "3 月", "约 1 处"],
            ["8", "黑龙江省社会康复医院", "4 月", "约 1 处"],
            ["9", "呼兰中医院", "5 月", "约 1 处"],
            ["10", "黑龙江中医药大学附属第二医院（哈南分院）", "5 月", "约 1 处"],
        ],
        [1.2, 7.5, 2.5, 3.5],
    )
    add_para(doc, "每月需提供的材料：", bold=True, space_after=2)
    add_bullets(doc, [
        "该月原始账单（计价前）",
        "该月处理后账单（贵院确认的正确版）",
        "两表同一账期；有差别的行，处理后价格应与原始不同",
    ])
    add_para(
        doc,
        "另：黑龙江省第二医院（南岗院区）6 月无差别；业务上 4 月约有 3 处与口腔科成型夹相关差异，"
        "若方便请一并提供 4 月原始 + 处理后。",
        size=10,
        space_after=14,
    )

    # 第三节
    add_para(doc, "三、6 月及 1–6 月均无差别 — 暂可不补材料（共 8 家）", size=14, bold=True, color=COLOR_H2, space_after=6)
    add_para(doc, "下列医院目前各月原始与处理后均无价格差别，除非贵院确认某月应有特色调价，否则可暂不优先提供新材料。", space_after=4)
    add_bullets(doc, [
        "国药总医院第三院区",
        "哈尔滨市第五医院（二门诊）",
        "黑龙江九洲妇科医院",
        "哈尔滨仁胜医院",
        "香坊中医院",
        "悦美芳华医疗门诊医院",
        "黑龙江省第二医院（南岗院区）",
        "哈尔滨市呼兰区第一人民医院",
    ])
    doc.add_paragraph()

    # 第四节
    add_para(doc, "四、6 月已测完，暂无其它阻塞 — 供参考（共 3 家）", size=14, bold=True, color=COLOR_H2, space_after=6)
    add_bullets(doc, [
        "哈尔滨华夏眼科医院 — 6 月材料齐全，两表无差别",
        "哈尔滨市红十字妇产医院 — 仅 6 月完整；4、5 月见第一节",
        "哈尔滨工业大学医院 — 4、5 月已测；6 月见第一节",
    ])
    doc.add_paragraph()

    # 第五节
    add_para(doc, "五、其它医院 — 部分早期月份缺表（可选，非当前最急）", size=14, bold=True, color=COLOR_H2, space_after=6)
    add_para(doc, "以下医院 6 月主测已完成，但 1–3 月等缺少成对文件，暂不影响 6 月结论。", space_after=6)
    add_table(
        doc,
        ["医院名称", "缺少情况"],
        [
            ["黑龙江省中医药大学附属第三医院（电力）", "缺 1–3 月处理后账单"],
            ["道外区人民医院", "缺 1–3 月处理后账单"],
            ["黑龙江维多利亚妇产医院", "缺 1–3 月处理后（5 月见第二节）"],
            ["黑龙江九洲妇科医院", "缺 1–3 月处理后账单"],
            ["呼兰中医院", "缺 1–3 月处理后（5 月见第二节）"],
            ["哈尔滨冰城医疗美容医院", "缺 1 月处理后账单"],
            ["黑龙江中医药大学附属第二医院（南岗）", "缺 2 月成对账单"],
            ["黑龙江中医药大学附属第二医院（哈南分院）", "缺 2 月成对账单（5 月见第二节）"],
            ["南岗区妇产医院", "缺 4 月成对账单（5 月见第二节）"],
        ],
        [8, 8.5],
    )

    # 第六节
    add_para(doc, "六、尚未纳入本轮测试 — 仅缺「原始账单」（处理后已有）", size=14, bold=True, color=COLOR_H2, space_after=6)
    add_bullets(doc, [
        "奥兰医院",
        "哈尔滨市第一专科医院",
        "哈尔滨市骨伤科医院",
        "哈尔滨市南岗区人民医院（九院）",
        "黑龙江东大肛肠",
        "黑龙江省远东心脑血管医院",
        "南岗区先锋路社区卫生服务中心",
    ])
    add_para(doc, "需补：各账期原始 Excel；处理后不用重给。", space_after=14)

    # 转发话术
    add_para(doc, "可直接转发给客户的一段话", size=14, bold=True, color=COLOR_H2, space_after=6)
    quote = (
        "您好，特色账单测试需要每个账期两份 Excel：（1）计价前原始账单；（2）贵院确认后的处理后账单。"
        "两份必须是同一账期，且按发货单号、包名、包数能对应。\n\n"
        "请优先提供：\n"
        "① 红十字妇产医院 — 4 月、5 月【处理后】账单；\n"
        "② 工程大学医院 — 6 月 15 日～7 月 14 日【原始】账单；\n"
        "③ 第二节所列 10 家医院、对应月份的原始 + 处理后账单。\n\n"
        "有特色调价的行，处理后价格应与原始不同；无调价的行，两表价格应一致。"
        "结款函有则一并提供，不影响主测。谢谢！"
    )
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.5)
    p.paragraph_format.space_after = Pt(6)
    run = p.add_run(quote)
    set_run(run, size=10)
    run.italic = True

    doc.save(OUT)
    print(f"已生成：{OUT}")


if __name__ == "__main__":
    build()
