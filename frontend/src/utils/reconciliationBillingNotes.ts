export type DiscountChainStep = {
  label: string
  detail?: string
}

export type PolicyTraceStep = {
  label: string
  detail?: string
  policyType?: string
}

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
      traces.push({ label: item })
    } else if (item && typeof item === 'object') {
      const obj = item as Record<string, unknown>
      traces.push({
        label: String(obj.name ?? obj.policyType ?? obj.label ?? '策略'),
        detail: obj.description != null ? String(obj.description) : undefined,
        policyType: obj.policyType != null ? String(obj.policyType) : undefined
      })
    }
  }
  return traces
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
  const traceNotes = notes.filter((note) => !note.includes('多报价命中') && !isDiscountNote(note))

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
    billingNotesType
  }
}

export function hasBillingDetail(row: Record<string, unknown>): boolean {
  const ctx = parseReconciliationBillingContext(row)
  return (
    ctx.isMultiPrice ||
    ctx.discountChain.length > 0 ||
    ctx.policyTraces.length > 0 ||
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
