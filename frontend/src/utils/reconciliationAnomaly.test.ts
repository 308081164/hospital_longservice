import {
  classifyReconciliationAnomalyCategories,
  filterRowsByAnomalyCategories,
  hasUnitPriceMismatch,
  isReconciliationAnomalyRow,
  needsManualRuleReview
} from './reconciliationAnomaly.ts'

function assertTrue(value: boolean, message: string) {
  if (!value) throw new Error(message)
}

assertTrue(
  hasUnitPriceMismatch({ unitPrice: 25, expectedUnitPrice: 44 }),
  'price mismatch'
)
assertTrue(
  needsManualRuleReview({ pricingRule: '未命中规则', expectedUnitPrice: null }),
  'no rule hit'
)
assertTrue(
  isReconciliationAnomalyRow({ status: 'unchanged', unitPrice: 8, expectedUnitPrice: 4 }),
  'unchanged with price mismatch is anomaly'
)
assertTrue(
  !isReconciliationAnomalyRow({ status: 'unchanged', unitPrice: 8, expectedUnitPrice: 8 }),
  'unchanged same price is not anomaly'
)

const classified = classifyReconciliationAnomalyCategories({
  status: 'warning',
  unitPrice: 8,
  expectedUnitPrice: 4,
  difference: -4
})
assertTrue(
  classified.includes('status_warning') && classified.includes('price_mismatch'),
  'warning row with price mismatch has multiple categories'
)

const rows = [
  { status: 'warning', unitPrice: 8, expectedUnitPrice: 4 },
  { status: 'unchanged', pricingRule: '未命中规则', expectedUnitPrice: null }
]
const manualOnly = filterRowsByAnomalyCategories(rows, ['manual_review'])
assertTrue(manualOnly.length === 1, 'category filter keeps manual review rows only')

console.log('reconciliationAnomaly tests passed')
