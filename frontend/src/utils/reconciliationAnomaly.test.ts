import {
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

console.log('reconciliationAnomaly tests passed')
