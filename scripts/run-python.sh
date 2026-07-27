#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ARGS=("$@")
docker run --rm \
  -v "${ROOT}:/work" \
  -w /work \
  --network host \
  python:3.12-slim \
  bash -c 'pip install -q openpyxl requests 2>/dev/null; exec python3 "$@"' _ "${ARGS[@]}"
