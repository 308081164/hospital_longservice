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
    p_deploy.add_argument("--expected", type=int, default=int(os.environ.get("EXPECTED_BILLING_ENABLED", "36")))
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
    p_verify.add_argument("--expected", type=int, default=int(os.environ.get("EXPECTED_BILLING_ENABLED", "36")))
    p_verify.add_argument("--skip-mysql", action="store_true")
    p_verify.add_argument("--allow-import", action="store_true", help="full 时允许 S4 import 副作用")
    p_verify.set_defaults(func=cmd_verify)

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
