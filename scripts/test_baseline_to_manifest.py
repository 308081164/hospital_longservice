#!/usr/bin/env python3
"""baseline_to_manifest.py 单元测试（往返一致）。"""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/baseline_to_manifest.py"


class BaselineToManifestTest(unittest.TestCase):
    def test_check_passes_on_current_repo(self) -> None:
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--check"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)

    def test_write_then_check_roundtrip(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            manifest = ROOT / "backend/src/main/resources/billing-seeds/billing-rules-manifest.json"
            baseline_dir = ROOT / "backend/src/main/resources/billing-rules/baseline"
            index = ROOT / "backend/src/main/resources/billing-rules/index.json"

            dest_manifest = tmp_path / "billing-rules-manifest.json"
            dest_manifest.write_text(manifest.read_text(encoding="utf-8"), encoding="utf-8")

            # 注入路径：通过环境变量不可行，直接调用模块
            sys.path.insert(0, str(ROOT / "scripts"))
            import baseline_to_manifest as btm  # noqa: E402

            orig_manifest = btm.MANIFEST_PATH
            orig_test = btm.TEST_MANIFEST_PATH
            orig_baseline = btm.BASELINE_DIR
            try:
                btm.MANIFEST_PATH = dest_manifest
                btm.TEST_MANIFEST_PATH = tmp_path / "test-manifest.json"
                btm.BASELINE_DIR = baseline_dir
                btm.INDEX_PATH = index
                btm.build_manifest()
                btm.write_manifest(btm.build_manifest(json.loads(dest_manifest.read_text(encoding="utf-8"))))
                self.assertEqual(btm.check_manifest(), 0)
            finally:
                btm.MANIFEST_PATH = orig_manifest
                btm.TEST_MANIFEST_PATH = orig_test
                btm.BASELINE_DIR = orig_baseline


if __name__ == "__main__":
    unittest.main()
