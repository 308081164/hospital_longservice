#!/usr/bin/env bash
# 校验 billing-rules-manifest 是否与 seed 文件 + HardcodedRulesMigrationRunner 同步。
# 未来 CI 在 push main 前调用：bash scripts/check-manifest-fresh.sh
# 开发者新增/修改规则后若忘记运行 `python3 scripts/billing_rules_manifest.py --write`，
# 本脚本会在 CI 早期直接失败（而非拖到部署后的 parity gate 才暴露）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

python3 scripts/billing_rules_manifest.py --write >/dev/null

python3 - <<'PY'
import json
import subprocess
import sys
from pathlib import Path

FILES = [
    "backend/src/main/resources/billing-seeds/billing-rules-manifest.json",
    "backend/src/test/resources/billing-rules-manifest.json",
    "测试用例/billing_rules_manifest.json",
]

failed = False
for rel in FILES:
    p = Path(rel)
    if not p.is_file():
        print(f"ERROR: 缺少 manifest 文件: {rel}", file=sys.stderr)
        failed = True
        continue
    cur_hash = json.loads(p.read_text(encoding="utf-8")).get("manifest_hash")
    head_hash = None
    try:
        head_raw = subprocess.check_output(
            ["git", "show", f"HEAD:{rel}"], text=True, stderr=subprocess.DEVNULL
        )
        head_hash = json.loads(head_raw).get("manifest_hash")
    except Exception:
        head_hash = None  # 新增文件：仍会因 cur_hash != None 判为过期
    if cur_hash != head_hash:
        print(f"ERROR: manifest 过期（未重新生成）: {rel}", file=sys.stderr)
        print(f"  HEAD hash: {(head_hash or '')[:16]}", file=sys.stderr)
        print(f"  生成 hash: {(cur_hash or '')[:16]}", file=sys.stderr)
        failed = True

if failed:
    print("", file=sys.stderr)
    print(
        "请运行 `python3 scripts/billing_rules_manifest.py --write` 重新生成 manifest 并提交。",
        file=sys.stderr,
    )
    sys.exit(1)

print("OK: 3 份 billing-rules-manifest.json 的 manifest_hash 均与 HEAD 一致（无漂移）")
PY
