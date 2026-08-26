#!/usr/bin/env python3
"""通用规则（hospital_pricing_rule「标准灭菌计费规则」）跨环境对比与同步工具。

该表是「通用规则」的唯一运行时来源：高温/低温/包装/小件/清洗/物流/敷料/结款函等
全部通用计价规则都以 rules_json 形式存放在 hospital_pricing_rule（is_active=1）。
此前系统只有 customer_product_rule（医院特色规则）的对比能力（hospital-cli rules compare），
缺少针对「通用规则」的对比/复刻工具，导致 dev 与 prod 的通用规则长期漂移。

子命令：
  list  --env dev|prod                           列出该环境全部 hospital_pricing_rule
  dump  --env dev|prod [--out FILE]              导出激活规则到 JSON（含 name/version/description/rules）
  diff  --dev-base URL --prod-base URL           对比 dev/prod 激活规则，打印差异；有差异时 exit 1
  push  --dev-base URL --prod-base URL [--dry-run]  将 dev 激活规则一键复刻到 prod（先备份）

用法示例：
  python3 scripts/pricing_rule_sync.py diff \
      --dev-base http://127.0.0.1:8088 --prod-base http://39.102.213.51:8853
  python3 scripts/pricing_rule_sync.py push \
      --dev-base http://127.0.0.1:8088 --prod-base http://39.102.213.51:8853
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from lib.api_client import ApiClient  # noqa: E402

DEFAULT_DEV_BASE = "http://127.0.0.1:8088"
DEFAULT_PROD_BASE = "http://39.102.213.51:8853"
ACTIVE_PATH = "/api/hospital-pricing-rules/active"
LIST_PATH = "/api/hospital-pricing-rules"
BACKUP_DIR = ROOT / "deploy" / "backups"


def make_client(base: str) -> ApiClient:
    c = ApiClient(api_base=base, mode="direct")
    c.login(force=True)
    return c


def active_rule(client: ApiClient) -> dict[str, Any]:
    data = client.get(ACTIVE_PATH)
    if data.get("code") != 200:
        raise RuntimeError(f"读取激活规则失败: {data}")
    rule = data.get("data") or {}
    return rule


def rule_list(client: ApiClient) -> list[dict[str, Any]]:
    data = client.get(LIST_PATH)
    if data.get("code") != 200:
        raise RuntimeError(f"读取规则列表失败: {data}")
    return data.get("data") or []


def walk(a: Any, b: Any, path: str, diffs: list[str]) -> None:
    if type(a) != type(b):
        diffs.append(f"[TYPE] {path}: dev={type(a).__name__} prod={type(b).__name__}")
        return
    if isinstance(a, dict):
        for k in sorted(set(a) | set(b), key=str):
            p = f"{path}.{k}" if path else str(k)
            if k not in a:
                diffs.append(f"[ONLY-PROD] {p} = {json.dumps(b[k], ensure_ascii=False)}")
            elif k not in b:
                diffs.append(f"[ONLY-DEV]  {p} = {json.dumps(a[k], ensure_ascii=False)}")
            else:
                walk(a[k], b[k], p, diffs)
    elif isinstance(a, list):
        if a != b:
            diffs.append(f"[LIST-DIFF] {path}: dev_len={len(a)} prod_len={len(b)}")
            # 按对象标识对齐（name / size / count / label）
            def ident(x: Any) -> str:
                if isinstance(x, dict):
                    return json.dumps(
                        x.get("name") or x.get("size") or x.get("count") or x.get("label") or x,
                        ensure_ascii=False,
                        sort_keys=True,
                    )
                return json.dumps(x, ensure_ascii=False)
            am: dict[str, list[Any]] = {}
            bm: dict[str, list[Any]] = {}
            for x in a:
                am.setdefault(ident(x), []).append(x)
            for x in b:
                bm.setdefault(ident(x), []).append(x)
            for k in sorted(set(am) | set(bm), key=str):
                p = f"{path}[{k}]"
                if k not in am:
                    for x in bm[k]:
                        diffs.append(f"[ONLY-PROD] {p} = {json.dumps(x, ensure_ascii=False)}")
                elif k not in bm:
                    for x in am[k]:
                        diffs.append(f"[ONLY-DEV]  {p} = {json.dumps(x, ensure_ascii=False)}")
                else:
                    for x, y in zip(am[k], bm[k]):
                        walk(x, y, p, diffs)
    else:
        if a != b:
            diffs.append(f"[VALUE] {path}: dev={json.dumps(a, ensure_ascii=False)} prod={json.dumps(b, ensure_ascii=False)}")


def diff_rules(dev_rules: Any, prod_rules: Any) -> list[str]:
    diffs: list[str] = []
    walk(dev_rules, prod_rules, "", diffs)
    return diffs


def cmd_list(args: argparse.Namespace) -> int:
    base = args.dev_base if args.env == "dev" else args.prod_base
    client = make_client(base)
    rows = rule_list(client)
    print(f"== {args.env} ({base}) 共 {len(rows)} 条 hospital_pricing_rule ==")
    for r in rows:
        print(
            f"  id={r.get('id')} name={r.get('name')} version={r.get('version')} "
            f"isActive={r.get('isActive')} hospitalName={r.get('hospitalName')} planName={r.get('planName')}"
        )
    return 0


def cmd_dump(args: argparse.Namespace) -> int:
    base = args.dev_base if args.env == "dev" else args.prod_base
    client = make_client(base)
    rule = active_rule(client)
    payload = {
        "source": args.env,
        "base": base,
        "id": rule.get("id"),
        "name": rule.get("name"),
        "version": rule.get("version"),
        "description": rule.get("description"),
        "rules": rule.get("rules"),
        "exportedAt": time.strftime("%Y-%m-%d %H:%M:%S"),
    }
    out = Path(args.out) if args.out else None
    text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    if out:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text, encoding="utf-8")
        print(f"已导出 {args.env} 激活规则 (id={rule.get('id')}) -> {out}")
    else:
        print(text)
    return 0


def cmd_diff(args: argparse.Namespace) -> int:
    dev = active_rule(make_client(args.dev_base))
    prod = active_rule(make_client(args.prod_base))
    print(f"dev  激活规则 id={dev.get('id')} name={dev.get('name')} version={dev.get('version')}")
    print(f"prod 激活规则 id={prod.get('id')} name={prod.get('name')} version={prod.get('version')}")
    diffs = diff_rules(dev.get("rules") or {}, prod.get("rules") or {})
    if not diffs:
        print("== 通用规则完全一致（1:1）==")
        return 0
    print(f"== 发现 {len(diffs)} 处差异（dev -> prod）==")
    for d in diffs:
        print("  " + d)
    return 1


def cmd_push(args: argparse.Namespace) -> int:
    dev_client = make_client(args.dev_base)
    prod_client = make_client(args.prod_base)
    dev = active_rule(dev_client)
    prod = active_rule(prod_client)

    dev_rules = dev.get("rules") or {}
    prod_rules = prod.get("rules") or {}
    diffs = diff_rules(dev_rules, prod_rules)

    print(f"dev  -> id={dev.get('id')} name={dev.get('name')} version={dev.get('version')}")
    print(f"prod -> id={prod.get('id')} name={prod.get('name')} version={prod.get('version')}")
    if not diffs:
        print("== 已完全一致，无需同步 ==")
        return 0

    print(f"== 差异 {len(diffs)} 处，准备将 prod 复刻为 dev ==")
    if args.dry_run:
        for d in diffs:
            print("  " + d)
        print("[dry-run] 未执行任何写入")
        return 0

    # 1) 备份 prod 当前规则
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    ts = time.strftime("%Y%m%d-%H%M%S")
    backup = BACKUP_DIR / f"hospital-pricing-rule-prod-backup-{ts}.json"
    backup.write_text(
        json.dumps(
            {
                "source": "prod",
                "base": args.prod_base,
                "id": prod.get("id"),
                "name": prod.get("name"),
                "version": prod.get("version"),
                "description": prod.get("description"),
                "rules": prod_rules,
                "backedUpAt": time.strftime("%Y-%m-%d %H:%M:%S"),
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"已备份 prod 规则 -> {backup}")

    # 2) PUT 更新 prod 激活规则为 dev 内容（保留 prod 的 id，替换规则内容与版本）
    body = {
        "name": dev.get("name") or prod.get("name"),
        "version": dev.get("version") or prod.get("version"),
        "description": dev.get("description"),
        "rules": dev_rules,
    }
    data = prod_client.request_json(
        "PUT",
        f"/api/hospital-pricing-rules/{prod.get('id')}",
        token=prod_client.login(force=True),
        json_body=body,
    )
    if data.get("code") != 200:
        print(f"PUT 失败: {data}", file=sys.stderr)
        return 1
    print(f"PUT 成功（prod id={prod.get('id')}）")

    # 3) 校验
    prod_after = active_rule(prod_client)
    diffs_after = diff_rules(dev_rules, prod_after.get("rules") or {})
    if diffs_after:
        print(f"== 校验失败，仍存在 {len(diffs_after)} 处差异 ==", file=sys.stderr)
        for d in diffs_after:
            print("  " + d, file=sys.stderr)
        return 1
    print("== 复刻完成，prod 通用规则已与 dev 完全一致（1:1）==")
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="pricing_rule_sync", description="通用规则跨环境对比与同步")
    sub = p.add_subparsers(dest="command", required=True)

    p_list = sub.add_parser("list", help="列出环境全部规则")
    p_list.add_argument("--env", choices=["dev", "prod"], default="dev")
    p_list.add_argument("--dev-base", default=DEFAULT_DEV_BASE)
    p_list.add_argument("--prod-base", default=DEFAULT_PROD_BASE)
    p_list.set_defaults(func=cmd_list)

    p_dump = sub.add_parser("dump", help="导出激活规则")
    p_dump.add_argument("--env", choices=["dev", "prod"], default="dev")
    p_dump.add_argument("--dev-base", default=DEFAULT_DEV_BASE)
    p_dump.add_argument("--prod-base", default=DEFAULT_PROD_BASE)
    p_dump.add_argument("--out", type=Path)
    p_dump.set_defaults(func=cmd_dump)

    p_diff = sub.add_parser("diff", help="对比 dev/prod 激活规则")
    p_diff.add_argument("--dev-base", default=DEFAULT_DEV_BASE)
    p_diff.add_argument("--prod-base", default=DEFAULT_PROD_BASE)
    p_diff.set_defaults(func=cmd_diff)

    p_push = sub.add_parser("push", help="将 dev 激活规则复刻到 prod")
    p_push.add_argument("--dev-base", default=DEFAULT_DEV_BASE)
    p_push.add_argument("--prod-base", default=DEFAULT_PROD_BASE)
    p_push.add_argument("--dry-run", action="store_true")
    p_push.set_defaults(func=cmd_push)

    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
