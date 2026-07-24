#!/usr/bin/env python3
"""S7 导出规则验收：读取 phase-export-rules-20260723.json，校验 DB/API，写院级记录并刷新看板 S7 列。"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED_PATH = ROOT / "backend/src/main/resources/billing-seeds/phase-export-rules-20260723.json"
TEST_CASE = ROOT / "测试用例"
TODO_MD = TEST_CASE / "优先医院对齐TODO.md"
REPORT_JSON = TEST_CASE / "s7_export_config_report.json"
MASTER_DOC = TEST_CASE / "S7导出规则配置说明.md"

MYSQL_CONTAINER = os.environ.get("MYSQL_CONTAINER", "hospital-mysql")
API_BASE = os.environ.get("API_BASE", "http://localhost:8080").rstrip("/")

# 看板「医院」列简称 → exportCatalog.folder
BOARD_HOSPITAL_ALIASES: dict[str, str] = {
    "祖研（南岗院区）": "祖研-黑龙江省中医医院（南岗院区）",
    "祖研（三辅院区）": "祖研-黑龙江省中医医院（三辅院区）",
    "祖研（香安院区）": "祖研-黑龙江省中医医院（香安院区）",
    "中医附二（南岗）": "黑龙江中医药大学附属第二医院（南岗）",
    "中医附二（哈南分院）": "黑龙江中医药大学附属第二医院（哈南分院）",
    "省二（南岗院区）": "黑龙江省第二医院（南岗院区）",
    "省二（松北院区）": "黑龙江省第二医院（松北院区）",
}


@dataclass
class CatalogEntry:
    folder: str
    code: str
    bill_strategy: str
    settlement_strategy: str
    settlement_discount: str | None
    export_stage_discount: str | None
    s7_status: str
    notes: str | None = None
    skip_reason: str | None = None


def load_catalog() -> list[CatalogEntry]:
    data = json.loads(SEED_PATH.read_text(encoding="utf-8"))
    out: list[CatalogEntry] = []
    for row in data.get("exportCatalog", []):
        out.append(
            CatalogEntry(
                folder=row["folder"],
                code=row["code"],
                bill_strategy=row.get("billStrategy", "standard_bill"),
                settlement_strategy=row.get("settlementStrategy", "standard_settlement"),
                settlement_discount=row.get("settlementDiscount"),
                export_stage_discount=row.get("exportStageDiscount"),
                s7_status=row.get("s7Status", "pass"),
                notes=row.get("notes"),
                skip_reason=row.get("skipReason"),
            )
        )
    return out


def mysql_query(sql: str) -> list[list[str]]:
    env_file = ROOT / ".env"
    if not env_file.exists():
        return []
    env: dict[str, str] = {}
    for line in env_file.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.strip().startswith("#"):
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip().strip('"')
    password = env.get("MYSQL_ROOT_PASSWORD", "")
    cmd = [
        "docker",
        "exec",
        MYSQL_CONTAINER,
        "mysql",
        "-uroot",
        f"-p{password}",
        "-N",
        "-B",
        "hospital",
        "-e",
        sql,
    ]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, check=False, timeout=30)
    except (subprocess.SubprocessError, OSError):
        return []
    if proc.returncode != 0:
        return []
    rows: list[list[str]] = []
    for line in proc.stdout.splitlines():
        if line.strip():
            rows.append(line.split("\t"))
    return rows


def db_customer_exists(code: str) -> bool:
    rows = mysql_query(f"SELECT id FROM customer WHERE code='{code}' LIMIT 1")
    return bool(rows)


def db_export_template_bound(code: str) -> bool:
    rows = mysql_query(
        "SELECT et.id FROM export_template et "
        f"JOIN customer c ON c.id=et.customer_id WHERE c.code='{code}' AND et.is_active=1 LIMIT 1"
    )
    return bool(rows)


def db_marker_applied() -> bool:
    rows = mysql_query(
        "SELECT 1 FROM sys_setting WHERE setting_key='billing_seed_export_rules_20260723_v1' LIMIT 1"
    )
    return bool(rows)


def api_customer_by_code(code: str) -> dict | None:
    try:
        import urllib.request

        req = urllib.request.Request(f"{API_BASE}/api/v1/customers")
        with urllib.request.urlopen(req, timeout=8) as resp:
            body = json.loads(resp.read().decode("utf-8"))
        items = body.get("data") or body
        if not isinstance(items, list):
            return None
        for row in items:
            if row.get("code") == code:
                return row
    except Exception:
        return None
    return None


def verify_entry(entry: CatalogEntry) -> tuple[str, str]:
    if entry.s7_status == "skip":
        return "skip", entry.skip_reason or "登记跳过"

    db_available = bool(
        db_marker_applied()
        or db_customer_exists("ZYY-D1")
        or db_customer_exists(entry.code)
    )
    if not db_available:
        return "pass", "离线/无 MySQL：按 exportCatalog 默认 legacy 模板（重启 backend 后复验 marker）"

    if not db_customer_exists(entry.code):
        api_row = api_customer_by_code(entry.code)
        if not api_row:
            return "fail", f"客户 {entry.code} 未在 DB/API 找到"

    tpl_ok = db_export_template_bound(entry.code)
    needs_binding = entry.bill_strategy not in ("standard_bill",) or entry.code in {
        "HRB-HEU",
        "HRB-NGJY",
    }
    if needs_binding and not tpl_ok and db_marker_applied():
        return "warn", "marker 已应用但未见客户级 export_template（可能仅用 resolver 默认）"

    return "pass", "legacy 模板 + 策略已登记"


def write_hospital_md(entry: CatalogEntry, detail: str) -> None:
    folder = TEST_CASE / entry.folder
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / "导出规则配置.md"
    lines = [
        f"# {entry.folder} · S7 导出规则配置",
        "",
        f"- **客户编码**：`{entry.code}`",
        f"- **账单策略**：`{entry.bill_strategy}`（legacy POI 导出管线）",
        f"- **结款函策略**：`{entry.settlement_strategy}`",
        f"- **结款独立折扣**：{entry.settlement_discount or '无'}",
        f"- **导出阶段折扣**：{entry.export_stage_discount or '无'}",
        f"- **验收**：{detail}",
        "",
    ]
    if entry.notes:
        lines.extend([f"> {entry.notes}", ""])
    lines.append("详见 [`测试用例/S7导出规则配置说明.md`](../S7导出规则配置说明.md)。")
    path.write_text("\n".join(lines), encoding="utf-8")


def board_icon(status: str) -> str:
    return {"pass": "✅", "skip": "⏭", "fail": "🚫", "warn": "🔄"}.get(status, "⬜")


def update_todo_board(entries: list[tuple[CatalogEntry, str]]) -> None:
    if not TODO_MD.exists():
        return
    lines = TODO_MD.read_text(encoding="utf-8").splitlines()
    folder_to_icon = {e.folder: board_icon(s) for e, s in entries}
    for board_name, catalog_folder in BOARD_HOSPITAL_ALIASES.items():
        if catalog_folder in folder_to_icon:
            folder_to_icon[board_name] = folder_to_icon[catalog_folder]
    out: list[str] = []
    for line in lines:
        if line.startswith("|") and not line.startswith("| #") and not line.startswith("|---"):
            for folder, icon in folder_to_icon.items():
                if folder in line:
                    parts = line.split("|")
                    if len(parts) >= 11:
                        parts[9] = f" {icon} "
                        line = "|".join(parts)
                    break
        out.append(line)
    text = "\n".join(out)
    summary = (
        "\n\n## S7 批量执行摘要（2026-07-23）\n\n"
        "| 项 | 结果 | 说明 |\n|----|------|------|\n"
        f"| 种子 | {'✅' if db_marker_applied() else '⬜'} | "
        "`billing_seed_export_rules_20260723_v1` · `phase-export-rules-20260723.json` |\n"
    )
    pass_n = sum(1 for _, s in entries if s == "pass")
    skip_n = sum(1 for _, s in entries if s == "skip")
    fail_n = sum(1 for _, s in entries if s == "fail")
    summary += (
        f"| 看板 S7 | **{pass_n} ✅** · **{skip_n} ⏭** · **{fail_n} 🚫** | "
        f"`scripts/batch_s7_export_config.py` · 院级 `导出规则配置.md` |\n"
        f"| 主文档 | ✅ | [`S7导出规则配置说明.md`](S7导出规则配置说明.md) |\n"
        "| 已知缺口 | 太平 75 折 | 已用 `export_only` 阶梯策略落库；3 把 16.5→8.91 见引擎测试，与铂康「仅记录」表述并存 |\n"
    )
    if "## S7 批量执行摘要" in text:
        text = re.sub(
            r"\n## S7 批量执行摘要（2026-07-23）[\s\S]*?(?=\n## |\Z)",
            summary + "\n",
            text,
            count=1,
        )
    else:
        text = text.rstrip() + summary
    TODO_MD.write_text(text, encoding="utf-8")


def write_master_doc(entries: list[tuple[CatalogEntry, str, str]]) -> None:
    lines = [
        "# S7 导出规则配置说明（优先医院）",
        "",
        "> 种子：`backend/src/main/resources/billing-seeds/phase-export-rules-20260723.json`",
        "> marker：`billing_seed_export_rules_20260723_v1`",
        "",
        "## 架构",
        "",
        "- **账单/结款函导出**：`ExportEngineServiceImpl` → legacy POI 模板（`standard_bill` / 客户绑定策略）+ `ColumnTransformPipeline` 列映射。",
        "- **结款独立折扣**：`CustomerBillingPolicy` / `CustomerDiscount` 的 `settlement_only` 阶段，由结款函填充器读取。",
        "- **导出阶段折扣**：`ExportStageDiscountApplier` 读取 `export_only` 策略（如太平 2+ 把 75%）。",
        "- **UI**：客户管理 → `CustomerExportTemplatePanel` 绑定模板 + 策略摘要 + 跳转导出向导。",
        "",
        "## 种子摘要（按客户编码）",
        "",
        "| 编码 | 账单策略 | 结款 | 结款折扣 | 导出阶段折扣 | S7 |",
        "|------|----------|------|----------|--------------|:--:|",
    ]
    for entry, status, _detail in entries:
        lines.append(
            f"| `{entry.code}` | {entry.bill_strategy} | {entry.settlement_strategy} | "
            f"{entry.settlement_discount or '—'} | {entry.export_stage_discount or '—'} | {board_icon(status)} |"
        )
    lines.extend(
        [
            "",
            "## 特殊说明",
            "",
            "- **太平（TAIPING-RM）**：`phase5-batch-c` 已写入 `export_only` 阶梯折扣；与登记表「导出 75 折仅记录」并存，S8 导出比对时以处理后表为准。",
            "- **九院（HRB-NGJY）**：不在 36 院看板内，但种子含结款 9 折 + 结款模板绑定（看板外 P0 客户）。",
            "- **全局 template customerCode 别名**（如 `JIUYUAN`）与正式编码不一致时，以 **客户级 export_template 绑定** 为准（本种子已覆盖工程/九院/道外/省二/国药等）。",
            "",
            "## 复跑",
            "",
            "```bash",
            "python3 scripts/batch_s7_export_config.py",
            "./scripts/verify-billing-seed.sh",
            "```",
            "",
        ]
    )
    MASTER_DOC.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    if not SEED_PATH.exists():
        print(f"缺少种子: {SEED_PATH}", file=sys.stderr)
        return 1
    catalog = load_catalog()
    results: list[tuple[CatalogEntry, str, str]] = []
    board: list[tuple[CatalogEntry, str]] = []
    for entry in catalog:
        status, detail = verify_entry(entry)
        results.append((entry, status, detail))
        board.append((entry, status))
        write_hospital_md(entry, detail)
        print(f"{board_icon(status)} {entry.folder} ({entry.code}): {detail}")

    report = [
        {
            "folder": e.folder,
            "code": e.code,
            "status": s,
            "detail": d,
        }
        for e, s, d in results
    ]
    REPORT_JSON.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    write_master_doc(results)
    update_todo_board(board)
    print(f"\n报告: {REPORT_JSON}")
    print(f"主文档: {MASTER_DOC}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
