# -*- coding: utf-8 -*-
"""共性排查：扫描全部严格医院 raw 材料中的多数字包名形态。

分类：
A  含「气腹针N」（紧凑写法）且全名数字段 ≥2 —— 0902 修复覆盖的形态
B  含非气腹「针N」且针前有 ≥2 个「汉字+数字」段 —— extractLastNumber 丢段缺陷面
C  含「气腹针-N」横线形态（不触发拆分，对照）
输出仅聚合结果，不 dump 原始行。
"""
import glob
import re
import sys
from collections import defaultdict

import openpyxl

HAN = r'[\u4e00-\u9fff]'
SEG = re.compile(r'[\u4e00-\u9fff]+(\d+)')
NEEDLE = re.compile(r'针(\d+)')
VERESS_COMPACT = re.compile(r'气腹针(\d+)')
VERESS_HYPHEN = re.compile(r'气腹针[-－](\d+)')

def segs(text):
    return [(m.group(0), int(m.group(1))) for m in SEG.finditer(text)]

def classify(name):
    """返回 (类别, 细节) 或 None。"""
    if not name or len(name) > 60 or '/' not in name and 'Z' not in name and 'z' not in name:
        # 包名通常带 /Z码；放宽：含针或≥2数字段即可
        pass
    out = []
    all_seg = segs(name)
    if VERESS_COMPACT.search(name):
        if len(all_seg) >= 2:
            out.append(('A', f'气腹针紧凑+多数字段 segs={all_seg}'))
        else:
            out.append(('A1', f'气腹针紧凑单段 segs={all_seg}'))
    if VERESS_HYPHEN.search(name):
        out.append(('C', '气腹针横线形态'))
    # B：非气腹针N，且针前≥2段
    for m in NEEDLE.finditer(name):
        start = m.start()
        if start >= 2 and name[start-2:start] == '气腹':
            continue
        before = name[:start]
        before_segs = segs(before)
        if len(before_segs) >= 2:
            out.append(('B', f'非气腹针{m.group(1)} 针前段={before_segs}'))
        break
    return out

def scan_file(path, stats, samples):
    try:
        wb = openpyxl.load_workbook(path, read_only=True)
    except Exception:
        return
    for ws in wb.worksheets:
        for row in ws.iter_rows(values_only=True):
            for cell in row:
                if not isinstance(cell, str):
                    continue
                cell = cell.strip()
                if not cell or len(cell) > 80:
                    continue
                if '针' not in cell and len(segs(cell)) < 2:
                    continue
                for cat, detail in classify(cell) or []:
                    if cell not in samples[cat]:
                        samples[cat][cell] = detail

def main():
    roots = sys.argv[1:] or ['测试用例']
    stats = defaultdict(dict)
    samples = defaultdict(dict)
    files = []
    for root in roots:
        files += glob.glob(f'{root}/**/原始表格/*.xlsx', recursive=True)
        files += glob.glob(f'{root}/**/处理后表格/*.xlsx', recursive=True)
    print(f'scanning {len(files)} xlsx files...')
    for f in files:
        scan_file(f, stats, samples)
    for cat in ('A', 'A1', 'B', 'C'):
        items = samples.get(cat, {})
        print(f'\n== 类别 {cat}: {len(items)} 种包名')
        for name, detail in sorted(items.items()):
            print(f'  {name}   [{detail}]')

if __name__ == '__main__':
    main()
