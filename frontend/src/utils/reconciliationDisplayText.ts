import { getPricingModeLabel } from '@/constants/pricingModeLabels'

/** 计费策略类型 → 中文（英文枚举） */
const POLICY_TYPE_LABELS: Record<string, string> = {
  DISCOUNT: '折扣策略（DISCOUNT）',
  LOGISTICS: '物流策略（LOGISTICS）',
  MONTHLY_SETTLEMENT: '月度结算策略（MONTHLY_SETTLEMENT）',
  URGENT: '加急策略（URGENT）',
  DEDUCTION: '抵扣策略（DEDUCTION）',
  SETTLEMENT_EXTRA: '结款附加策略（SETTLEMENT_EXTRA）',
  SETTLEMENT_OVERRIDE: '结款覆盖策略（SETTLEMENT_OVERRIDE）'
}

/** 客户计费模式等未纳入 pricingModeLabels 的枚举 */
const BILLING_MODE_LABELS: Record<string, string> = {
  special_only: '仅特色计价（special_only）',
  hybrid: '混合计价（hybrid）'
}

/** billingNotes.type 等追溯类型 */
const BILLING_NOTES_TYPE_LABELS: Record<string, string> = {
  any_price_match: '多报价命中（any_price_match）',
  any_price_mismatch: '多报价未命中（any_price_mismatch）',
  field_consistency: '字段核对（field_consistency）'
}

/** 字段一致性 violation code → 中文（英文枚举） */
const FIELD_CONSISTENCY_CODE_LABELS: Record<string, string> = {
  BAG_SIZE_MISMATCH: '包材尺寸不一致（BAG_SIZE_MISMATCH）',
  MATERIAL_CLASS_MISMATCH: '包材类别不一致（MATERIAL_CLASS_MISMATCH）',
  INSTRUMENT_COUNT_MISMATCH: '器械件数不一致（INSTRUMENT_COUNT_MISMATCH）'
}

/** 修正状态 → 中文（英文枚举） */
const RECONCILIATION_STATUS_LABELS: Record<string, string> = {
  corrected: '已修正（corrected）',
  unchanged: '无需修改（unchanged）',
  warning: '人工复核（warning）',
  skipped: '已跳过（skipped）'
}

/** 按长度降序，避免短 token 误替换长 token 的前缀 */
const EMBEDDED_TERM_REPLACEMENTS: Array<[string, string]> = [
  ['special_only 未命中特色规则', '仅特色计价未命中特色规则（special_only）'],
  ['any_price_mismatch', BILLING_NOTES_TYPE_LABELS.any_price_mismatch],
  ['any_price_match', BILLING_NOTES_TYPE_LABELS.any_price_match],
  ['field_consistency', BILLING_NOTES_TYPE_LABELS.field_consistency],
  ['dressing_nonwoven', `${getPricingModeLabel('dressing_nonwoven')}（dressing_nonwoven）`],
  ['dressing_cotton', `${getPricingModeLabel('dressing_cotton')}（dressing_cotton）`],
  ['legacy_per_piece', `${getPricingModeLabel('legacy_per_piece')}（legacy_per_piece）`],
  ['special_only', BILLING_MODE_LABELS.special_only],
  ['standard', `${getPricingModeLabel('standard')}（standard）`],
  ['fixed', `${getPricingModeLabel('fixed')}（fixed）`],
  ['hybrid', BILLING_MODE_LABELS.hybrid]
]

function isAsciiIdentifier(value: string): boolean {
  return /^[A-Za-z0-9_]+$/.test(value)
}

function containsChinese(value: string): boolean {
  return /[\u4e00-\u9fff]/.test(value)
}

/** 计价路径 / 计价模式：中文标签（英文枚举） */
export function formatPricingPathDisplay(value?: string | null): string {
  if (value == null) return ''
  const trimmed = value.trim()
  if (!trimmed) return ''

  const billingMode = BILLING_MODE_LABELS[trimmed]
  if (billingMode) return billingMode

  const notesType = BILLING_NOTES_TYPE_LABELS[trimmed]
  if (notesType) return notesType

  const modeLabel = getPricingModeLabel(trimmed)
  if (modeLabel !== trimmed && modeLabel !== '继承分类默认') {
    return `${modeLabel}（${trimmed}）`
  }

  if (containsChinese(trimmed)) return trimmed
  if (isAsciiIdentifier(trimmed)) return formatEnumFallback(trimmed)
  return localizeReconciliationDisplayText(trimmed)
}

/** 策略类型：中文（英文枚举） */
export function formatPolicyTypeDisplay(value?: string | null): string {
  if (value == null) return ''
  const trimmed = value.trim()
  if (!trimmed) return ''
  const upper = trimmed.toUpperCase()
  return POLICY_TYPE_LABELS[upper] ?? POLICY_TYPE_LABELS[trimmed] ?? formatEnumFallback(trimmed)
}

/** 字段一致性 violation code */
export function formatFieldConsistencyCodeDisplay(code?: string | null): string {
  if (code == null) return ''
  const trimmed = code.trim()
  if (!trimmed) return ''
  return FIELD_CONSISTENCY_CODE_LABELS[trimmed] ?? formatEnumFallback(trimmed)
}

/** 对账行修正状态 */
export function formatReconciliationStatusDisplay(status?: string | null): string {
  if (status == null) return ''
  const trimmed = status.trim()
  if (!trimmed) return ''
  return RECONCILIATION_STATUS_LABELS[trimmed] ?? formatEnumFallback(trimmed)
}

function formatEnumFallback(value: string): string {
  if (containsChinese(value)) return value
  if (isAsciiIdentifier(value)) {
    return `系统术语（${value}）`
  }
  return value
}

/** 将文本中的英文枚举/术语替换为「中文（英文）」展示形式 */
export function localizeReconciliationDisplayText(value?: string | null): string {
  if (value == null) return ''
  let text = String(value).trim()
  if (!text) return ''

  if (!containsChinese(text) && isAsciiIdentifier(text)) {
    return formatPricingPathDisplay(text) || formatPolicyTypeDisplay(text) || formatEnumFallback(text)
  }

  for (const [token, replacement] of EMBEDDED_TERM_REPLACEMENTS) {
    if (text.includes(replacement) || text.includes(`（${token}）`)) continue
    text = text.split(token).join(replacement)
  }

  for (const [code, label] of Object.entries(POLICY_TYPE_LABELS)) {
    if (text.includes(label) || text.includes(`（${code}）`)) continue
    if (text.includes(code)) {
      text = text.replace(new RegExp(`\\b${code}\\b`, 'g'), label)
    }
  }

  return text
}
