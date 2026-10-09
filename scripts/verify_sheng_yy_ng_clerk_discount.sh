#!/usr/bin/env bash
# 省医院南岗内勤六折 · 对账编排器回归（无需本地 JDK）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
docker run --rm \
  -v "$ROOT/backend:/app" \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn -q -Dtest=ShengYyNgClerkDiscountOrchestratorTest,ReconciliationPricingOrchestratorTest test
echo "PASS: SHENG-YY-NG clerk discount orchestrator tests"
