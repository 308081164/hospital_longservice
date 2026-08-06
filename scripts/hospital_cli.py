#!/usr/bin/env python3
"""Unified CLI for deploy smoke, billing verify, S8/S4 regression."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
TEST_CASE = ROOT / "测试用例"
STABLE_JOB_MAP = TEST_CASE / "job_baseline_stable.json"
PROD_JOB_MAP = TEST_CASE / "job_baseline_prod.json"

sys.path.insert(0, str(SCRIPTS))
from lib.api_client import ApiClient, ApiError, configure_client, get_client  # noqa: E402
from rules_compare import (  # noqa: E402
    PARITY_REPORT,
    format_human,
    run_rules_compare,
)
from rules_spot_check import (  # noqa: E402
    MANIFEST_HASH_KEY,
    format_spot_check_human,
    format_verify_deploy_human,
    run_spot_check,
    run_verify_deploy,
)


HRB_CJ_HOSPITAL = "哈尔滨长健医院"
HRB_CJ_DIR = TEST_CASE / HRB_CJ_HOSPITAL
HRB_CJ_SEED_MARKERS = (
    "billing_seed_hrb_cj_dedup_customer_20260731_v1",
    "billing_seed_hrb_cj_default_rule_20260731_v1",
    "billing_seed_hrb_cj_pricing_fixed_20260731_v1",
)
SIMULATE_SAMPLE_ROW = {
    "packName": "手术包（二）",
    "sheetName": "手术室",
    "instrumentCount": 43,
    "unitPrice": 231,
    "totalPrice": 231,
    "packType": "器械包(ZSD)",
    "packageMaterial": "高温灭菌无纺布60*60",
    "temperature": "HT",
}


def row_field(row: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        val = row.get(key)
        if val is not None:
            return val
    return None


def mysql_query(deploy_path: Path, sql: str) -> str | None:
    script = deploy_path / "deploy/mysql-hospital-cli.sh"
    if not script.is_file():
        script = ROOT / "deploy/mysql-hospital-cli.sh"
    if not script.is_file():
        return None
    db = os.environ.get("MYSQL_DATABASE", "hospital")
    env = os.environ.copy()
    env.setdefault("DEPLOY_PATH", str(deploy_path))
    try:
        out = subprocess.check_output(
            ["bash", str(script), "--exec-root", "-N", "-e", sql, db],
            text=True,
            cwd=str(deploy_path if (deploy_path / "deploy").is_dir() else ROOT),
            env=env,
            stderr=subprocess.DEVNULL,
        )
        lines = [line.strip() for line in out.splitlines() if line.strip()]
        return lines[-1] if lines else ""
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None


def mysql_manifest_hash(deploy_path: Path) -> str | None:
    return mysql_query(
        deploy_path,
        f"SELECT setting_value FROM sys_setting WHERE setting_key='{MANIFEST_HASH_KEY}' LIMIT 1",
    )


def mysql_exec(deploy_path: Path, sql: str) -> bool | None:
    script = deploy_path / "deploy/mysql-hospital-cli.sh"
    if not script.is_file():
        script = ROOT / "deploy/mysql-hospital-cli.sh"
    if not script.is_file():
        return None
    db = os.environ.get("MYSQL_DATABASE", "hospital")
    env = os.environ.copy()
    env.setdefault("DEPLOY_PATH", str(deploy_path))
    try:
        subprocess.check_output(
            ["bash", str(script), "--exec-root", "-e", sql, db],
            text=True,
            cwd=str(deploy_path if (deploy_path / "deploy").is_dir() else ROOT),
            env=env,
            stderr=subprocess.DEVNULL,
        )
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None


def mysql_seed_markers(deploy_path: Path, markers: tuple[str, ...]) -> dict[str, bool] | None:
    script = deploy_path / "deploy/mysql-hospital-cli.sh"
    if not script.is_file():
        script = ROOT / "deploy/mysql-hospital-cli.sh"
    if not script.is_file():
        return None
    db = os.environ.get("MYSQL_DATABASE", "hospital")
    in_list = ", ".join(f"'{m}'" for m in markers)
    sql = f"SELECT setting_key FROM sys_setting WHERE setting_key IN ({in_list})"
    env = os.environ.copy()
    env.setdefault("DEPLOY_PATH", str(deploy_path))
    try:
        out = subprocess.check_output(
            ["bash", str(script), "--exec-root", "-N", "-e", sql, db],
            text=True,
            cwd=str(deploy_path if (deploy_path / "deploy").is_dir() else ROOT),
            env=env,
            stderr=subprocess.DEVNULL,
        )
        found = {line.strip() for line in out.splitlines() if line.strip()}
        return {m: m in found for m in markers}
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None


def mysql_enable_customer_billing(deploy_path: Path, code: str) -> bool | None:
    return mysql_exec(deploy_path, f"UPDATE customer SET billing_enabled=1 WHERE code='{code}'")


HRB_CJ_ENSURE_RULES: list[dict[str, Any]] = [
    {
        "ruleType": "PRICE_PER_INSTRUMENT",
        "name": "手术包5.5元/件",
        "priority": 10,
        "price": 5.5,
        "keywords": ["手术包"],
        "temperature": "HT",
        "skipPackaging": True,
        "skipDiscount": True,
        "isActive": True,
    },
    {
        "ruleType": "FIXED_PRICE",
        "name": "长健敷料包W12050",
        "priority": 2,
        "price": 35,
        "keywords": ["敷料包/W12050"],
        "skipPackaging": True,
        "isActive": True,
    },
    {
        "ruleType": "FIXED_PRICE",
        "name": "长健硅胶珠子22",
        "priority": 2,
        "price": 22,
        "keywords": ["硅胶珠子7号"],
        "skipPackaging": True,
        "isActive": True,
    },
]


def find_surgical_pack_row(rows: list[dict[str, Any]]) -> dict[str, Any] | None:
    for row in rows:
        pack_name = str(row_field(row, "packName") or "")
        if "手术包" in pack_name and "（二）" in pack_name:
            return row
    for row in rows:
        pack_name = str(row_field(row, "packName") or "")
        if pack_name == "手术包（二）":
            return row
    return None


def update_prod_job_map(hospital: str, job_id: int) -> None:
    payload: dict[str, Any] = {"version": "1", "jobs": {}}
    if PROD_JOB_MAP.is_file():
        payload = json.loads(PROD_JOB_MAP.read_text(encoding="utf-8"))
    jobs = payload.setdefault("jobs", {})
    jobs[hospital] = job_id
    payload["updated"] = time.strftime("%Y-%m-%d")
    PROD_JOB_MAP.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def find_hrb_cj_june_raw() -> tuple[Path | None, str]:
    raw_dir = HRB_CJ_DIR / "原始表格"
    if not raw_dir.is_dir():
        return None, "缺少原始表格目录"
    preferred = raw_dir / "哈尔滨长健医院6月.xlsx"
    if preferred.is_file():
        return preferred, "6月"
    for path in sorted(raw_dir.iterdir()):
        if path.suffix.lower() in {".xlsx", ".xls"}:
            return path, path.name
    return None, "无 xlsx 原始账单"


def import_reconciliation_bill(client: ApiClient, hospital: str, file_path: Path) -> dict[str, Any]:
    data = client.post_multipart(
        "/api/hospital-reconciliations/import",
        {
            "rule_id": "1",
            "operator_name": "billing-verify-cli",
            "hospital_name": hospital,
        },
        "source_file",
        file_path,
    )
    payload = data.get("data")
    return payload if isinstance(payload, dict) else {}


def enable_hrb_cj_billing(client: ApiClient, customer: dict[str, Any], deploy_path: Path) -> bool:
    if mysql_enable_customer_billing(deploy_path, "HRB-CJ"):
        return True
    customer_id = int(row_field(customer, "id"))
    product_rules = customer.get("product_rules") or customer.get("productRules") or []
    body = {
        "code": row_field(customer, "code"),
        "canonicalName": row_field(customer, "canonical_name", "canonicalName"),
        "status": row_field(customer, "status") or "active",
        "defaultRuleId": int(row_field(customer, "default_rule_id", "defaultRuleId") or 1),
        "billingEnabled": True,
        "billingPricingMode": row_field(customer, "billing_pricing_mode", "billingPricingMode") or "standard",
        "productRules": product_rules,
    }
    client.update_customer(customer_id, body)
    return True


def ensure_hrb_cj_product_rules(client: ApiClient, customer_id: int) -> list[str]:
    existing = client.product_rules(customer_id)
    existing_names = {str(r.get("name") or "") for r in existing}
    has_surgical = any("手术包" in n for n in existing_names) or any(
        str(r.get("rule_type") or r.get("ruleType") or "") == "PRICE_PER_INSTRUMENT"
        and any("手术包" in k for k in (r.get("keywords") or []))
        for r in existing
    )
    created: list[str] = []
    for spec in HRB_CJ_ENSURE_RULES:
        if spec["name"] in existing_names:
            continue
        if spec["name"] == "手术包5.5元/件" and has_surgical:
            continue
        try:
            client.create_product_rule(customer_id, spec)
            created.append(str(spec["name"]))
        except ApiError as exc:
            if "已配置" in str(exc):
                continue
            raise
    return created


def run_billing_verify(
    client: ApiClient,
    *,
    profile: str,
    reimport: bool,
    update_prod_map: bool,
) -> CliReport:
    report = CliReport("billing verify", profile, client.mode, client.api_base, time.time())
    deploy_path = Path(os.environ.get("DEPLOY_PATH", ROOT))

    try:
        health = client.health()
        report.add(
            StepResult(
                "V0_health_login",
                "V0",
                health.get("code") == 200,
                str(health.get("msg") or "ok"),
            )
        )
        client.login(force=True)
    except Exception as exc:
        report.add(StepResult("V0_health_login", "V0", False, str(exc)))
        report.finished_at = time.time()
        return report

    changjian = client.customer_by_code("CHANGJIAN")
    cj_status = str(row_field(changjian or {}, "status") or "").lower()
    report.add(
        StepResult(
            "V1_changjian_inactive",
            "V1",
            changjian is not None and cj_status == "inactive",
            f"CHANGJIAN status={cj_status or 'missing'}",
            {"customer": changjian},
        )
    )

    hrb_cj = client.customer_by_code("HRB-CJ")
    hrb_id = row_field(hrb_cj or {}, "id")
    billing_enabled = row_field(hrb_cj or {}, "billingEnabled", "billing_enabled")

    if reimport and hrb_cj and billing_enabled is not True:
        try:
            enable_hrb_cj_billing(client, hrb_cj, deploy_path)
            hrb_cj = client.customer_by_code("HRB-CJ") or hrb_cj
            billing_enabled = row_field(hrb_cj, "billingEnabled", "billing_enabled")
            report.add(
                StepResult(
                    "V1b_enable_billing",
                    "V1",
                    billing_enabled is True,
                    f"已启用 HRB-CJ 特色账单 billing={billing_enabled}",
                )
            )
        except Exception as exc:
            report.add(StepResult("V1b_enable_billing", "V1", False, str(exc)))

    if reimport and hrb_id:
        try:
            created = ensure_hrb_cj_product_rules(client, int(hrb_id))
            detail = "规则齐全" if not created else f"已补建: {', '.join(created)}"
            report.add(
                StepResult(
                    "V2b_ensure_product_rules",
                    "V2",
                    True,
                    detail,
                    {"created": created},
                )
            )
        except Exception as exc:
            report.add(StepResult("V2b_ensure_product_rules", "V2", False, str(exc)))

    default_rule_id = row_field(hrb_cj or {}, "defaultRuleId", "default_rule_id")
    pricing_mode = row_field(hrb_cj or {}, "billingPricingMode", "billing_pricing_mode")
    config_ok = (
        hrb_cj is not None
        and billing_enabled is True
        and int(default_rule_id or 0) == 1
        and str(pricing_mode or "") == "standard"
    )
    report.add(
        StepResult(
            "V2_hrb_cj_config",
            "V2",
            config_ok,
            f"HRB-CJ billing={billing_enabled} default_rule_id={default_rule_id} mode={pricing_mode}",
            {"customer_id": hrb_id},
        )
    )

    surgical_rule_name = ""
    if hrb_id:
        try:
            rules = client.product_rules(int(hrb_id))
            surgical = [
                r
                for r in rules
                if "手术包" in str(r.get("name") or "")
                or (
                    str(r.get("rule_type") or r.get("ruleType") or "") == "PRICE_PER_INSTRUMENT"
                    and any("手术包" in k for k in (r.get("keywords") or []))
                )
            ]
            surgical_rule_name = str(surgical[0].get("name")) if surgical else ""
            report.add(
                StepResult(
                    "V3_surgical_pack_rule",
                    "V3",
                    bool(surgical),
                    surgical_rule_name or f"未找到手术包5.5规则（共 {len(rules)} 条）",
                    {"rules": [r.get("name") for r in rules]},
                )
            )
        except Exception as exc:
            report.add(StepResult("V3_surgical_pack_rule", "V3", False, str(exc)))
    else:
        report.add(StepResult("V3_surgical_pack_rule", "V3", False, "HRB-CJ 不存在"))

    marker_status = mysql_seed_markers(deploy_path, HRB_CJ_SEED_MARKERS)
    if marker_status is None:
        report.add(
            StepResult(
                "V4_seed_markers",
                "V4",
                True,
                "跳过 MySQL seed marker 检查（无 mysql-hospital-cli.sh 或非部署机）",
            )
        )
    else:
        missing = [k for k, ok in marker_status.items() if not ok]
        report.add(
            StepResult(
                "V4_seed_markers",
                "V4",
                not missing,
                "全部存在" if not missing else f"缺失: {', '.join(missing)}",
                {"markers": marker_status},
            )
        )

    if hrb_id:
        try:
            sim = client.simulate_billing(
                customer_id=int(hrb_id),
                hospital_name=HRB_CJ_HOSPITAL,
                sample_row=SIMULATE_SAMPLE_ROW,
            )
            status = str(row_field(sim, "status") or "")
            diff = row_field(sim, "difference")
            pricing_rule = str(row_field(sim, "pricingRule", "pricing_rule") or "")
            try:
                diff_val = float(diff)
            except (TypeError, ValueError):
                diff_val = None
            sim_ok = (
                status == "warning"
                and diff_val is not None
                and abs(diff_val - 5.5) < 0.01
                and "手术包" in pricing_rule
            )
            report.add(
                StepResult(
                    "V5_simulate_231_warning",
                    "V5",
                    sim_ok,
                    f"status={status} diff={diff} rule={pricing_rule}",
                    {"simulate": sim},
                )
            )
        except Exception as exc:
            report.add(StepResult("V5_simulate_231_warning", "V5", False, str(exc)))
    else:
        report.add(StepResult("V5_simulate_231_warning", "V5", False, "HRB-CJ 不存在"))

    job_id: int | None = None
    if reimport:
        raw_path, label = find_hrb_cj_june_raw()
        if raw_path is None or not raw_path.is_file():
            report.add(StepResult("V6_reimport_june", "V6", False, f"缺少 6 月原始账单 ({label})"))
        else:
            try:
                job = import_reconciliation_bill(client, HRB_CJ_HOSPITAL, raw_path)
                job_id = int(job.get("id") or job.get("jobId") or job.get("job_id"))
                report.add(
                    StepResult(
                        "V6_reimport_june",
                        "V6",
                        True,
                        f"Job #{job_id} 导入 {raw_path.name} ({label})",
                        {"job_id": job_id, "file": str(raw_path)},
                    )
                )
            except Exception as exc:
                report.add(StepResult("V6_reimport_june", "V6", False, str(exc)))
    else:
        report.add(StepResult("V6_reimport_june", "V6", True, "跳过（未指定 --reimport）"))

    if reimport and job_id:
        try:
            rows = client.reconciliation_rows(job_id)
            golden = find_surgical_pack_row(rows)
            if golden is None:
                report.add(StepResult("V7_golden_row", "V7", False, "未找到手术包（二）行"))
            else:
                status = str(row_field(golden, "status") or "")
                expected = row_field(golden, "expectedUnitPrice", "expected_unit_price")
                pricing_rule = str(row_field(golden, "pricingRule", "pricing_rule") or "")
                try:
                    expected_val = float(expected)
                except (TypeError, ValueError):
                    expected_val = None
                golden_ok = (
                    status == "unchanged"
                    and expected_val is not None
                    and abs(expected_val - 236.5) < 0.01
                    and "手术包" in pricing_rule
                )
                report.add(
                    StepResult(
                        "V7_golden_row",
                        "V7",
                        golden_ok,
                        f"status={status} expected={expected} rule={pricing_rule}",
                        {"row": golden},
                    )
                )
        except Exception as exc:
            report.add(StepResult("V7_golden_row", "V7", False, str(exc)))

        if update_prod_map and job_id:
            try:
                update_prod_job_map(HRB_CJ_HOSPITAL, job_id)
                report.add(
                    StepResult(
                        "V8_update_prod_map",
                        "V8",
                        True,
                        f"job_baseline_prod.json → {HRB_CJ_HOSPITAL}={job_id}",
                        {"job_id": job_id},
                    )
                )
            except Exception as exc:
                report.add(StepResult("V8_update_prod_map", "V8", False, str(exc)))
        elif update_prod_map:
            report.add(StepResult("V8_update_prod_map", "V8", False, "无 Job ID，未更新 prod map"))
    elif update_prod_map:
        report.add(StepResult("V8_update_prod_map", "V8", True, "跳过（需 --reimport 成功）"))

    report.finished_at = time.time()
    return report


def cmd_billing_verify(args: argparse.Namespace) -> int:
    client = resolve_client(args)
    report = run_billing_verify(
        client,
        profile=args.profile,
        reimport=args.reimport,
        update_prod_map=args.update_prod_map,
    )
    print_report(report, as_json=args.json)
    return 0 if report.ok else 1


@dataclass
class StepResult:
    name: str
    level: str
    ok: bool
    detail: str = ""
    data: dict[str, Any] = field(default_factory=dict)


@dataclass
class CliReport:
    command: str
    profile: str
    mode: str
    api_base: str
    started_at: float
    finished_at: float = 0.0
    ok: bool = True
    steps: list[StepResult] = field(default_factory=list)

    def add(self, step: StepResult) -> None:
        self.steps.append(step)
        if not step.ok:
            self.ok = False

    def to_dict(self) -> dict[str, Any]:
        return {
            "command": self.command,
            "profile": self.profile,
            "mode": self.mode,
            "api_base": self.api_base,
            "started_at": self.started_at,
            "finished_at": self.finished_at,
            "duration_sec": round(self.finished_at - self.started_at, 2),
            "ok": self.ok,
            "steps": [asdict(s) for s in self.steps],
        }


def load_dotenv() -> None:
    env_path = os.environ.get("DEPLOY_PATH", ROOT)
    dotenv = Path(env_path) / ".env"
    if not dotenv.is_file():
        dotenv = ROOT / ".env"
    if not dotenv.is_file():
        return
    for line in dotenv.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, val = line.partition("=")
        key = key.strip()
        val = val.strip().strip("'\"")
        os.environ.setdefault(key, val)


def resolve_client(args: argparse.Namespace) -> ApiClient:
    load_dotenv()
    mode = args.mode
    api_base = args.api or os.environ.get("API_BASE") or os.environ.get("API_INTERNAL")
    if not api_base:
        api_base = "http://127.0.0.1:8853" if mode == "direct" and args.profile == "prod" else "http://127.0.0.1:8000"
    return configure_client(
        api_base=api_base,
        mode=mode,
        username=args.username,
        password=args.password,
    )


def default_job_map(profile: str) -> Path:
    if profile == "prod" and PROD_JOB_MAP.is_file():
        return PROD_JOB_MAP
    return STABLE_JOB_MAP


def pick_smoke_job_id(profile: str) -> int | None:
    path = default_job_map(profile)
    if not path.is_file():
        return None
    jobs = json.loads(path.read_text(encoding="utf-8")).get("jobs") or {}
    for key in ("太平人民医院", "哈尔滨工业大学医院", "黑龙江中医药大学附属第一医院"):
        if key in jobs:
            return int(jobs[key])
    if jobs:
        return int(next(iter(jobs.values())))
    return None


def run_smoke(client: ApiClient, *, profile: str) -> CliReport:
    report = CliReport("smoke", profile, client.mode, client.api_base, time.time())

    try:
        health = client.health()
        report.add(StepResult("L0_health", "L0", True, str(health.get("msg") or "ok"), {"raw": health}))
    except Exception as exc:
        report.add(StepResult("L0_health", "L0", False, str(exc)))
        report.finished_at = time.time()
        return report

    try:
        version = client.version()
        ver = (version.get("data") or {}).get("version") or version.get("msg")
        report.add(StepResult("L1_version", "L1", version.get("code") == 200, str(ver), {"raw": version}))
    except Exception as exc:
        report.add(StepResult("L1_version", "L1", False, str(exc)))

    try:
        token = client.login(force=True)
        report.add(StepResult("L2_login", "L2", bool(token), "access_token ok"))
    except Exception as exc:
        report.add(StepResult("L2_login", "L2", False, str(exc)))
        report.finished_at = time.time()
        return report

    try:
        info = client.userinfo()
        username = (info.get("data") or {}).get("username") or (info.get("data") or {}).get("name")
        report.add(StepResult("L3_userinfo", "L3", True, str(username or "ok"), {"raw": info}))
    except Exception as exc:
        report.add(StepResult("L3_userinfo", "L3", False, str(exc)))

    job_id = pick_smoke_job_id(profile)
    if job_id is None:
        report.add(StepResult("L4_job_lookup", "L4", False, "无 job map"))
    else:
        try:
            data = client.get(f"/api/hospital-reconciliations/{job_id}")
            ok = data.get("code") == 200
            hospital = (data.get("data") or {}).get("hospitalName") or (data.get("data") or {}).get("hospital_name")
            report.add(
                StepResult(
                    "L4_job_get",
                    "L4",
                    ok,
                    f"Job #{job_id} {hospital or ''}".strip(),
                    {"job_id": job_id, "raw": data},
                )
            )
        except Exception as exc:
            report.add(StepResult("L4_job_get", "L4", False, str(exc), {"job_id": job_id}))

        if job_id:
            tmp = TEST_CASE / ".cli_smoke" / f"job{job_id}_bill.xlsx"
            tmp.parent.mkdir(parents=True, exist_ok=True)
            try:
                client.export_v2(job_id, tmp, "bill")
                report.add(StepResult("L5_export_v2_bill", "L5", True, str(tmp.relative_to(ROOT))))
            except Exception as exc:
                report.add(StepResult("L5_export_v2_bill", "L5", False, str(exc), {"job_id": job_id}))

    report.finished_at = time.time()
    return report


def count_billing_enabled(rows: list[dict[str, Any]]) -> int:
    return sum(1 for row in rows if row.get("billing_enabled") or row.get("billingEnabled"))


def mysql_billing_count(deploy_path: Path) -> int | None:
    script = deploy_path / "deploy/mysql-hospital-cli.sh"
    if not script.is_file():
        script = ROOT / "deploy/mysql-hospital-cli.sh"
    if not script.is_file():
        return None
    db = os.environ.get("MYSQL_DATABASE", "hospital")
    env = os.environ.copy()
    env.setdefault("DEPLOY_PATH", str(deploy_path))
    try:
        out = subprocess.check_output(
            ["bash", str(script), "--exec-root", "-N", "-e", "SELECT COUNT(*) FROM customer WHERE billing_enabled=1", db],
            text=True,
            cwd=str(deploy_path if (deploy_path / "deploy").is_dir() else ROOT),
            env=env,
            stderr=subprocess.DEVNULL,
        )
        return int(out.strip().splitlines()[-1])
    except (subprocess.CalledProcessError, ValueError, FileNotFoundError):
        return None


def run_deploy_check(client: ApiClient, *, profile: str, expected: int, skip_mysql: bool) -> CliReport:
    report = CliReport("deploy-check", profile, client.mode, client.api_base, time.time())
    deploy_path = Path(os.environ.get("DEPLOY_PATH", ROOT))

    for _ in range(30):
        try:
            client.health()
            break
        except Exception:
            time.sleep(2)
    else:
        report.add(StepResult("L7_health_wait", "L7", False, "backend 不可达"))
        report.finished_at = time.time()
        return report
    report.add(StepResult("L7_health_wait", "L7", True, "backend 可达"))

    try:
        client.login(force=True)
        rows = client.customers()
        enabled = count_billing_enabled(rows)
        ok = enabled == expected
        report.add(
            StepResult(
                "L8_billing_enabled_api",
                "L8",
                ok,
                f"API billing_enabled=1: {enabled} / 期望 {expected}",
                {"enabled": enabled, "expected": expected},
            )
        )
    except Exception as exc:
        report.add(StepResult("L8_billing_enabled_api", "L8", False, str(exc)))
        report.finished_at = time.time()
        return report

    if not skip_mysql:
        mysql_count = mysql_billing_count(deploy_path)
        if mysql_count is None:
            report.add(StepResult("L8_mysql_compare", "L8", True, "跳过 MySQL 比对（无 mysql-hospital-cli.sh 或非部署机）"))
        else:
            ok = mysql_count == enabled
            report.add(
                StepResult(
                    "L8_mysql_compare",
                    "L8",
                    ok,
                    f"MySQL billing_enabled=1: {mysql_count}",
                    {"mysql_enabled": mysql_count, "api_enabled": enabled},
                )
            )

    report.finished_at = time.time()
    return report


def run_jobs_list(client: ApiClient, *, hospital: str | None) -> CliReport:
    report = CliReport("jobs list", "local", client.mode, client.api_base, time.time())
    try:
        client.login(force=True)
        rows = client.list_reconciliations(hospital_name=hospital)
        simplified = []
        for row in rows[:50]:
            simplified.append(
                {
                    "id": row.get("id") or row.get("jobId") or row.get("job_id"),
                    "hospital": row.get("hospitalName") or row.get("hospital_name"),
                    "status": row.get("status"),
                    "createdAt": row.get("createdAt") or row.get("createTime"),
                }
            )
        report.add(StepResult("jobs", "L4", True, f"{len(rows)} jobs", {"jobs": simplified}))
    except Exception as exc:
        report.add(StepResult("jobs", "L4", False, str(exc)))
    report.finished_at = time.time()
    return report


def forward_batch_script(script: str, passthrough: list[str], extra: list[str]) -> int:
    cmd = [sys.executable, str(SCRIPTS / script), *extra, *passthrough]
    print("+", " ".join(cmd), flush=True)
    return subprocess.call(cmd, cwd=str(ROOT))


def build_api_forward_args(args: argparse.Namespace) -> list[str]:
    out = ["--mode", args.mode]
    if args.api:
        out.extend(["--api-base", args.api])
    if args.username:
        out.extend(["--username", args.username])
    if args.password:
        out.extend(["--password", args.password])
    return out


def print_report(report: CliReport, *, as_json: bool) -> None:
    if as_json:
        print(json.dumps(report.to_dict(), ensure_ascii=False, indent=2))
        return
    print(f"\n== {report.command} ({report.mode} @ {report.api_base}) ==")
    for step in report.steps:
        mark = "OK" if step.ok else "FAIL"
        print(f"[{mark}] {step.name}: {step.detail}")
    print(f"结果: {'PASS' if report.ok else 'FAIL'} ({report.to_dict()['duration_sec']}s)")


def cmd_rules_compare(args: argparse.Namespace) -> int:
    if not args.code and not args.all:
        print("需要 --code 或 --all", file=sys.stderr)
        return 2
    client = resolve_client(args)
    try:
        report = run_rules_compare(
            client,
            code=args.code,
            compare_all=args.all,
            manifest_path=args.manifest,
        )
    except Exception as exc:
        print(f"rules compare 失败: {exc}", file=sys.stderr)
        return 1
    if args.json:
        payload = json.dumps(report, ensure_ascii=False, indent=2)
        out_path = args.json_output or PARITY_REPORT
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(payload + "\n", encoding="utf-8")
        print(payload)
    else:
        print(format_human(report))
    if args.fail_on_drift and not report.get("ok"):
        return 1
    return 0


def cmd_rules_doc(args: argparse.Namespace) -> int:
    sys.path.insert(0, str(SCRIPTS))
    from billing_rules_catalog import build_catalog_md  # noqa: E402

    out = args.out or (ROOT / "docs/医院特色计价规则清单.md")
    md = build_catalog_md()
    if args.write:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(md, encoding="utf-8")
        print(f"wrote {out}")
        return 0
    print(md)
    return 0


def cmd_rules_spot_check(args: argparse.Namespace) -> int:
    if not args.code:
        print("需要 --code", file=sys.stderr)
        return 2
    client = resolve_client(args)
    try:
        report = run_spot_check(client, code=args.code, hospital_name=args.hospital)
    except Exception as exc:
        print(f"rules spot-check 失败: {exc}", file=sys.stderr)
        return 1
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        print(format_spot_check_human(report))
    return 0 if report.get("ok") else 1


def cmd_rules_audit_names(args: argparse.Namespace) -> int:
    client = resolve_client(args)
    try:
        client.login(force=True)
        rows = client.customers()
    except Exception as exc:
        print(f"rules audit-names 失败: {exc}", file=sys.stderr)
        return 1

    from collections import defaultdict

    by_name: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        name = str(row_field(row, "canonical_name", "canonicalName") or "").strip()
        if not name:
            name = str(row_field(row, "code") or "").strip()
        by_name[name].append(row)

    dupes = {name: group for name, group in by_name.items() if len(group) > 1}
    active_dupes = {
        name: group
        for name, group in dupes.items()
        if any(str(row_field(r, "status") or "active").lower() != "inactive" for r in group)
    }

    def row_summary(row: dict[str, Any]) -> dict[str, Any]:
        return {
            "code": row_field(row, "code"),
            "canonicalName": row_field(row, "canonical_name", "canonicalName"),
            "status": row_field(row, "status") or "active",
            "billingEnabled": bool(row_field(row, "billingEnabled", "billing_enabled")),
        }

    report = {
        "ok": len(active_dupes) == 0,
        "customer_count": len(rows),
        "duplicate_name_groups": len(dupes),
        "active_duplicate_name_groups": len(active_dupes),
        "duplicates": {
            name: [row_summary(r) for r in sorted(group, key=lambda x: str(row_field(x, "code") or ""))]
            for name in sorted(active_dupes)
        },
        "inactive_only_duplicates": {
            name: [row_summary(r) for r in sorted(group, key=lambda x: str(row_field(x, "code") or ""))]
            for name in sorted(dupes.keys() - active_dupes.keys())
        },
    }

    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        if not active_dupes:
            print("规范名重复审计：无（启用客户规范名均唯一）")
            if dupes and not args.strict:
                print(f"（另有 {len(dupes) - len(active_dupes)} 组仅 inactive legacy 同名，见 --json）")
        else:
            print("规范名重复审计：发现重复（含非 inactive 客户）")
            print("")
            print("| 规范名 | 客户码 | status | billingEnabled |")
            print("|--------|--------|--------|----------------|")
            for name in sorted(active_dupes):
                for row in active_dupes[name]:
                    code = row_field(row, "code")
                    status = row_field(row, "status") or "active"
                    billing = bool(row_field(row, "billingEnabled", "billing_enabled"))
                    print(f"| {name} | {code} | {status} | {billing} |")

    if args.fail_on_dup and not report["ok"]:
        return 1
    return 0


def cmd_rules_verify_deploy(args: argparse.Namespace) -> int:
    if not args.code and not args.all:
        print("需要 --code 或 --all", file=sys.stderr)
        return 2
    client = resolve_client(args)
    deploy_path = Path(os.environ.get("DEPLOY_PATH", ROOT))

    def hash_reader() -> str | None:
        if args.skip_mysql:
            return None
        return mysql_manifest_hash(deploy_path)

    spot_code = args.spot_check or (args.code if args.code else None)
    try:
        report = run_verify_deploy(
            client,
            code=args.code,
            compare_all=args.all,
            manifest_path=args.manifest,
            mysql_hash_reader=hash_reader,
            spot_check_code=spot_code,
        )
    except Exception as exc:
        print(f"rules verify-deploy 失败: {exc}", file=sys.stderr)
        return 1
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        print(format_verify_deploy_human(report))
    if args.fail_on_drift and not report.get("ok"):
        return 1
    return 0 if report.get("ok") else 1


def cmd_smoke(args: argparse.Namespace) -> int:
    client = resolve_client(args)
    report = run_smoke(client, profile=args.profile)
    print_report(report, as_json=args.json)
    return 0 if report.ok else 1


def cmd_deploy_check(args: argparse.Namespace) -> int:
    client = resolve_client(args)
    report = run_deploy_check(client, profile=args.profile, expected=args.expected, skip_mysql=args.skip_mysql)
    print_report(report, as_json=args.json)
    return 0 if report.ok else 1


def cmd_jobs(args: argparse.Namespace) -> int:
    client = resolve_client(args)
    if args.jobs_cmd == "list":
        report = run_jobs_list(client, hospital=args.hospital)
        print_report(report, as_json=args.json)
        return 0 if report.ok else 1
    print(f"未知 jobs 子命令: {args.jobs_cmd}", file=sys.stderr)
    return 2


def cmd_s8(args: argparse.Namespace) -> int:
    extra = build_api_forward_args(args)
    if args.job_map:
        extra.extend(["--job-map", str(args.job_map)])
    if args.hospital:
        for name in args.hospital:
            extra.extend(["--hospital", name])
    return forward_batch_script("batch_s8_export_compare.py", args.passthrough, extra)


def cmd_s4(args: argparse.Namespace) -> int:
    extra = build_api_forward_args(args)
    if args.hospital:
        for name in args.hospital:
            extra.append(name)
    elif args.allow_import:
        pass
    else:
        print("s4 需要 --hospital 或显式 --allow-import 全量", file=sys.stderr)
        return 2
    return forward_batch_script("batch_june_system_test.py", args.passthrough, extra)


def cmd_verify(args: argparse.Namespace) -> int:
    client = resolve_client(args)
    combined = CliReport("verify", args.profile, client.mode, client.api_base, time.time())
    exit_code = 0

    smoke = run_smoke(client, profile=args.profile)
    combined.steps.extend(smoke.steps)
    if not smoke.ok:
        combined.ok = False
        exit_code = 1

    deploy = run_deploy_check(
        client,
        profile=args.profile,
        expected=args.expected,
        skip_mysql=args.skip_mysql,
    )
    combined.steps.extend(deploy.steps)
    if not deploy.ok:
        combined.ok = False
        exit_code = max(exit_code, 1)

    if args.level == "full" and combined.ok:
        job_map = args.job_map or default_job_map(args.profile)
        s8_args = build_api_forward_args(args) + ["--job-map", str(job_map)]
        if args.hospitals:
            for name in args.hospitals.split(","):
                name = name.strip()
                if name:
                    s8_args.extend(["--hospital", name])
        code = forward_batch_script("batch_s8_export_compare.py", [], s8_args)
        combined.add(StepResult("S8_batch", "S8", code == 0, f"exit {code}"))
        if code != 0:
            combined.ok = False
            exit_code = max(exit_code, code)

        if args.hospitals and args.allow_import:
            s4_args = build_api_forward_args(args)
            for name in args.hospitals.split(","):
                name = name.strip()
                if name:
                    s4_args.append(name)
            code = forward_batch_script("batch_june_system_test.py", [], s4_args)
            combined.add(StepResult("S4_batch", "S4", code == 0, f"exit {code}"))
            if code != 0:
                combined.ok = False
                exit_code = max(exit_code, code)

    combined.finished_at = time.time()
    print_report(combined, as_json=args.json)
    return exit_code if not combined.ok else 0


def add_common_flags(p: argparse.ArgumentParser) -> None:
    p.add_argument("--mode", choices=["docker", "direct"], default=os.environ.get("API_MODE", "docker"))
    p.add_argument("--api", "--api-base", dest="api", help="API base URL")
    p.add_argument("--profile", choices=["local", "prod"], default="local")
    p.add_argument("--username", default=None)
    p.add_argument("--password", default=None)
    p.add_argument("--json", action="store_true", help="机器可读 JSON 报告")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="hospital-cli", description="Hospital 部署与回归 CLI")
    sub = parser.add_subparsers(dest="command", required=True)

    p_smoke = sub.add_parser("smoke", help="L0-L5 健康/登录/export-v2 探测")
    add_common_flags(p_smoke)
    p_smoke.set_defaults(func=cmd_smoke)

    p_deploy = sub.add_parser("deploy-check", help="L7-L8 billing_enabled API vs MySQL")
    add_common_flags(p_deploy)
    p_deploy.add_argument("--expected", type=int, default=int(os.environ.get("EXPECTED_BILLING_ENABLED", "24")))
    p_deploy.add_argument("--skip-mysql", action="store_true")
    p_deploy.set_defaults(func=cmd_deploy_check)

    p_jobs = sub.add_parser("jobs", help="Job 查询")
    add_common_flags(p_jobs)
    jobs_sub = p_jobs.add_subparsers(dest="jobs_cmd", required=True)
    p_jobs_list = jobs_sub.add_parser("list", help="按 hospital 过滤最近 Job")
    p_jobs_list.add_argument("--hospital", "-H")
    p_jobs.set_defaults(func=cmd_jobs, passthrough=[])

    p_s8 = sub.add_parser("s8", help="S8 export-v2 比对（透传 batch_s8_export_compare.py）")
    add_common_flags(p_s8)
    p_s8.add_argument("--hospital", "-H", action="append", default=[])
    p_s8.add_argument("--job-map", type=Path)
    p_s8.add_argument("passthrough", nargs=argparse.REMAINDER, help="额外参数（前加 --）")
    p_s8.set_defaults(func=cmd_s8)

    p_s4 = sub.add_parser("s4", help="S4 pricing 定点重导（透传 batch_june_system_test.py）")
    add_common_flags(p_s4)
    p_s4.add_argument("--hospital", "-H", action="append", default=[])
    p_s4.add_argument("--allow-import", action="store_true", help="允许无 --hospital 时全量重导")
    p_s4.add_argument("passthrough", nargs=argparse.REMAINDER)
    p_s4.set_defaults(func=cmd_s4)

    p_verify = sub.add_parser("verify", help="smoke + deploy-check + 可选 S8/S4")
    add_common_flags(p_verify)
    p_verify.add_argument("--level", choices=["basic", "full"], default="basic")
    p_verify.add_argument("--hospitals", help="逗号分隔医院名（full 时 S8/S4 白名单）")
    p_verify.add_argument("--job-map", type=Path)
    p_verify.add_argument("--expected", type=int, default=int(os.environ.get("EXPECTED_BILLING_ENABLED", "24")))
    p_verify.add_argument("--skip-mysql", action="store_true")
    p_verify.add_argument("--allow-import", action="store_true", help="full 时允许 S4 import 副作用")
    p_verify.set_defaults(func=cmd_verify)

    p_billing = sub.add_parser("billing", help="特色账单验收")
    billing_sub = p_billing.add_subparsers(dest="billing_cmd", required=True)
    p_bv = billing_sub.add_parser("verify", help="长健 HRB-CJ 生产验收")
    add_common_flags(p_bv)
    p_bv.add_argument("--reimport", action="store_true", help="重新导入 6 月账单并校验 golden row")
    p_bv.add_argument("--update-prod-map", action="store_true", help="将新 Job ID 写回 job_baseline_prod.json")
    p_bv.set_defaults(func=cmd_billing_verify)

    p_rules = sub.add_parser("rules", help="特色账单规则比对")
    rules_sub = p_rules.add_subparsers(dest="rules_cmd", required=True)
    p_rc = rules_sub.add_parser("compare", help="manifest vs API productRules")
    add_common_flags(p_rc)
    p_rc.add_argument("--code", help="单院 customer code，如 ZYY-D1")
    p_rc.add_argument("--all", action="store_true", help="全部 billing_enabled 客户")
    p_rc.add_argument("--fail-on-drift", action="store_true", help="有 drift 时 exit 1")
    p_rc.add_argument("--manifest", type=Path, help="manifest JSON（默认 classpath 生成物）")
    p_rc.add_argument(
        "--json-output",
        type=Path,
        default=PARITY_REPORT,
        help=f"写入 JSON 报告（默认 {PARITY_REPORT.name}）",
    )
    p_rc.set_defaults(func=cmd_rules_compare)

    p_rd = rules_sub.add_parser("doc", help="生成 docs/医院特色计价规则清单.md")
    p_rd.add_argument("--write", action="store_true", help="写入 markdown 文件")
    p_rd.add_argument("--out", type=Path, help="输出路径")
    p_rd.set_defaults(func=cmd_rules_doc)

    p_rsc = rules_sub.add_parser("spot-check", help="定点试算验证（simulate API）")
    add_common_flags(p_rsc)
    p_rsc.add_argument("--code", required=True, help="customer code，如 HRB-2ND")
    p_rsc.add_argument("--hospital", help="hospitalName 覆盖（默认取客户名）")
    p_rsc.set_defaults(func=cmd_rules_spot_check)

    p_ran = rules_sub.add_parser("audit-names", help="按规范名审计重复客户")
    add_common_flags(p_ran)
    p_ran.add_argument("--fail-on-dup", action="store_true", help="存在非 inactive 重复规范名时 exit 1")
    p_ran.add_argument(
        "--strict",
        action="store_true",
        help="stdout 摘要也提示 inactive-only 重复组数量（默认仅在无 active 重复时提示）",
    )
    p_ran.set_defaults(func=cmd_rules_audit_names)

    p_rvd = rules_sub.add_parser("verify-deploy", help="compare + reconcile hash + 可选 spot-check")
    add_common_flags(p_rvd)
    p_rvd.add_argument("--code", help="单院 customer code")
    p_rvd.add_argument("--all", action="store_true", help="全部 billing_enabled 客户 compare")
    p_rvd.add_argument("--fail-on-drift", action="store_true", help="有 drift 时 exit 1")
    p_rvd.add_argument("--manifest", type=Path, help="manifest JSON")
    p_rvd.add_argument("--skip-mysql", action="store_true", help="跳过 reconcile hash MySQL 查询")
    p_rvd.add_argument(
        "--spot-check",
        help="额外跑 spot-check 的 code（默认 --code 时同 code）",
    )
    p_rvd.set_defaults(func=cmd_rules_verify_deploy)

    p_report = sub.add_parser("report", help="占位：请用各子命令 --json")
    p_report.set_defaults(func=lambda _a: (print("使用 smoke/deploy-check/verify --json", file=sys.stderr) or 2))

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except ApiError as exc:
        print(f"API 错误: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
