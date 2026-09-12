import { parseReconciliationBillingContext } from '@/utils/reconciliationBillingNotes'

const PRICE_TOLERANCE = 0.001

function toNumber(value: unknown): number | null {
  if (value == null || value === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function readPricingRule(row: Record<string, unknown>): string {
  const raw = row.pricingRule ?? row.pricing_rule
  return raw == null ? '' : String(raw).trim()
}

/** 规则单价与原单价不一致（字段核验告警）。 */
export function hasUnitPriceMismatch(row: Record<string, unknown>): boolean {
  const unitPrice = toNumber(row.unitPrice ?? row.unit_price)
  const expectedUnitPrice = toNumber(row.expectedUnitPrice ?? row.expected_unit_price)
  if (unitPrice == null || expectedUnitPrice == null) return false
  return Math.abs(unitPrice - expectedUnitPrice) > PRICE_TOLERANCE
}

/** 需要人工核验：未命中任何计价规则，或引擎无法给出规则单价。 */
export function needsManualRuleReview(row: Record<string, unknown>): boolean {
  const pricingRule = readPricingRule(row)
  const expectedUnitPrice = toNumber(row.expectedUnitPrice ?? row.expected_unit_price)
  if (pricingRule === '未命中规则') return true
  if (pricingRule.includes('未识别包装类型')) return true
  if (pricingRule.includes('special_only 未命中')) return true
  if (!pricingRule && expectedUnitPrice == null) return true
  return false
}

/**
 * 「仅异常」筛选口径：与后端 export-anomalies / 字段核验告警保持一致。
 * 包含：非 unchanged/skipped 状态、单价不一致、未命中规则、字段核对异常。
 */
export function isReconciliationAnomalyRow(
  row: Record<string, unknown>,
  options?: { includeFieldConsistency?: boolean }
): boolean {
  const status = String(row.status ?? '')
  if (status === 'warning' || status === 'corrected') return true

  if (hasUnitPriceMismatch(row)) return true
  if (needsManualRuleReview(row)) return true

  if (options?.includeFieldConsistency !== false) {
    const ctx = parseReconciliationBillingContext(row)
    if (ctx.hasFieldConsistencyIssues || ctx.hasBlockingValidationIssues) return true
  }

  const difference = toNumber(row.difference)
  if (difference != null && Math.abs(difference) > PRICE_TOLERANCE) return true

  return false
}
