import {
  hasBillingDetail,
  normalizeBillingNotes,
  parseReconciliationBillingContext
} from '@/utils/reconciliationBillingNotes'
import { localizeReconciliationDisplayText } from '@/utils/reconciliationDisplayText'

export type PricingPathCategory =
  | 'SPECIAL_HIT'
  | 'STANDARD'
  | 'PRESERVE'
  | 'DISABLED'
  | 'SKIPPED'
  | 'UNKNOWN'

export type PricingPathTagType = 'success' | 'warning' | 'info' | 'danger'

export type PricingPathClassification = {
  category: PricingPathCategory
  label: string
  tagType: PricingPathTagType
  summary: string
}

export type PricingFlowStepKind =
  | 'summary'
  | 'note'
  | 'discount'
  | 'multiPrice'
  | 'policy'
  | 'ruleMeta'
  | 'productMatch'

export type PricingFlowStep = {
  kind: PricingFlowStepKind
  label: string
  detail?: string
}

const STANDARD_KEYWORDS = [
  '高温',
  '低温',
  '敷料',
  '阶梯',
  '纸塑',
  '无纺布',
  '路径覆盖',
  '未命中规则'
]

const STRUCTURED_PRODUCT_MATCH_PREFIX = '结构化产品匹配:'

function readPricingRule(row: Record<string, unknown>): string {
  const raw = row.pricingRule ?? row.pricing_rule
  return raw == null ? '' : String(raw).trim()
}

function readStatus(row: Record<string, unknown>): string {
  return String(row.status ?? '')
}

function readNotes(row: Record<string, unknown>): string[] {
  if (!Array.isArray(row.notes)) return []
  return row.notes.filter((note): note is string => typeof note === 'string')
}

function isStandardPricingRule(pricingRule: string): boolean {
  if (!pricingRule) return false
  return STANDARD_KEYWORDS.some((keyword) => pricingRule.includes(keyword))
}

function isCustomerCorrectionPriceRule(pricingRule: string): boolean {
  return pricingRule.startsWith('校正价')
}

function isStructuredProductMatchNote(note: string): boolean {
  return note.startsWith(STRUCTURED_PRODUCT_MATCH_PREFIX)
}

function truncateSummary(text: string, maxLen = 28): string {
  if (text.length <= maxLen) return text
  return `${text.slice(0, maxLen)}…`
}

/** 读取引擎落库的实际计价路径（优先 billingNotes，兼容旧行仅含产品识别路径） */
export function readEffectivePricingPath(row: Record<string, unknown>): string {
  const billingNotes = normalizeBillingNotes(row.billingNotes ?? row.billing_notes)
  const fromNotes = billingNotes?.effectivePricingPath ?? billingNotes?.effective_pricing_path
  if (typeof fromNotes === 'string' && fromNotes.trim()) {
    return fromNotes.trim()
  }
  const raw = row.pricingPath ?? row.pricing_path
  return raw == null ? '' : String(raw).trim()
}

function isCustomerFixedPriceHit(row: Record<string, unknown>, pricingRule: string): boolean {
  return readEffectivePricingPath(row) === 'fixed' || isCustomerCorrectionPriceRule(pricingRule)
}

function formatStructuredProductMatchNote(note: string, identificationOnly: boolean): string {
  const localized = localizeReconciliationDisplayText(note)
  if (!identificationOnly) return localized
  const body = localized.replace(STRUCTURED_PRODUCT_MATCH_PREFIX, '').trim()
  return `产品识别（未作为计价路径）：${body}`
}

export function classifyPricingPath(row: Record<string, unknown>): PricingPathClassification {
  const pricingRule = readPricingRule(row)
  const status = readStatus(row)
  const effectivePath = readEffectivePricingPath(row)

  if (status === 'skipped') {
    return {
      category: 'SKIPPED',
      label: 'pricingPath.skipped',
      tagType: 'info',
      summary: pricingRule || '无法自动计价'
    }
  }

  if (pricingRule === '特色账单已关闭') {
    return {
      category: 'DISABLED',
      label: 'pricingPath.disabled',
      tagType: 'info',
      summary: pricingRule
    }
  }

  if (pricingRule.includes('special_only 未命中')) {
    return {
      category: 'PRESERVE',
      label: 'pricingPath.preserve',
      tagType: 'info',
      summary: pricingRule
    }
  }

  if (isCustomerFixedPriceHit(row, pricingRule)) {
    return {
      category: 'SPECIAL_HIT',
      label: 'pricingPath.customerFixed',
      tagType: 'warning',
      summary: truncateSummary(pricingRule || '客户校正价')
    }
  }

  const notes = readNotes(row)
  if (notes.some((note) => note.includes('混合模式未命中特色规则，走标准灭菌计价'))) {
    return {
      category: 'STANDARD',
      label: 'pricingPath.standard',
      tagType: 'success',
      summary: truncateSummary(pricingRule || '标准灭菌')
    }
  }

  if (effectivePath === 'standard') {
    return {
      category: 'STANDARD',
      label: 'pricingPath.standard',
      tagType: 'success',
      summary: truncateSummary(pricingRule || '标准灭菌')
    }
  }

  if (effectivePath && effectivePath !== 'fixed') {
    return {
      category: 'SPECIAL_HIT',
      label: 'pricingPath.specialHit',
      tagType: 'warning',
      summary: truncateSummary(pricingRule || effectivePath)
    }
  }

  const matchedRuleId = row.matchedRuleId ?? row.matched_rule_id
  if (matchedRuleId != null && matchedRuleId !== '') {
    if (pricingRule && isStandardPricingRule(pricingRule)) {
      return {
        category: 'STANDARD',
        label: 'pricingPath.standard',
        tagType: 'success',
        summary: truncateSummary(pricingRule)
      }
    }
    return {
      category: 'SPECIAL_HIT',
      label: 'pricingPath.specialHit',
      tagType: 'warning',
      summary: truncateSummary(pricingRule || String(matchedRuleId))
    }
  }

  if (pricingRule && !isStandardPricingRule(pricingRule)) {
    return {
      category: 'SPECIAL_HIT',
      label: 'pricingPath.specialHit',
      tagType: 'warning',
      summary: truncateSummary(pricingRule)
    }
  }

  if (pricingRule && isStandardPricingRule(pricingRule)) {
    return {
      category: 'STANDARD',
      label: 'pricingPath.standard',
      tagType: 'success',
      summary: truncateSummary(pricingRule)
    }
  }

  return {
    category: 'UNKNOWN',
    label: 'pricingPath.unknown',
    tagType: 'info',
    summary: pricingRule || '—'
  }
}

export function buildPricingFlowTimeline(row: Record<string, unknown>): PricingFlowStep[] {
  const steps: PricingFlowStep[] = []
  const pricingRule = readPricingRule(row)
  const ctx = parseReconciliationBillingContext(row)
  const notes = readNotes(row)
  const customerFixedHit = isCustomerFixedPriceHit(row, pricingRule)

  const productMatchNotes = notes.filter(isStructuredProductMatchNote)
  const pricingNotes = notes.filter((note) => !isStructuredProductMatchNote(note))

  productMatchNotes.forEach((note) => {
    steps.push({
      kind: 'productMatch',
      label: 'pricingFlow.stepProductMatch',
      detail: formatStructuredProductMatchNote(note, customerFixedHit)
    })
  })

  if (pricingRule) {
    steps.push({
      kind: 'summary',
      label: customerFixedHit ? 'pricingFlow.stepCustomerFixed' : 'pricingFlow.stepSummary',
      detail: localizeReconciliationDisplayText(pricingRule)
    })
  }

  if (ctx.ruleName || ctx.matchedRuleId != null) {
    const parts: string[] = []
    if (ctx.ruleName) parts.push(ctx.ruleName)
    else if (customerFixedHit && pricingRule) parts.push(pricingRule)
    if (ctx.matchedRuleId != null) parts.push(`规则编号（Rule ID）：${ctx.matchedRuleId}`)
    steps.push({
      kind: 'ruleMeta',
      label: 'pricingFlow.stepRuleMeta',
      detail: parts.join(' · ')
    })
  }

  if (ctx.isMultiPrice) {
    const multiParts = [
      ctx.isMatched ? '多报价命中' : '多报价未命中',
      ctx.candidates.length ? `候选: ${ctx.candidates.join(' / ')}` : null,
      ctx.matchedPrice != null ? `命中价: ${ctx.matchedPrice}` : null
    ].filter(Boolean)
    steps.push({
      kind: 'multiPrice',
      label: 'pricingFlow.stepMultiPrice',
      detail: multiParts.join('；')
    })
  }

  pricingNotes.forEach((note) => {
    steps.push({
      kind: 'note',
      label: 'pricingFlow.stepNote',
      detail: localizeReconciliationDisplayText(note)
    })
  })

  if (steps.length === 0 && ctx.traceNotes.length > 0) {
    ctx.traceNotes.forEach((note) => {
      steps.push({
        kind: 'note',
        label: 'pricingFlow.stepNote',
        detail: localizeReconciliationDisplayText(note)
      })
    })
  }

  return steps
}

export function hasPricingDetail(row: Record<string, unknown>): boolean {
  const pricingRule = readPricingRule(row)
  if (pricingRule && pricingRule !== '未命中规则') return true
  if (readNotes(row).length > 0) return true
  return hasBillingDetail(row)
}

export function pricingPathSummary(row: Record<string, unknown>): string {
  return classifyPricingPath(row).summary
}
