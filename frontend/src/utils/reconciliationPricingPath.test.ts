import {
  buildPricingFlowTimeline,
  classifyPricingPath,
  readEffectivePricingPath
} from './reconciliationPricingPath.ts'

function assertEqual(actual: unknown, expected: unknown, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`)
  }
}

function assertTrue(value: boolean, message: string) {
  if (!value) throw new Error(message)
}

const zuyanSfCorrectionRow = {
  status: 'unchanged',
  pricingRule: '校正价33.0',
  pricingPath: 'fixed',
  matchedRuleId: 1087,
  matchedProductId: 303,
  billingNotes: {
    matchedRuleId: 1087,
    ruleName: '校正价33.0',
    effectivePricingPath: 'fixed'
  },
  notes: [
    '结构化产品匹配: 排针 [SMALL_ITEM] → 小件计价 (standard)',
    '校正价33.0，单价按 33 元。'
  ]
}

const legacyZuyanSfRow = {
  status: 'unchanged',
  pricingRule: '校正价33.0',
  pricingPath: 'standard',
  matchedRuleId: 1087,
  notes: [
    '结构化产品匹配: 排针 [SMALL_ITEM] → 小件计价 (standard)',
    '校正价33.0，单价按 33 元。'
  ]
}

const standardRow = {
  status: 'unchanged',
  pricingRule: '高温无纺布计费',
  pricingPath: 'standard',
  notes: ['混合模式未命中特色规则，走标准灭菌计价。']
}

const classification = classifyPricingPath(zuyanSfCorrectionRow)
assertEqual(classification.label, 'pricingPath.customerFixed', 'correction price uses customerFixed badge')
assertTrue(classification.summary.includes('校正价33.0'), 'summary shows rule name')

const legacyClassification = classifyPricingPath(legacyZuyanSfRow)
assertEqual(legacyClassification.label, 'pricingPath.customerFixed', 'legacy rows infer customerFixed from rule name')

const standardClassification = classifyPricingPath(standardRow)
assertEqual(standardClassification.label, 'pricingPath.standard', 'standard path stays standard')

assertEqual(readEffectivePricingPath(zuyanSfCorrectionRow), 'fixed', 'reads effective path from billingNotes')
assertEqual(readEffectivePricingPath(legacyZuyanSfRow), 'standard', 'falls back to row pricingPath')

const timeline = buildPricingFlowTimeline(zuyanSfCorrectionRow)
assertTrue(timeline.length >= 3, 'timeline has multiple steps')
assertEqual(timeline[0]?.label, 'pricingFlow.stepProductMatch', 'product match comes first')
assertTrue(
  timeline[0]?.detail?.includes('产品识别（未作为计价路径）') ?? false,
  'product match annotated when fixed price hit'
)
assertEqual(timeline[1]?.label, 'pricingFlow.stepCustomerFixed', 'second step is customer fixed hit')

console.log('reconciliationPricingPath.test.ts: all assertions passed')
