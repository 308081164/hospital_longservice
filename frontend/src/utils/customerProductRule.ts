export type BillingMode = 'PER_PACK' | 'PER_INSTRUMENT' | 'PACK_NAME_SUFFIX'

export type CustomerProductRuleType =
  | 'FIXED_PRICE'
  | 'PRICE_PER_INSTRUMENT'
  | 'MULTIPLIER'
  | 'FOLD'
  | 'EXTRA_FEE'
  | 'ADD_FEE'

export type CustomerProductRuleMatchMode = 'first' | 'any_price'

export type BillingTemperatureScope = 'HT' | 'LT' | 'ANY'

export const CUSTOMER_PRODUCT_RULE_TYPES: CustomerProductRuleType[] = [
  'FIXED_PRICE',
  'PRICE_PER_INSTRUMENT',
  'MULTIPLIER',
  'FOLD',
  'EXTRA_FEE',
]

/** 后端存库默认值；引擎不读该字段，仅影响同客户多条策略的编译顺序 */
export const CUSTOMER_PRODUCT_RULE_DEFAULT_PRIORITY = 100

export function inferBillingModeFromDraft(draft: Pick<CustomerProductRuleDraft, 'ruleType' | 'billingMode' | 'keywords'>): BillingMode {
  if (draft.billingMode) return draft.billingMode
  if (draft.ruleType === 'PRICE_PER_INSTRUMENT') {
    if (draft.keywords.some((k) => k.trim() === '刮勺探针')) return 'PACK_NAME_SUFFIX'
    return 'PER_INSTRUMENT'
  }
  return 'PER_PACK'
}

export function syncRuleTypeFromBillingMode(draft: CustomerProductRuleDraft): void {
  const mode = inferBillingModeFromDraft(draft)
  draft.billingMode = mode
  draft.ruleType = mode === 'PER_PACK' ? 'FIXED_PRICE' : 'PRICE_PER_INSTRUMENT'
  if (mode === 'PACK_NAME_SUFFIX') {
    draft.pieceCountSource = draft.pieceCountSource ?? 'PACK_NAME_LAST_NUMBER'
  } else if (mode === 'PER_INSTRUMENT') {
    draft.pieceCountSource = draft.pieceCountSource ?? 'EFFECTIVE_COUNT'
  } else {
    draft.pieceCountSource = undefined
  }
}

export function isProductRequired(ruleType: CustomerProductRuleType): boolean {
  return ruleType === 'FIXED_PRICE' || ruleType === 'PRICE_PER_INSTRUMENT' || ruleType === 'MULTIPLIER'
}

export function isSettlementRule(ruleType: CustomerProductRuleType): boolean {
  return ruleType === 'FOLD' || ruleType === 'EXTRA_FEE' || ruleType === 'ADD_FEE'
}

export interface CustomerProductRuleDraft {
  productId?: number
  productName?: string
  ruleType: CustomerProductRuleType
  matchMode: CustomerProductRuleMatchMode
  name?: string
  price?: number
  acceptedPrices: number[]
  multiplier?: number
  fee?: number
  threshold?: number
  foldRatio?: number
  keywords: string[]
  excludeKeywords: string[]
  materials: string[]
  temperature?: BillingTemperatureScope | ''
  bagSizeEquals?: number
  maxBagSizeExclusive?: number
  minInstrumentCount?: number
  maxInstrumentCount?: number
  originalUnitPrice?: number
  departments: string[]
  billingMode?: BillingMode
  pieceCountSource?: string
  skipPackaging: boolean
  skipDiscount: boolean
  priority: number
  isActive: boolean
}

export function createEmptyProductRuleDraft(
  ruleType: CustomerProductRuleType = 'FIXED_PRICE',
): CustomerProductRuleDraft {
  return {
    ruleType,
    matchMode: 'first',
    price: 1,
    acceptedPrices: [],
    multiplier: 1,
    fee: 1,
    threshold: 10,
    foldRatio: 5,
    keywords: [],
    excludeKeywords: [],
    materials: [],
    departments: [],
    skipPackaging: false,
    skipDiscount: false,
    priority: CUSTOMER_PRODUCT_RULE_DEFAULT_PRIORITY,
    isActive: true,
  }
}

export function ruleFromRecord(rule: Api.MasterData.CustomerProductRule): CustomerProductRuleDraft {
  const rawType = rule.ruleType ?? (rule as { rule_type?: string }).rule_type ?? 'FIXED_PRICE'
  const ruleType = rawType as CustomerProductRuleType
  const rawMatchMode = rule.matchMode ?? (rule as { match_mode?: string }).match_mode ?? 'first'
  const acceptedRaw = rule.acceptedPrices ?? (rule as { accepted_prices?: number[] }).accepted_prices ?? []
  return {
    productId: rule.productId ?? rule.product_id,
    productName: rule.productName ?? rule.product_name,
    ruleType,
    matchMode: rawMatchMode === 'any_price' ? 'any_price' : 'first',
    name: rule.name,
    price: rule.price ?? rule.fixed_price,
    acceptedPrices: [...acceptedRaw],
    multiplier: rule.multiplier,
    fee: rule.fee,
    threshold: rule.threshold,
    foldRatio: rule.foldRatio ?? rule.fold_ratio,
    keywords: [...(rule.keywords ?? [])],
    excludeKeywords: [...(rule.excludeKeywords ?? (rule as { exclude_keywords?: string[] }).exclude_keywords ?? [])],
    materials: [...(rule.materials ?? [])],
    temperature: normalizeTemperature(rule.temperature ?? (rule as { temperature?: string }).temperature),
    bagSizeEquals: rule.bagSizeEquals ?? rule.bag_size_equals,
    maxBagSizeExclusive: rule.maxBagSizeExclusive ?? rule.max_bag_size_exclusive,
    minInstrumentCount: rule.minInstrumentCount ?? rule.min_instrument_count,
    maxInstrumentCount: rule.maxInstrumentCount ?? rule.max_instrument_count,
    originalUnitPrice: (rule as { originalUnitPrice?: number; original_unit_price?: number }).originalUnitPrice
      ?? (rule as { original_unit_price?: number }).original_unit_price,
    billingMode: inferBillingModeFromRecord(rule),
    pieceCountSource: (rule as { pieceCountSource?: string; piece_count_source?: string }).pieceCountSource
      ?? (rule as { piece_count_source?: string }).piece_count_source,
    departments: parseDepartmentsFromRule(rule),
    skipPackaging: rule.skipPackaging ?? rule.skip_packaging ?? false,
    skipDiscount: rule.skipDiscount ?? rule.skip_discount ?? false,
    priority: rule.priority ?? CUSTOMER_PRODUCT_RULE_DEFAULT_PRIORITY,
    isActive: rule.isActive ?? rule.is_active ?? true,
  }
}

function inferBillingModeFromRecord(rule: Api.MasterData.CustomerProductRule): BillingMode {
  const explicit = (rule as { billingMode?: BillingMode; billing_mode?: BillingMode }).billingMode
    ?? (rule as { billing_mode?: BillingMode }).billing_mode
  if (explicit) return explicit
  const ruleType = rule.ruleType ?? (rule as { rule_type?: string }).rule_type
  if (ruleType === 'PRICE_PER_INSTRUMENT') {
    if ((rule.keywords ?? []).some((k) => k.trim() === '刮勺探针')) return 'PACK_NAME_SUFFIX'
    return 'PER_INSTRUMENT'
  }
  return 'PER_PACK'
}

function normalizeList(values?: string[]): string {
  return JSON.stringify((values ?? []).map((v) => v.trim()).filter(Boolean).sort())
}

/** Append keyword to list if not already present (trimmed exact match). Mutates `keywords`. */
export function appendKeywordIfMissing(keywords: string[], keyword: string): boolean {
  const trimmed = keyword.trim()
  if (!trimmed) return false
  if (keywords.some((k) => k.trim() === trimmed)) return false
  keywords.push(trimmed)
  return true
}

/** Keep the first keyword slot aligned with the primary product/keyword field. */
export function syncPrimaryKeyword(keywords: string[], keyword: string): void {
  const trimmed = keyword.trim()
  if (!trimmed) {
    if (keywords.length > 0) keywords.shift()
    return
  }
  if (keywords.length === 0) {
    keywords.push(trimmed)
    return
  }
  keywords[0] = trimmed
}

export function hasSameMatchSignature(
  a: Pick<CustomerProductRuleDraft, 'ruleType' | 'matchMode' | 'productId' | 'keywords' | 'excludeKeywords' | 'materials' | 'temperature' | 'bagSizeEquals' | 'maxBagSizeExclusive' | 'minInstrumentCount' | 'maxInstrumentCount'>,
  b: Pick<CustomerProductRuleDraft, 'ruleType' | 'matchMode' | 'productId' | 'keywords' | 'excludeKeywords' | 'materials' | 'temperature' | 'bagSizeEquals' | 'maxBagSizeExclusive' | 'minInstrumentCount' | 'maxInstrumentCount'>,
): boolean {
  return a.ruleType === b.ruleType
    && a.matchMode === b.matchMode
    && a.productId === b.productId
    && normalizeList(a.keywords) === normalizeList(b.keywords)
    && normalizeList(a.excludeKeywords) === normalizeList(b.excludeKeywords)
    && normalizeList(a.materials) === normalizeList(b.materials)
    && (a.temperature || '') === (b.temperature || '')
    && (a.bagSizeEquals ?? null) === (b.bagSizeEquals ?? null)
    && (a.maxBagSizeExclusive ?? null) === (b.maxBagSizeExclusive ?? null)
    && (a.minInstrumentCount ?? null) === (b.minInstrumentCount ?? null)
    && (a.maxInstrumentCount ?? null) === (b.maxInstrumentCount ?? null)
}

const LEGACY_RULE_TYPE_SUFFIXES = [
  '固定单价',
  '固定价格',
  '按件计价',
  '倍率计价',
  '倍率',
  '件数折算',
  '折算',
  '场景加收',
  '加收',
] as const

/** Strip hospital prefix + rule-type suffix from legacy migrated rule names. */
export function sanitizeLegacyRuleName(name?: string | null): string | null {
  let text = name?.trim()
  if (!text) return null

  text = text.replace(/\s*每件\s*[\d.]+\s*元\s*$/, '').trim()

  for (const suffix of LEGACY_RULE_TYPE_SUFFIXES) {
    if (text.endsWith(suffix)) {
      text = text.slice(0, -suffix.length).trimEnd()
      break
    }
  }

  const hospitalMatch = text.match(
    /^[\u4e00-\u9fff\d\s.-]+?(?:医院|门诊|集团|中心|诊所)(?:[（(][^）)]+[）)])?\s*(.+)$/,
  )
  if (hospitalMatch?.[1]?.trim()) {
    text = hospitalMatch[1].trim()
  }

  text = text.replace(/^[\u4e00-\u9fff]+集团\s+/, '').trim()

  return text || null
}

export function isLegacyPollutedRuleName(name?: string | null): boolean {
  const trimmed = name?.trim()
  if (!trimmed) return false
  const sanitized = sanitizeLegacyRuleName(trimmed)
  return !!sanitized && sanitized !== trimmed
}

/** Resolve a clean label for table display: product name > keyword > sanitized legacy name. */
export function resolveProductRuleLabel(
  rule: Api.MasterData.CustomerProductRule,
  products?: Api.MasterData.ProductRecord[],
): string {
  const productName = rule.productName ?? rule.product_name
  if (productName?.trim()) return productName.trim()

  const productId = rule.productId ?? rule.product_id
  if (productId != null) {
    const found = products?.find((p) => p.id === productId)
    if (found?.name?.trim()) return found.name.trim()
  }

  const firstKeyword = rule.keywords?.map((k) => k.trim()).find(Boolean)
  if (firstKeyword) return firstKeyword

  const sanitized = sanitizeLegacyRuleName(rule.name)
  if (sanitized) return sanitized

  if (productId != null) return `商品 #${productId}`
  return '特色计价策略'
}

/** Resolve name to persist: product name > keyword > clean custom name (never polluted legacy). */
export function resolveProductRuleSaveName(
  draft: CustomerProductRuleDraft,
  productName?: string,
): string | undefined {
  const fromProduct = productName?.trim()
  if (fromProduct) return fromProduct

  const fromKeyword = draft.keywords.map((k) => k.trim()).find(Boolean)
  if (fromKeyword) return fromKeyword

  const draftName = draft.name?.trim()
  if (draftName && !isLegacyPollutedRuleName(draftName)) {
    return draftName
  }

  return sanitizeLegacyRuleName(draftName) ?? undefined
}

function normalizeOptionalPositiveInt(value?: number | null): number | undefined {
  if (value == null || value <= 0) return undefined
  return value
}

export function draftToProductRule(
  draft: CustomerProductRuleDraft,
  productName?: string,
): Api.MasterData.CustomerProductRule {
  const isAnyPrice = draft.matchMode === 'any_price'
  const resolvedName = resolveProductRuleSaveName(draft, productName)
  return {
    productId: draft.productId,
    ruleType: draft.ruleType,
    matchMode: draft.matchMode,
    name: resolvedName,
    productName: productName ?? resolvedName,
    price: draft.ruleType === 'FIXED_PRICE' || draft.ruleType === 'PRICE_PER_INSTRUMENT'
      ? (isAnyPrice ? draft.acceptedPrices[0] : draft.price)
      : undefined,
    fixed_price: draft.ruleType === 'FIXED_PRICE' || draft.ruleType === 'PRICE_PER_INSTRUMENT'
      ? (isAnyPrice ? draft.acceptedPrices[0] : draft.price)
      : undefined,
    acceptedPrices: isAnyPrice && draft.acceptedPrices.length ? [...draft.acceptedPrices] : undefined,
    multiplier: draft.ruleType === 'MULTIPLIER' ? draft.multiplier : undefined,
    fee: draft.ruleType === 'EXTRA_FEE' || draft.ruleType === 'ADD_FEE' ? draft.fee : undefined,
    threshold: draft.ruleType === 'FOLD' ? draft.threshold : undefined,
    foldRatio: draft.ruleType === 'FOLD' ? draft.foldRatio : undefined,
    keywords: draft.keywords.length ? [...draft.keywords] : undefined,
    excludeKeywords: draft.excludeKeywords.length ? [...draft.excludeKeywords] : undefined,
    materials: draft.materials.length ? [...draft.materials] : undefined,
    temperature: draft.temperature || undefined,
    bagSizeEquals: normalizeOptionalPositiveInt(draft.bagSizeEquals),
    maxBagSizeExclusive: normalizeOptionalPositiveInt(draft.maxBagSizeExclusive),
    minInstrumentCount: normalizeOptionalPositiveInt(draft.minInstrumentCount),
    maxInstrumentCount: normalizeOptionalPositiveInt(draft.maxInstrumentCount),
    skipPackaging: draft.skipPackaging,
    skipDiscount: draft.skipDiscount,
    priority: CUSTOMER_PRODUCT_RULE_DEFAULT_PRIORITY,
    isActive: draft.isActive,
  }
}

export function draftToSavePayload(
  draft: CustomerProductRuleDraft,
  productName?: string,
): Api.MasterData.SaveCustomerProductRulePayload {
  syncRuleTypeFromBillingMode(draft)
  const isAnyPrice = draft.matchMode === 'any_price'
  return {
    productId: draft.productId,
    ruleType: draft.ruleType,
    matchMode: draft.matchMode,
    name: resolveProductRuleSaveName(draft, productName),
    price: draft.ruleType === 'FIXED_PRICE' || draft.ruleType === 'PRICE_PER_INSTRUMENT'
      ? (isAnyPrice ? undefined : draft.price)
      : undefined,
    acceptedPrices: isAnyPrice && draft.acceptedPrices.length ? [...draft.acceptedPrices] : undefined,
    multiplier: draft.ruleType === 'MULTIPLIER' ? draft.multiplier : undefined,
    fee: draft.ruleType === 'EXTRA_FEE' || draft.ruleType === 'ADD_FEE' ? draft.fee : undefined,
    threshold: draft.ruleType === 'FOLD' ? draft.threshold : undefined,
    foldRatio: draft.ruleType === 'FOLD' ? draft.foldRatio : undefined,
    keywords: draft.keywords,
    excludeKeywords: draft.excludeKeywords,
    materials: draft.materials,
    temperature: draft.temperature || undefined,
    bagSizeEquals: normalizeOptionalPositiveInt(draft.bagSizeEquals),
    maxBagSizeExclusive: normalizeOptionalPositiveInt(draft.maxBagSizeExclusive),
    minInstrumentCount: normalizeOptionalPositiveInt(draft.minInstrumentCount),
    maxInstrumentCount: normalizeOptionalPositiveInt(draft.maxInstrumentCount),
    originalUnitPrice: draft.originalUnitPrice,
    departments: draft.departments?.length ? [...draft.departments] : undefined,
    billingMode: draft.billingMode ?? inferBillingModeFromDraft(draft),
    pieceCountSource: draft.pieceCountSource,
    skipPackaging: isSettlementRule(draft.ruleType) ? false : draft.skipPackaging,
    skipDiscount: isSettlementRule(draft.ruleType) ? false : draft.skipDiscount,
    priority: CUSTOMER_PRODUCT_RULE_DEFAULT_PRIORITY,
    isActive: draft.isActive,
  }
}

function hasKeywordOverlap(keywords: string[], excludeKeywords: string[]): boolean {
  const normalized = new Set(keywords.map((k) => k.trim().toLowerCase()).filter(Boolean))
  return excludeKeywords.some((k) => normalized.has(k.trim().toLowerCase()))
}

export function validateProductRuleDraft(draft: CustomerProductRuleDraft): string | null {
  const hasKeywords = draft.keywords.some((k) => k.trim())
  const hasProductOrKeyword = draft.productId != null || hasKeywords
  if (isProductRequired(draft.ruleType)) {
    if (!hasProductOrKeyword) return '请选择商品或填写匹配关键词'
  } else if (!hasProductOrKeyword) {
    return '请绑定商品或填写匹配关键词'
  }
  if (draft.ruleType === 'FIXED_PRICE' || draft.ruleType === 'PRICE_PER_INSTRUMENT') {
    if (draft.matchMode === 'any_price') {
      const validPrices = draft.acceptedPrices.filter((p) => p > 0)
      if (validPrices.length < 2) return '多报价模式至少需要 2 个有效价格'
    } else if (!draft.price || draft.price <= 0) {
      return '请输入有效的固定价格'
    }
    const billingMode = draft.billingMode ?? inferBillingModeFromDraft(draft)
    if (billingMode === 'PACK_NAME_SUFFIX' && !draft.keywords.some((k) => k.trim())) {
      return '按包名后缀数字计价时，匹配关键词不能为空'
    }
  } else if (draft.ruleType === 'MULTIPLIER') {
    if (!draft.multiplier || draft.multiplier < 0.01 || draft.multiplier > 99) {
      return '倍率须在 0.01 ~ 99 之间'
    }
  } else if (draft.ruleType === 'FOLD') {
    if (!draft.threshold || draft.threshold <= 0) return '折算阈值必须大于 0'
    if (!draft.foldRatio || draft.foldRatio <= 0) return '折算除数必须大于 0'
  } else if (draft.ruleType === 'EXTRA_FEE' || draft.ruleType === 'ADD_FEE') {
    if (!draft.fee || draft.fee <= 0) return '加收金额必须大于 0'
  }
  if (hasKeywordOverlap(draft.keywords, draft.excludeKeywords)) {
    return '排除关键词不能与匹配关键词重复'
  }
  if (
    draft.minInstrumentCount != null
    && draft.maxInstrumentCount != null
    && draft.minInstrumentCount > draft.maxInstrumentCount
  ) {
    return '最小件数不能大于最大件数'
  }
  return null
}

export function formatRuleMatchSummary(rule: Api.MasterData.CustomerProductRule): string {
  const parts: string[] = []
  if (rule.materials?.length) parts.push(`包材:${rule.materials.join('/')}`)
  if (rule.keywords?.length) parts.push(`词:${rule.keywords.join('/')}`)
  const exclude = rule.excludeKeywords ?? (rule as { exclude_keywords?: string[] }).exclude_keywords
  if (exclude?.length) parts.push(`排除:${exclude.join('/')}`)
  if (rule.bagSizeEquals != null) parts.push(`袋=${rule.bagSizeEquals}cm`)
  if (rule.maxBagSizeExclusive != null) parts.push(`袋<${rule.maxBagSizeExclusive}cm`)
  if (rule.minInstrumentCount != null || rule.maxInstrumentCount != null) {
    const min = rule.minInstrumentCount ?? '∞'
    const max = rule.maxInstrumentCount ?? '∞'
    parts.push(`件${min}~${max}`)
  }
  const temperature = rule.temperature ?? (rule as { temperature?: string }).temperature
  if (temperature && temperature !== 'ANY') parts.push(`温:${temperature}`)
  const ruleType = rule.ruleType ?? (rule as { rule_type?: string }).rule_type
  if (parts.length) return parts.join(' · ')
  if (ruleType === 'FOLD' || ruleType === 'EXTRA_FEE' || ruleType === 'ADD_FEE') {
    return '按关键词/场景匹配'
  }
  return '默认匹配（商品名）'
}

export function ruleDisplayName(
  rule: Api.MasterData.CustomerProductRule,
  products?: Api.MasterData.ProductRecord[],
): string {
  return resolveProductRuleLabel(rule, products)
}

type RuleLabelFn = (key: string, params?: Record<string, unknown>) => string

export function ruleTypeLabelKey(ruleType?: string): string {
  if (ruleType === 'MULTIPLIER') return 'menus.masterData.customerProductRules.multiplier'
  if (ruleType === 'PRICE_PER_INSTRUMENT') return 'menus.masterData.customerProductRules.pricePerInstrument'
  if (ruleType === 'FOLD') return 'menus.masterData.customerProductRules.foldRule'
  if (ruleType === 'EXTRA_FEE' || ruleType === 'ADD_FEE') return 'menus.masterData.customerProductRules.extraFee'
  return 'menus.masterData.customerProductRules.fixedPrice'
}

export function formatRuleValueLabel(
  rule: Api.MasterData.CustomerProductRule,
  t: RuleLabelFn,
): string {
  const ruleType = rule.ruleType ?? (rule as { rule_type?: string }).rule_type
  const matchMode = rule.matchMode ?? (rule as { match_mode?: string }).match_mode
  const accepted = rule.acceptedPrices ?? (rule as { accepted_prices?: number[] }).accepted_prices
  if (matchMode === 'any_price' && accepted?.length) {
    return t('menus.masterData.customerProductRules.valueAnyPrice', { prices: accepted.join(', ') })
  }
  if (ruleType === 'MULTIPLIER') {
    return t('menus.masterData.customerProductRules.valueMultiplier', { rate: rule.multiplier ?? 1 })
  }
  if (ruleType === 'FOLD') {
    return t('menus.masterData.customerProductRules.valueFold', {
      threshold: rule.threshold ?? 0,
      ratio: rule.foldRatio ?? rule.fold_ratio ?? 0,
    })
  }
  if (ruleType === 'EXTRA_FEE' || ruleType === 'ADD_FEE') {
    return t('menus.masterData.customerProductRules.valueExtraFee', { fee: rule.fee ?? 0 })
  }
  const price = rule.price ?? rule.fixed_price ?? 0
  const billingMode = inferBillingModeFromRecord(rule)
  if (billingMode === 'PACK_NAME_SUFFIX') {
    return `${price} 元/件(包名后缀)`
  }
  if (billingMode === 'PER_INSTRUMENT' || ruleType === 'PRICE_PER_INSTRUMENT') {
    return `${price} 元/件`
  }
  if (billingMode === 'PER_PACK') {
    return `${price} 元/包`
  }
  return t('menus.masterData.customerProductRules.valueFixed', { price })
}

function normalizeTemperature(value?: string | null): BillingTemperatureScope | '' {
  if (!value) return ''
  const upper = value.trim().toUpperCase()
  if (upper === 'HT' || upper === 'LT' || upper === 'ANY') return upper
  return ''
}

function parseDepartmentsFromRule(rule: Api.MasterData.CustomerProductRule): string[] {
  const raw = (rule as { departments?: string[] }).departments
  if (raw?.length) return [...raw]
  const conditions = (rule as { conditionsJson?: string; conditions_json?: string }).conditionsJson
    ?? (rule as { conditions_json?: string }).conditions_json
  if (!conditions) return []
  try {
    const parsed = JSON.parse(conditions) as Array<{ field?: string; value?: string | string[] }>
    for (const cond of parsed) {
      if (cond.field === 'department') {
        if (Array.isArray(cond.value)) return cond.value.map(String)
        if (typeof cond.value === 'string') return [cond.value]
      }
    }
  } catch {
    return []
  }
  return []
}
