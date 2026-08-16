#!/usr/bin/env bash
# Run backend Maven tests inside JDK 17 container (matches CI / backend/Dockerfile).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEST_SELECTOR="${1:-RuleEntrySemanticsTest,PricingRuleCompilerSemanticTest,SpecialCharge11CoverageTest,PricingEngineSpecialRulesTest,PricingEngineStandardPathTest,PricingEngineBillingModeTest,RuleTypeCoverageGateTest,RuleFidelityRegressionTest}"

docker run --rm \
  -v "$ROOT/backend:/app" \
  -v hospital-backend-m2:/root/.m2 \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dtest="$TEST_SELECTOR"
