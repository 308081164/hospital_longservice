import {
  blocksPricingFromBillingNotes,
  fieldConsistencyCellClass,
  fieldConsistencyCellTone,
  fieldConsistencyRowClass,
  parseReconciliationBillingContext,
  shouldBlockPricingDisplay,
  shouldShowValidationIndicator,
  validationIndicatorMessages
} from './reconciliationBillingNotes.ts'

function assertEqual(actual: unknown, expected: unknown, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`)
  }
}

function assertTrue(value: boolean, message: string) {
  if (!value) throw new Error(message)
}

const fieldConsistencyRow = {
  expectedUnitPrice: 8,
  correctedTotalPrice: 8,
  billingNotes: {
    fieldConsistency: {
      type: 'field_consistency',
      violations: [
        {
          code: 'INSTRUMENT_COUNT_MISMATCH',
          message: '包名件数合计 3 与器械数列 2 不一致'
        }
      ]
    }
  }
}

const fieldCtx = parseReconciliationBillingContext(fieldConsistencyRow)
assertTrue(fieldCtx.hasFieldConsistencyIssues, 'field consistency should be detected')
assertEqual(fieldCtx.blocksPricingDisplay, false, 'field consistency must not block pricing display')
assertEqual(shouldBlockPricingDisplay(fieldConsistencyRow), false, 'shouldBlockPricingDisplay is false')
assertEqual(shouldShowValidationIndicator(fieldConsistencyRow), true, 'indicator should show')
assertTrue(
  validationIndicatorMessages(fieldConsistencyRow).includes('包名件数合计 3 与器械数列 2 不一致'),
  'indicator tooltip should include violation message'
)

const billingValidationRow = {
  expectedUnitPrice: 10,
  correctedTotalPrice: 10,
  billingNotes: {
    billingValidation: {
      violations: [
        {
          code: 'BLANK_PACKAGE_MATERIAL',
          severity: 'error',
          message: '包装材料为空'
        }
      ]
    }
  }
}

const validationCtx = parseReconciliationBillingContext(billingValidationRow)
assertTrue(validationCtx.hasBlockingValidationIssues, 'billing validation error should be detected')
assertEqual(validationCtx.blocksPricingDisplay, false, 'validation error must not block pricing display')
assertEqual(shouldBlockPricingDisplay(billingValidationRow), false, 'shouldBlockPricingDisplay is false')
assertEqual(shouldShowValidationIndicator(billingValidationRow), true, 'indicator should show')

const explicitBlockNotes = {
  blocksPricing: true,
  fieldConsistency: {
    type: 'field_consistency',
    violations: [{ code: 'BAG_SIZE_MISMATCH', message: '尺寸不一致' }]
  }
}

assertEqual(blocksPricingFromBillingNotes(explicitBlockNotes), true, 'explicit blocksPricing still blocks')
assertEqual(
  shouldBlockPricingDisplay({
    billingNotes: explicitBlockNotes,
    expectedUnitPrice: null,
    correctedTotalPrice: null
  }),
  true,
  'explicit blocksPricing row still blocks display'
)

const unchangedRow = { ...fieldConsistencyRow, status: 'unchanged' }
assertEqual(
  shouldShowValidationIndicator(unchangedRow),
  false,
  'indicator hidden when row marked as 无需修改 (unchanged)'
)
assertEqual(
  shouldShowValidationIndicator({ ...fieldConsistencyRow, status: 'warning' }),
  true,
  'indicator still shows for other statuses'
)

// 无需修改（unchanged）必须同时清除整行与单元格高亮
assertTrue(
  fieldConsistencyRowClass(fieldConsistencyRow) !== '',
  'row class present for row with field consistency issues'
)
assertEqual(
  fieldConsistencyRowClass(unchangedRow),
  '',
  'row highlight cleared when row marked as 无需修改 (unchanged)'
)
assertEqual(
  fieldConsistencyRowClass({ ...billingValidationRow, status: 'unchanged' }),
  '',
  'row highlight cleared for billing validation row marked unchanged'
)
assertTrue(
  fieldConsistencyRowClass({ ...fieldConsistencyRow, status: 'warning' }) !== '',
  'row highlight still present for other statuses'
)

assertTrue(
  fieldConsistencyCellTone(fieldConsistencyRow, 'packName') != null,
  'cell tone present for affected field'
)
assertEqual(
  fieldConsistencyCellTone(unchangedRow, 'packName'),
  null,
  'cell tone cleared when row marked as 无需修改 (unchanged)'
)
assertEqual(
  fieldConsistencyCellTone(unchangedRow, 'instrumentCount'),
  null,
  'cell tone cleared for instrumentCount when unchanged'
)
assertEqual(
  fieldConsistencyCellClass(unchangedRow, 'packName'),
  '',
  'cell class cleared when row marked as 无需修改 (unchanged)'
)
assertEqual(
  fieldConsistencyCellTone({ ...billingValidationRow, status: 'unchanged' }, 'packageMaterial'),
  null,
  'cell tone cleared for billing validation row marked unchanged'
)
assertTrue(
  fieldConsistencyCellTone({ ...fieldConsistencyRow, status: 'warning' }, 'packName') != null,
  'cell tone still present for other statuses'
)

console.log('reconciliationBillingNotes.test.ts: all assertions passed')
