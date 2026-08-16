import {
  hasBillingDetail,
  parseReconciliationBillingContext
} from '@/utils/reconciliationBillingNotes'

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

function truncateSummary(text: string, maxLen = 28): string {
  if (text.length <= maxLen) return text
  return `${text.slice(0, maxLen)}…`
}

export function classifyPricingPath(row: Record<string, unknown>): PricingPathClassification {
  const pricingRule = readPricingRule(row)
  const status = readStatus(row)

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

  const notes = readNotes(row)
  if (notes.some((note) => note.includes('混合模式未命中特色规则，走标准灭菌计价'))) {
    return {
      category: 'STANDARD',
      label: 'pricingPath.standard',
      tagType: 'success',
      summary: truncateSummary(pricingRule || '标准灭菌')
    }
  }

  const pricingPath = row.pricingPath ?? row.pricing_path
  if (typeof pricingPath === 'string' && pricingPath.trim()) {
    const pathLabel = pricingPath.trim()
    if (pathLabel !== 'standard' && pathLabel !== 'fixed') {
      return {
        category: 'SPECIAL_HIT',
        label: 'pricingPath.specialHit',
        tagType: 'warning',
        summary: truncateSummary(pricingRule || pathLabel)
      }
    }
  }

  const matchedRuleId = row.matchedRuleId ?? row.matched_rule_id
  if (matchedRuleId != null && matchedRuleId !== '') {
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

  if (pricingRule) {
    steps.push({
      kind: 'summary',
      label: 'pricingFlow.stepSummary',
      detail: pricingRule
    })
  }

  if (ctx.ruleName || ctx.matchedRuleId != null) {
    const parts: string[] = []
    if (ctx.ruleName) parts.push(ctx.ruleName)
    if (ctx.matchedRuleId != null) parts.push(`规则 ID: ${ctx.matchedRuleId}`)
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

  readNotes(row).forEach((note) => {
    steps.push({
      kind: 'note',
      label: 'pricingFlow.stepNote',
      detail: note
    })
  })

  if (steps.length === 0 && ctx.traceNotes.length > 0) {
    ctx.traceNotes.forEach((note) => {
      steps.push({ kind: 'note', label: 'pricingFlow.stepNote', detail: note })
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
