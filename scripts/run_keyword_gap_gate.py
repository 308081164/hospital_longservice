#!/usr/bin/env python3
"""G2 关键词失配复扫：exact_token 规则 × 测试用例包名语料。"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "keyword_gap_scan.py"


def main() -> int:
    parser = argparse.ArgumentParser(description="关键词失配复扫（G2 门禁）")
    parser.add_argument("--fail-on-findings", action="store_true", help="有 findings 时 exit 1")
    args = parser.parse_args()
    if not SCRIPT.is_file():
        print(f"ERROR: missing {SCRIPT}", file=sys.stderr)
        return 2
    proc = subprocess.run([sys.executable, str(SCRIPT)], cwd=str(ROOT), text=True)
    if proc.returncode != 0 and args.fail_on_findings:
        return 1
    return proc.returncode


if __name__ == "__main__":
    raise SystemExit(main())
