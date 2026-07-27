#!/usr/bin/env bash
# 宿主机 venv 跑 S8 脚本（需 docker exec 调 backend API；规避 Xcode Python 崩溃）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VENV="${ROOT}/.venv-s8"
if [ ! -x "${VENV}/bin/python" ]; then
  python3 -m venv "${VENV}"
  "${VENV}/bin/pip" install -q --upgrade pip
  "${VENV}/bin/pip" install -q openpyxl requests
fi
exec "${VENV}/bin/python" "$@"
