#!/usr/bin/env python3
"""Run S5 error-correction engine smoke tests (Maven). See 测试用例/S5纠错测试最小用例集.md."""
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"

TESTS = (
    "PricingEngineS5ErrorCorrectionTest",
    "PricingEngineTest#ngjyZeroDressingPackWithoutMaterialFlagsWarning",
)


def run_mvn(extra_args: list[str] | None = None) -> int:
    cmd = [
        "mvn",
        "-q",
        "test",
        f"-Dtest={','.join(TESTS)}",
    ]
    if extra_args:
        cmd.extend(extra_args)
    print(" ".join(cmd), file=sys.stderr)
    return subprocess.call(cmd, cwd=BACKEND)


def main() -> int:
    use_docker = os.environ.get("S5_SMOKE_DOCKER", "").strip() in ("1", "true", "yes")
    if use_docker:
        compose = ROOT / "docker-compose.yml"
        if not compose.is_file():
            print("docker-compose.yml not found", file=sys.stderr)
            return 2
        cmd = [
            "docker",
            "compose",
            "-f",
            str(compose),
            "run",
            "--rm",
            "--no-deps",
            "backend",
            "mvn",
            "-q",
            "test",
            f"-Dtest={','.join(TESTS)}",
        ]
        print(" ".join(cmd), file=sys.stderr)
        return subprocess.call(cmd, cwd=ROOT)
    return run_mvn()


if __name__ == "__main__":
    raise SystemExit(main())
