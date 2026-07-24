#!/usr/bin/env bash
# 纯 Python 脚本（不依赖 docker exec）。需要调 backend API 的脚本请用 run-python-host.sh。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VENV="${ROOT}/.venv-s8"
if [ ! -x "${VENV}/bin/python" ]; then
  python3 -m venv "${VENV}"
  "${VENV}/bin/pip" install -q --upgrade pip
  "${VENV}/bin/pip" install -q openpyxl requests
fi
exec "${VENV}/bin/python" "$@"
