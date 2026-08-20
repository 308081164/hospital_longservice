#!/usr/bin/env bash
# 包名计数能力回归：客户复核 106 条 + 单元测试矩阵
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SELECTOR="PackNameSpecParserTest,PackNameCountCustomerReviewTest,BillRowFieldConsistencyValidatorTest"

if [[ "${1:-}" == "--local" ]]; then
  cd "$ROOT/backend"
  mvn -q test -Dtest="$SELECTOR"
else
  docker run --rm \
    -v "$ROOT/backend:/app" \
    -v hospital-backend-m2:/root/.m2 \
    -w /app \
    maven:3.9-eclipse-temurin-17 \
    mvn test -Dtest="$SELECTOR"
fi
