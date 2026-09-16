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

const manualReviewRow = {
  status: 'warning',
  pricingRule: '电力骨牵引包人工核对',
  pricingPath: 'preserve',
  matchedRuleId: 9901,
  billingNotes: {
    matchedRuleId: 9901,
    ruleName: '电力骨牵引包人工核对',
    effectivePricingPath: 'preserve',
    manualReview: true
  },
  notes: [
    '电力骨牵引包人工核对，按每件 0 元，单包计费件数 1 件，单价按 0 元。',
    '【计价告警】命中规则「电力骨牵引包人工核对」，需人工核对，已按账单原价 192.5 元暂计。'
  ]
}

const manualReviewClassification = classifyPricingPath(manualReviewRow)
assertEqual(
  manualReviewClassification.label,
  'pricingPath.manualReview',
  'manual review uses manualReview badge instead of customerFixed'
)

const dianliXishoufuRow = {
  status: 'warning',
  pricingRule: '未识别包装类型，保留原价',
  pricingPath: 'preserve',
  unitPrice: 35,
  expectedUnitPrice: 35,
  notes: [
    '【计价告警】包装材料""未能识别为纸塑袋或无纺布，已按账单原价暂计，请检查包装材料列填写是否正确，并人工核对单价。'
  ],
  billingNotes: {
    effectivePricingPath: 'preserve'
  }
}

const xishoufuClassification = classifyPricingPath(dianliXishoufuRow)
assertEqual(
  xishoufuClassification.label,
  'pricingPath.manualReview',
  'preserve-original fallback shows manual review badge'
)
assertTrue(
  !xishoufuClassification.summary.includes('校正价'),
  '洗手服 without correction rule does not show correction summary'
)

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
