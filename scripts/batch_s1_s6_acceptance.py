#!/usr/bin/env python3
"""Batch S1–S6 acceptance for 测试用例/优先医院对齐TODO hospitals."""

from __future__ import annotations

import json
import sys
from dataclasses import asdict, dataclass, field
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CASE = ROOT / "测试用例"
DOCS_REG = ROOT / "docs" / "逐院需求登记表"
REPORT_JSON = TEST_CASE / "s1_s6_batch_report.json"
P06_SEED = ROOT / "backend/src/main/resources/billing-seeds/phase-batch-p0.6.json"

sys.path.insert(0, str(ROOT / "scripts"))
from analyze_test_case_excel import analyze_hospital, issue_count  # noqa: E402
from batch_june_price_reconciliation import (  # noqa: E402
    FOLDER_CODE_OVERRIDE,
    TODO_HOSPITALS,
    load_seed_profiles,
    resolve_profile,
)

# Hospitals with PDF special pricing — S3 needs productRules in seeds (may still be partial)
PDF_RULE_PENDING = {
    "道外区人民医院",
    "南岗区妇产医院",
    "哈尔滨市呼兰区第一人民医院",
    "三精肾病医院",
    "黑龙江中医药大学附属第一医院",
}

S6_V11_DONE = {
    "道外区人民医院",
    "南岗区妇产医院",
    "太平人民医院",
    "哈尔滨市呼兰区第一人民医院",
    "三精肾病医院",
    "黑龙江中医药大学附属第一医院",
    "黑龙江省医院（南岗院区）",
    "黑龙江省医院（香坊院区）",
    "哈尔滨工业大学医院",
    "哈尔滨工程大学医院",
    "哈尔滨市南岗区人民医院（九院）",
}


@dataclass
class StepResult:
    status: str  # pass | fail | skip | warn | partial
    detail: str = ""


@dataclass
class HospitalAcceptance:
    folder: str
    s1: StepResult = field(default_factory=lambda: StepResult("pending"))
    s2: StepResult = field(default_factory=lambda: StepResult("pending"))
    s3: StepResult = field(default_factory=lambda: StepResult("pending"))
    s4: StepResult = field(default_factory=lambda: StepResult("pending"))
    s5: StepResult = field(default_factory=lambda: StepResult("pending"))
    s6: StepResult = field(default_factory=lambda: StepResult("pending"))


def normalize_recon_status(raw: str) -> str:
    return raw.strip().strip("*").strip()


def parse_reconciliation_md() -> dict[str, str]:
    """后读的文件覆盖先读的；主报告应最后读以覆盖 L9 补充里的旧 Job。"""
    out: dict[str, str] = {}
    for name in ("批量6月系统对账结果-L9L61补充.md", "批量6月系统对账结果.md"):
        path = TEST_CASE / name
        if not path.exists():
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.startswith("|") or line.startswith("| 医院") or line.startswith("|------"):
                continue
            parts = [p.strip() for p in line.split("|") if p.strip()]
            if len(parts) >= 2 and parts[0] not in {"医院"}:
                hospital = parts[0]
                status = normalize_recon_status(parts[-1] if parts else "")
                if status:
                    out[hospital] = status
    return out


def check_s1(folder: str) -> StepResult:
    base = TEST_CASE / folder
    if not base.is_dir():
        return StepResult("fail", "测试用例目录不存在")
    raw_dir = base / "原始表格"
    proc_dir = base / "处理后表格"
    missing = []
    if not raw_dir.is_dir():
        missing.append("原始表格/")
    if not proc_dir.is_dir():
        missing.append("处理后表格/")
    if missing:
        return StepResult("fail", "缺: " + ", ".join(missing))
    raw_n = len(list(raw_dir.glob("*.xlsx"))) + len(list(raw_dir.glob("*.xls")))
    proc_n = len(list(proc_dir.glob("*.xlsx"))) + len(list(proc_dir.glob("*.xls")))
    if raw_n == 0 and proc_n == 0:
        return StepResult("fail", "两目录均无 xlsx")
    if raw_n == 0:
        return StepResult("warn", f"处理后 {proc_n} 个；原始 0（仅 pass_zero 场景需登记）")
    if proc_n == 0:
        return StepResult("fail", f"原始 {raw_n} 个；处理后 0")
    return StepResult("pass", f"原始 {raw_n} · 处理后 {proc_n}")


def check_s2(folder: str, analysis_date: str) -> StepResult:
    base = TEST_CASE / folder
    try:
        report = analyze_hospital(base)
    except Exception as exc:
        return StepResult("fail", str(exc))
    md_path = base / "数据问题分析.md"
    csv_path = base / "数据问题清单.csv"
    from analyze_test_case_excel import render_markdown, write_csv

    md_path.write_text(render_markdown(report, analysis_date), encoding="utf-8")
    write_csv(report, csv_path)
    if report.failed:
        return StepResult("fail", report.error or "分析失败")
    n = issue_count(report)
    return StepResult("pass", f"已生成 · 差异 {n} 条")


def check_s3(folder: str, profiles: dict, enabled: set[str]) -> StepResult:
    prof = resolve_profile(folder, profiles)
    if not prof or not prof.code:
        return StepResult("fail", "种子中未解析到 customer code")
    code = prof.code
    if folder in FOLDER_CODE_OVERRIDE:
        code = FOLDER_CODE_OVERRIDE[folder]
    in_p06 = code in enabled
    rules_n = len(prof.product_rules or [])
    parts = [f"code={code}", f"P0.6={'是' if in_p06 else '否'}", f"productRules={rules_n}"]
    if folder in PDF_RULE_PENDING and rules_n == 0:
        return StepResult("partial", "；".join(parts) + " · PDF 明细待写入规则")
    if not in_p06 and folder not in {"哈尔滨市南岗区人民医院（九院）"}:
        return StepResult("warn", "；".join(parts) + " · 未在 P0.6 enable 列表")
    if rules_n == 0 and prof.pricing_mode in ("special_only", "hybrid"):
        return StepResult("partial", "；".join(parts) + " · 无 productRules")
    return StepResult("pass", "；".join(parts))


def check_s4(folder: str, recon: dict[str, str]) -> StepResult:
    st = recon.get(folder, "")
    if not st:
        return StepResult("skip", "无 6 月批量对账记录（需 docker backend 重跑 batch_june_system_test）")
    if st.startswith("pass"):
        return StepResult("pass", st)
    if "fail" in st:
        return StepResult("fail", st)
    return StepResult("warn", st)


def ensure_s5(folder: str) -> StepResult:
    base = TEST_CASE / folder
    path = base / "纠错测试记录.md"
    template = f"""# {folder} — 纠错能力测试（S5）

> 自动生成骨架 · {date.today().isoformat()} · 需在开发环境对账 Job 上手工或 API 执行

| # | 用例 | 操作 | 预期 | 实际 | 状态 |
|---|------|------|------|------|:----:|
| 1 | 错误单价 | 导入后改一行单价 +10% | 产生 warning / 异常明细 | 待测 | ⬜ |
| 2 | 错误包名 | 改包名使规则不匹配 | warning 或计价回退标准价 | 待测 | ⬜ |
| 3 | 关 billing | 客户关特色账单后再导入 | 按标准价或拒绝 | 待测 | ⬜ |

**结论：** 批量流水线仅落盘用例表；三项均未自动执行（无运行中 backend）。
"""
    if path.exists():
        text = path.read_text(encoding="utf-8")
        if "待测" in text and "✅" not in text:
            return StepResult("partial", "纠错测试记录.md 存在 · 用例未执行")
        if "✅" in text:
            return StepResult("pass", "记录含已通过用例")
        return StepResult("partial", "记录已存在")
    path.write_text(template, encoding="utf-8")
    return StepResult("partial", "已创建纠错测试记录.md · 待 API/手工执行")


def check_s6(folder: str) -> StepResult:
    doc = DOCS_REG / f"{folder}.md"
    if folder not in S6_V11_DONE:
        if not doc.exists():
            return StepResult("fail", "无对应登记表 md")
        text = doc.read_text(encoding="utf-8")
        if "v1.1" in text or "2026-07-22" in text:
            return StepResult("pass", "登记表已存在且较新")
        return StepResult("partial", "登记表存在 · 未标 v1.1/2026-07-22")
    if not doc.exists():
        return StepResult("fail", "批次院缺 md")
    text = doc.read_text(encoding="utf-8")
    if "v1.1" in text and "2026-07-22" in text:
        return StepResult("pass", "v1.1 · 2026-07-22")
    return StepResult("partial", "md 存在但版本批次不完整")


def icon(s: StepResult) -> str:
    m = {
        "pass": "✅",
        "fail": "🚫",
        "skip": "⏭",
        "warn": "🔄",
        "partial": "🔄",
        "pending": "⬜",
    }
    return m.get(s.status, "⬜")


def main() -> int:
    analysis_date = date.today().isoformat()
    enabled = set(json.loads(P06_SEED.read_text(encoding="utf-8")).get("enableBilling") or [])
    profiles = load_seed_profiles()
    recon = parse_reconciliation_md()

    results: list[HospitalAcceptance] = []
    for folder in TODO_HOSPITALS:
        ha = HospitalAcceptance(folder=folder)
        ha.s1 = check_s1(folder)
        ha.s2 = check_s2(folder, analysis_date)
        ha.s3 = check_s3(folder, profiles, enabled)
        ha.s4 = check_s4(folder, recon)
        ha.s5 = ensure_s5(folder)
        ha.s6 = check_s6(folder)
        results.append(ha)
        print(
            f"{folder}: S1={ha.s1.status} S2={ha.s2.status} S3={ha.s3.status} "
            f"S4={ha.s4.status} S5={ha.s5.status} S6={ha.s6.status}",
            flush=True,
        )

    payload = {
        "date": analysis_date,
        "hospitals": [{**{"folder": r.folder}, **{f"s{k}": asdict(getattr(r, f"s{k}")) for k in range(1, 7)}} for r in results],
    }
    REPORT_JSON.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nWrote {REPORT_JSON}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
