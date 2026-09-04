import {
  formatFieldConsistencyCodeDisplay,
  formatPolicyTypeDisplay,
  localizeReconciliationDisplayText
} from '@/utils/reconciliationDisplayText'

export type DiscountChainStep = {
  label: string
  detail?: string
}

export type PolicyTraceStep = {
  label: string
  detail?: string
  policyType?: string
}

export type FieldConsistencyViolation = {
  code: string
  message: string
  packNameSize?: string
  materialSize?: string
  packNameCount?: number
  instrumentCount?: number
  type?: string
  packageMaterial?: string
}

export type BillingValidationViolation = {
  code: string
  message: string
  severity: 'error' | 'warning'
  missingFields?: string[]
}

export type FieldConsistencyHighlightField = 'type' | 'packName' | 'packageMaterial' | 'instrumentCount'

export type FieldConsistencyCellTone = 'red' | 'amber' | null

export type ReconciliationBillingContext = {
  isMultiPrice: boolean
  isMatched: boolean
  isMismatch: boolean
  matchedPrice: number | null
  matchedRuleId: number | null
  ruleName: string | null
  candidates: number[]
  billUnitPrice: number | null
  expectedUnitPrice: number | null
  discountChain: DiscountChainStep[]
  policyTraces: PolicyTraceStep[]
  traceNotes: string[]
  billingNotesType: string | null
  fieldConsistencyViolations: FieldConsistencyViolation[]
  hasFieldConsistencyIssues: boolean
  billingValidationViolations: BillingValidationViolation[]
  hasBlockingValidationIssues: boolean
  hasZeroUnitPriceWarning: boolean
  blocksPricingDisplay: boolean
}

export type ReconciliationRowBillingFields = {
  matchedRuleId?: number | null
  matchedPriceOption?: number | null
  billingNotes?: Record<string, unknown> | null
}

function toNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (value == null || value === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

export function normalizeBillingNotes(raw: unknown): Record<string, unknown> | null {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null
  return raw as Record<string, unknown>
}

export function parsePriceList(raw: unknown): number[] {
  if (!Array.isArray(raw)) return []
  return raw.map((value) => toNumber(value)).filter((value): value is number => value != null)
}

export function parseCandidatesFromNotes(notes: string[]): number[] {
  for (const note of notes) {
    const match = note.match(/候选[：:]\s*([0-9.,\s]+)/)
    if (!match?.[1]) continue
    return match[1]
      .split(/[,，]/)
      .map((part) => Number(part.trim()))
      .filter((value) => Number.isFinite(value))
  }
  return []
}

export function parseRuleNameFromNotes(notes: string[]): string | null {
  for (const note of notes) {
    const hitMatch = note.match(/多报价命中：规则「([^」]+)」/)
    if (hitMatch?.[1]) return hitMatch[1]
    if (note.includes('多报价候选')) {
      const prefix = note.split(/，单价按|，按每件/)[0]?.trim()
      if (prefix) return prefix
    }
  }
  return null
}

function isDiscountNote(note: string): boolean {
  return /命中.*折扣|×|倍计费|包装收费|倍率/.test(note) && !note.includes('多报价命中')
}

export function extractDiscountChain(
  notes: string[],
  billingNotes: Record<string, unknown> | null
): DiscountChainStep[] {
  const chain: DiscountChainStep[] = []

  const rawChain = billingNotes?.discountChain ?? billingNotes?.discount_chain
  if (Array.isArray(rawChain)) {
    for (const item of rawChain) {
      if (typeof item === 'string') {
        chain.push({ label: item })
      } else if (item && typeof item === 'object') {
        const obj = item as Record<string, unknown>
        chain.push({
          label: String(obj.label ?? obj.name ?? obj.step ?? ''),
          detail:
            obj.detail != null
              ? String(obj.detail)
              : obj.note != null
                ? String(obj.note)
                : undefined
        })
      }
    }
  }

  for (const note of notes) {
    if (!isDiscountNote(note)) continue
    const short = note.length > 48 ? `${note.slice(0, 48)}…` : note
    if (chain.some((step) => step.detail === note || step.label === short)) continue
    chain.push({ label: short, detail: note.length > 48 ? note : undefined })
  }

  return chain
}

export function extractPolicyTraces(
  billingNotes: Record<string, unknown> | null
): PolicyTraceStep[] {
  const traces: PolicyTraceStep[] = []
  const raw = billingNotes?.policyTraces ?? billingNotes?.policy_traces
  if (!Array.isArray(raw)) return traces
  for (const item of raw) {
    if (typeof item === 'string') {
      traces.push({ label: localizeReconciliationDisplayText(item) })
    } else if (item && typeof item === 'object') {
      const obj = item as Record<string, unknown>
      const rawPolicyType = obj.policyType != null ? String(obj.policyType) : undefined
      const rawLabel = String(obj.name ?? obj.label ?? '')
      let label = rawLabel
      if (!label && rawPolicyType) {
        label = formatPolicyTypeDisplay(rawPolicyType)
      } else if (label && rawPolicyType && label.toUpperCase() === rawPolicyType.toUpperCase()) {
        label = formatPolicyTypeDisplay(rawPolicyType)
      } else {
        label = localizeReconciliationDisplayText(label || '策略')
      }
      traces.push({
        label,
        detail: obj.description != null ? localizeReconciliationDisplayText(String(obj.description)) : undefined,
        policyType:
          rawPolicyType && !label.includes(rawPolicyType) ? rawPolicyType : undefined
      })
    }
  }
  return traces
}

function parseFieldConsistencyViolation(raw: unknown): FieldConsistencyViolation | null {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null
  const obj = raw as Record<string, unknown>
  const code = obj.code != null ? String(obj.code) : ''
  const message = obj.message != null ? String(obj.message) : ''
  if (!code && !message) return null
  return {
    code,
    message,
    packNameSize: obj.packNameSize != null ? String(obj.packNameSize) : undefined,
    materialSize: obj.materialSize != null ? String(obj.materialSize) : undefined,
    packNameCount: toNumber(obj.packNameCount) ?? undefined,
    instrumentCount: toNumber(obj.instrumentCount) ?? undefined,
    type: obj.type != null ? String(obj.type) : undefined,
    packageMaterial: obj.packageMaterial != null ? String(obj.packageMaterial) : undefined
  }
}

export function extractFieldConsistencyViolations(
  billingNotes: Record<string, unknown> | null
): FieldConsistencyViolation[] {
  if (!billingNotes) return []

  const nested =
    billingNotes.fieldConsistency ??
    billingNotes.field_consistency ??
    (billingNotes.type === 'field_consistency' ? billingNotes : null)

  const rawViolations =
    billingNotes.consistencyViolations ??
    billingNotes.consistency_violations ??
    (nested && typeof nested === 'object' && !Array.isArray(nested)
      ? (nested as Record<string, unknown>).violations
      : null)

  if (!Array.isArray(rawViolations)) return []

  return rawViolations
    .map((item) => parseFieldConsistencyViolation(item))
    .filter((item): item is FieldConsistencyViolation => item != null)
}

function parseBillingValidationViolation(raw: unknown): BillingValidationViolation | null {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null
  const obj = raw as Record<string, unknown>
  const code = obj.code != null ? String(obj.code) : ''
  const message = obj.message != null ? String(obj.message) : ''
  if (!code && !message) return null
  const severityRaw = obj.severity != null ? String(obj.severity).toLowerCase() : 'error'
  const severity: BillingValidationViolation['severity'] =
    severityRaw === 'warning' ? 'warning' : 'error'
  const missingFields = Array.isArray(obj.missingFields)
    ? obj.missingFields.map((item) => String(item))
    : undefined
  return { code, message, severity, missingFields }
}

export function extractBillingValidationViolations(
  billingNotes: Record<string, unknown> | null
): BillingValidationViolation[] {
  if (!billingNotes) return []

  const nested =
    billingNotes.billingValidation ??
    billingNotes.billing_validation ??
    (billingNotes.type === 'billing_validation' ? billingNotes : null)

  const rawViolations =
    nested && typeof nested === 'object' && !Array.isArray(nested)
      ? (nested as Record<string, unknown>).violations
      : billingNotes.validationViolations ?? billingNotes.validation_violations

  if (!Array.isArray(rawViolations)) return []

  return rawViolations
    .map((item) => parseBillingValidationViolation(item))
    .filter((item): item is BillingValidationViolation => item != null)
}

export function blocksPricingFromBillingNotes(
  billingNotes: Record<string, unknown> | null
): boolean {
  if (!billingNotes) return false
  if (billingNotes.blocksPricing === true) return true
  const nested = billingNotes.billingValidation ?? billingNotes.billing_validation
  if (nested && typeof nested === 'object' && !Array.isArray(nested)) {
    if ((nested as Record<string, unknown>).blocksPricing === true) return true
  }
  return false
}

/** 字段核对/校验异常时在数值后展示红叹号（不阻断计价结果展示）。 */
export function shouldShowValidationIndicator(row: Record<string, unknown>): boolean {
  const ctx = parseReconciliationBillingContext(row)
  return ctx.hasFieldConsistencyIssues || ctx.hasBlockingValidationIssues
}

export function validationIndicatorMessages(row: Record<string, unknown>): string[] {
  const ctx = parseReconciliationBillingContext(row)
  const messages: string[] = []
  for (const item of ctx.fieldConsistencyViolations) {
    if (item.message) messages.push(item.message)
  }
  for (const item of ctx.billingValidationViolations) {
    if (item.severity === 'error' && item.message) messages.push(item.message)
  }
  return messages
}

export function shouldBlockPricingDisplay(row: Record<string, unknown>): boolean {
  return parseReconciliationBillingContext(row).blocksPricingDisplay
}

export function fieldConsistencyViolationLabel(
  code: string,
  t: (key: string) => string
): string {
  switch (code) {
    case 'BAG_SIZE_MISMATCH':
      return t('reconciliation.detail.fieldConsistencyBagSize')
    case 'MATERIAL_CLASS_MISMATCH':
      return t('reconciliation.detail.fieldConsistencyMaterialClass')
    case 'INSTRUMENT_COUNT_MISMATCH':
      return t('reconciliation.detail.fieldConsistencyInstrumentCount')
    default:
      return formatFieldConsistencyCodeDisplay(code) || code
  }
}

export function billingValidationViolationLabel(
  code: string,
  t: (key: string) => string
): string {
  switch (code) {
    case 'MISSING_FIELDS':
      return t('reconciliation.detail.validationMissingFields')
    case 'BLANK_PACKAGE_MATERIAL':
      return t('reconciliation.detail.validationBlankPackageMaterial')
    case 'ZERO_INSTRUMENT_COUNT':
      return t('reconciliation.detail.validationZeroInstrumentCount')
    case 'ZERO_UNIT_PRICE':
      return t('reconciliation.detail.validationZeroUnitPrice')
    default:
      return code
  }
}

/** 根据 violation code 映射需要高亮的表格字段 */
export function fieldConsistencyAffectedFields(
  violations: FieldConsistencyViolation[],
  billingValidationViolations: BillingValidationViolation[] = []
): Set<FieldConsistencyHighlightField> {
  const fields = new Set<FieldConsistencyHighlightField>()
  for (const violation of violations) {
    switch (violation.code) {
      case 'BAG_SIZE_MISMATCH':
        fields.add('packName')
        fields.add('packageMaterial')
        break
      case 'MATERIAL_CLASS_MISMATCH':
        fields.add('type')
        fields.add('packageMaterial')
        break
      case 'INSTRUMENT_COUNT_MISMATCH':
        fields.add('packName')
        fields.add('instrumentCount')
        break
      default:
        break
    }
  }
  for (const violation of billingValidationViolations) {
    if (violation.severity !== 'error') continue
    switch (violation.code) {
      case 'BLANK_PACKAGE_MATERIAL':
        fields.add('packageMaterial')
        break
      case 'ZERO_INSTRUMENT_COUNT':
        fields.add('instrumentCount')
        break
      case 'MISSING_FIELDS':
        for (const field of violation.missingFields ?? []) {
          if (
            field === 'type' ||
            field === 'packName' ||
            field === 'packageMaterial' ||
            field === 'instrumentCount'
          ) {
            fields.add(field)
          }
        }
        break
      default:
        break
    }
  }
  return fields
}

/** 单个单元格高亮色调：红=包材/类型，琥珀=器械件数 */
export function fieldConsistencyCellTone(
  row: Record<string, unknown>,
  field: FieldConsistencyHighlightField
): FieldConsistencyCellTone {
  const ctx = parseReconciliationBillingContext(row)
  if (!ctx.hasFieldConsistencyIssues && !ctx.hasBlockingValidationIssues) return null
  const affected = fieldConsistencyAffectedFields(
    ctx.fieldConsistencyViolations,
    ctx.billingValidationViolations.filter((item) => item.severity === 'error')
  )
  if (!affected.has(field)) return null

  const hasAmberInstrumentMismatch = ctx.fieldConsistencyViolations.some(
    (item) => item.code === 'INSTRUMENT_COUNT_MISMATCH'
  )
  const hasRedInstrumentIssue =
    ctx.billingValidationViolations.some((item) => item.code === 'ZERO_INSTRUMENT_COUNT') ||
    ctx.fieldConsistencyViolations.some((item) =>
      ['BAG_SIZE_MISMATCH', 'MATERIAL_CLASS_MISMATCH'].includes(item.code)
    ) ||
    ctx.billingValidationViolations.some((item) =>
      ['BLANK_PACKAGE_MATERIAL', 'MISSING_FIELDS'].includes(item.code)
    )

  if (field === 'instrumentCount') {
    if (ctx.billingValidationViolations.some((item) => item.code === 'ZERO_INSTRUMENT_COUNT')) {
      return 'red'
    }
    if (hasAmberInstrumentMismatch) return 'amber'
  }
  if (field === 'packName' && hasAmberInstrumentMismatch) {
    return 'amber'
  }
  if (hasRedInstrumentIssue || field === 'packageMaterial' || field === 'type') {
    return 'red'
  }
  return 'red'
}

export function fieldConsistencyCellClass(
  row: Record<string, unknown>,
  field: FieldConsistencyHighlightField
): string {
  const tone = fieldConsistencyCellTone(row, field)
  if (tone === 'red') return 'field-consistency-cell field-consistency-cell--red'
  if (tone === 'amber') return 'field-consistency-cell field-consistency-cell--amber'
  return ''
}

export function fieldConsistencyRowClass(row: Record<string, unknown>): string {
  const ctx = parseReconciliationBillingContext(row)
  return ctx.hasBlockingValidationIssues || ctx.hasFieldConsistencyIssues
    ? 'field-consistency-row'
    : ''
}

export function parseReconciliationBillingContext(
  row: Record<string, unknown>
): ReconciliationBillingContext {
  const billingNotes = normalizeBillingNotes(row.billingNotes ?? row.billing_notes)
  const notes = Array.isArray(row.notes)
    ? row.notes.filter((note): note is string => typeof note === 'string')
    : []

  const matchedPrice = toNumber(
    row.matchedPriceOption ??
      row.matched_price_option ??
      billingNotes?.matchedPrice ??
      billingNotes?.matched_price
  )

  const matchedRuleId = toNumber(
    row.matchedRuleId ??
      row.matched_rule_id ??
      billingNotes?.matchedRuleId ??
      billingNotes?.matched_rule_id
  )

  let candidates = parsePriceList(
    billingNotes?.candidates ?? billingNotes?.candidatePrices ?? billingNotes?.candidate_prices
  )
  if (candidates.length === 0) {
    candidates = parseCandidatesFromNotes(notes)
  }

  const billUnitPrice = toNumber(row.unitPrice)
  const expectedUnitPrice = toNumber(row.expectedUnitPrice)

  const billingNotesType = billingNotes?.type != null ? String(billingNotes.type) : null
  const ruleName =
    billingNotes?.ruleName != null ? String(billingNotes.ruleName) : parseRuleNameFromNotes(notes)

  const hasMultiPriceSignal =
    billingNotesType === 'any_price_match' ||
    billingNotesType === 'any_price_mismatch' ||
    candidates.length >= 2 ||
    notes.some((note) => note.includes('多报价'))

  const isMatched =
    billingNotesType === 'any_price_match' || notes.some((note) => note.includes('多报价命中'))

  const isMultiPrice = hasMultiPriceSignal
  const isMismatch = isMultiPrice && !isMatched && billUnitPrice != null

  const discountChain = extractDiscountChain(notes, billingNotes)
  const policyTraces = extractPolicyTraces(billingNotes)
  const fieldConsistencyViolations = extractFieldConsistencyViolations(billingNotes)
  const billingValidationViolations = extractBillingValidationViolations(billingNotes)
  const blocksPricingDisplay = blocksPricingFromBillingNotes(billingNotes)
  const hasBlockingValidationIssues = billingValidationViolations.some(
    (item) => item.severity === 'error'
  )
  const hasZeroUnitPriceWarning = billingValidationViolations.some(
    (item) => item.code === 'ZERO_UNIT_PRICE'
  )
  const traceNotes = notes.filter(
    (note) =>
      !note.includes('多报价命中') &&
      !isDiscountNote(note) &&
      !note.includes('【字段核对】') &&
      !note.includes('【字段核对错误】') &&
      !note.includes('【原始单价数据异常】')
  )

  return {
    isMultiPrice,
    isMatched,
    isMismatch,
    matchedPrice,
    matchedRuleId,
    ruleName,
    candidates,
    billUnitPrice,
    expectedUnitPrice,
    discountChain,
    policyTraces,
    traceNotes,
    billingNotesType,
    fieldConsistencyViolations,
    hasFieldConsistencyIssues: fieldConsistencyViolations.length > 0,
    billingValidationViolations,
    hasBlockingValidationIssues,
    hasZeroUnitPriceWarning,
    blocksPricingDisplay
  }
}

export function hasBillingDetail(row: Record<string, unknown>): boolean {
  const ctx = parseReconciliationBillingContext(row)
  return (
    ctx.isMultiPrice ||
    ctx.discountChain.length > 0 ||
    ctx.policyTraces.length > 0 ||
    ctx.hasFieldConsistencyIssues ||
    ctx.hasBlockingValidationIssues ||
    ctx.hasZeroUnitPriceWarning ||
    ctx.matchedRuleId != null ||
    ctx.traceNotes.length > 0 ||
    ctx.ruleName != null
  )
}

export function extractRowBillingFields(
  row: Record<string, unknown>
): ReconciliationRowBillingFields {
  const billingNotes = normalizeBillingNotes(row.billingNotes ?? row.billing_notes)
  return {
    matchedRuleId: toNumber(row.matchedRuleId ?? row.matched_rule_id),
    matchedPriceOption: toNumber(row.matchedPriceOption ?? row.matched_price_option),
    billingNotes
  }
}

export function formatReconciliationCurrency(value: number | null | undefined): string {
  if (value == null) return '-'
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
