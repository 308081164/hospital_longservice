import { parseReconciliationBillingContext } from '@/utils/reconciliationBillingNotes'

const PRICE_TOLERANCE = 0.001

/** 「仅异常」模式下的分类筛选项（与 isReconciliationAnomalyRow 口径对齐，可多标签叠加）。 */
export type ReconciliationAnomalyCategory =
  | 'status_warning'
  | 'status_corrected'
  | 'price_mismatch'
  | 'manual_review'
  | 'field_check'
  | 'amount_difference'

export const ALL_ANOMALY_CATEGORIES: ReconciliationAnomalyCategory[] = [
  'status_warning',
  'status_corrected',
  'price_mismatch',
  'manual_review',
  'field_check',
  'amount_difference'
]

export type AnomalyCategoryOption = {
  value: ReconciliationAnomalyCategory
  count: number
}

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
 * 判定单行归属的异常分类（一行可命中多个分类）。
 */
export function classifyReconciliationAnomalyCategories(
  row: Record<string, unknown>,
  options?: { includeFieldConsistency?: boolean }
): ReconciliationAnomalyCategory[] {
  const categories: ReconciliationAnomalyCategory[] = []
  const status = String(row.status ?? '')
  if (status === 'warning') categories.push('status_warning')
  if (status === 'corrected') categories.push('status_corrected')
  if (hasUnitPriceMismatch(row)) categories.push('price_mismatch')
  if (needsManualRuleReview(row)) categories.push('manual_review')

  if (options?.includeFieldConsistency !== false) {
    const ctx = parseReconciliationBillingContext(row)
    if (ctx.hasFieldConsistencyIssues || ctx.hasBlockingValidationIssues) {
      categories.push('field_check')
    }
  }

  const difference = toNumber(row.difference)
  if (difference != null && Math.abs(difference) > PRICE_TOLERANCE) {
    categories.push('amount_difference')
  }

  return categories
}

/**
 * 「仅异常」筛选口径：与后端 export-anomalies / 字段核验告警保持一致。
 * 包含：非 unchanged/skipped 状态、单价不一致、未命中规则、字段核对异常。
 */
export function isReconciliationAnomalyRow(
  row: Record<string, unknown>,
  options?: { includeFieldConsistency?: boolean }
): boolean {
  return classifyReconciliationAnomalyCategories(row, options).length > 0
}

/** 按分类筛选异常行；categories 为空时返回全部 rows。 */
export function filterRowsByAnomalyCategories<T extends Record<string, unknown>>(
  rows: T[],
  categories: ReconciliationAnomalyCategory[]
): T[] {
  if (!categories.length) return rows
  const selected = new Set(categories)
  return rows.filter((row) =>
    classifyReconciliationAnomalyCategories(row).some((category) => selected.has(category))
  )
}

/** 构建分类下拉选项及命中行数（用于仅异常模式工具栏）。 */
export function buildAnomalyCategoryOptions(
  rows: Record<string, unknown>[]
): AnomalyCategoryOption[] {
  const counts = new Map<ReconciliationAnomalyCategory, number>()
  for (const category of ALL_ANOMALY_CATEGORIES) {
    counts.set(category, 0)
  }
  for (const row of rows) {
    const seen = new Set<ReconciliationAnomalyCategory>()
    for (const category of classifyReconciliationAnomalyCategories(row)) {
      if (seen.has(category)) continue
      seen.add(category)
      counts.set(category, (counts.get(category) ?? 0) + 1)
    }
  }
  return ALL_ANOMALY_CATEGORIES.map((value) => ({
    value,
    count: counts.get(value) ?? 0
  }))
}
