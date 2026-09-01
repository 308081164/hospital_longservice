function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function readRuleObject(value: unknown, fieldName: string): Record<string, unknown> {
  if (!isRecord(value)) throw new Error(`${fieldName} 配置缺失或格式不正确`)
  return value
}

function toNumber(value: unknown, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function normalizeKeywordMatchMode(value: unknown): 'exact_token' | 'contains' {
  return value === 'contains' ? 'contains' : 'exact_token'
}

function normalizeBagSizes(value: unknown): Api.Hospital.BagSizeConfig[] {
  if (!Array.isArray(value)) return []
  return value.map((item) => {
    const bag = isRecord(item) ? item : {}
    return {
      size: toNumber(bag.size),
      price: toNumber(bag.price),
      keywords: Array.isArray(bag.keywords) ? (bag.keywords as string[]).filter(Boolean) : [],
      label: typeof bag.label === 'string' ? bag.label : undefined,
    }
  })
}

function normalizeTierPrices(value: unknown): Api.Hospital.TierPriceConfig[] {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => {
      const tier = isRecord(item) ? item : {}
      return {
        count: toNumber(tier.count),
        price: toNumber(tier.price),
      }
    })
    .filter((item) => item.count > 0 && item.price > 0)
    .sort((a, b) => b.count - a.count)
}

function normalizePackagingOptions(value: unknown): Api.Hospital.PackagingOptionConfig[] {
  if (!Array.isArray(value)) return []
  return value.map((item, index) => {
    const option = isRecord(item) ? item : {}
    return {
      label: typeof option.label === 'string' ? option.label : `选项${index + 1}`,
      price: toNumber(option.price),
      keywords: Array.isArray(option.keywords) ? (option.keywords as string[]).filter(Boolean) : [],
    }
  })
}

function normalizePackagingItems(value: unknown): Api.Hospital.PackagingChargeItemConfig[] {
  if (!Array.isArray(value)) return []
  return value.map((item, index) => {
    const config = isRecord(item) ? item : {}
    return {
      name: typeof config.name === 'string' ? config.name : `包装项${index + 1}`,
      keywords: Array.isArray(config.keywords) ? (config.keywords as string[]).filter(Boolean) : [],
      chargePerPack: config.chargePerPack !== undefined ? Boolean(config.chargePerPack) : true,
      options: normalizePackagingOptions(config.options),
    }
  })
}

function createDefaultHighTemperaturePaperPlasticBagSizes(): Api.Hospital.BagSizeConfig[] {
  return [
    { size: 25, price: 10.5, keywords: ['25cm', '25', '特大'] },
    { size: 20, price: 7.5, keywords: ['20cm', '20', '大'] },
    { size: 15, price: 5.5, keywords: ['15cm', '15', '中'] },
    { size: 10, price: 2.5, keywords: ['10cm', '10', '小'] },
  ]
}

function createDefaultLowTemperaturePaperPlasticBagSizes(): Api.Hospital.BagSizeConfig[] {
  return [
    { size: 30, price: 35, keywords: ['30cm', '30'] },
    { size: 25, price: 30, keywords: ['25cm', '25'] },
    { size: 20, price: 28, keywords: ['20cm', '20'] },
    { size: 15, price: 25, keywords: ['15cm', '15'] },
    { size: 10, price: 22, keywords: ['10cm', '10'] },
  ]
}

function createDefaultTierPrices(): Api.Hospital.TierPriceConfig[] {
  return [
    { count: 20, price: 300 },
    { count: 10, price: 165 },
    { count: 5, price: 88 },
  ]
}

function createEmptySpecialRules(): Api.Hospital.SpecialRulesConfig {
  return {
    fixedPrices: [],
    foldRules: [],
    extraFees: [],
  }
}

function createDefaultPackagingRules(): Api.Hospital.PackagingRulesConfig {
  return {
    enabled: true,
    selfPackedKeywords: ['仅灭菌', '医院自行打包', '自行打包', '自带包装'],
    items: [
      {
        name: '纱布棉球',
        keywords: ['纱布', '棉球', '辅料包'],
        chargePerPack: true,
        options: [
          {
            label: '大（20cm*20cm*15cm）',
            price: 2.5,
            keywords: ['20cm*20cm*15cm', '20cm×20cm×15cm', '大（20cm*20cm*15cm）', '大(20cm*20cm*15cm)'],
          },
          {
            label: '中（15cm*15cm*10cm）',
            price: 2,
            keywords: ['15cm*15cm*10cm', '15cm×15cm×10cm', '中（15cm*15cm*10cm）', '中(15cm*15cm*10cm)'],
          },
          {
            label: '小（10cm*10cm*5cm）',
            price: 1.5,
            keywords: ['10cm*10cm*5cm', '10cm×10cm×5cm', '小（10cm*10cm*5cm）', '小(10cm*10cm*5cm)', '10 cm及以下'],
          },
          {
            label: '20cm*20cm纸塑袋',
            price: 4,
            keywords: ['20cm*20cm', '20cm×20cm', '20*20'],
          },
          {
            label: '15cm*10cm纸塑袋',
            price: 2.5,
            keywords: ['15cm*10cm', '15cm×10cm', '15*10'],
          },
        ],
      },
      {
        name: 'rigip',
        keywords: ['rigip'],
        chargePerPack: true,
        options: [],
      },
      {
        name: '纸塑袋',
        keywords: ['纸塑袋'],
        chargePerPack: true,
        options: [],
      },
    ],
  }
}

function normalizeTemplateId(value: string, fallback: string): string {
  const normalized = value.trim().toLowerCase().replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '_').replace(/^_+|_+$/g, '')
  return normalized || fallback
}

function normalizeSettlementTemplates(value: unknown): Api.Hospital.SettlementLetterTemplate[] {
  if (!Array.isArray(value)) return []
  return value.map((item, index) => {
    const template = isRecord(item) ? item : {}
    const hospitalName = typeof template.hospitalName === 'string' ? template.hospitalName : ''
    const name = typeof template.name === 'string' ? template.name : hospitalName || `模板${index + 1}`
    return {
      id: typeof template.id === 'string' ? template.id : normalizeTemplateId(name, `template_${index + 1}`),
      name,
      hospitalName,
      templateSheetName: typeof template.templateSheetName === 'string' ? template.templateSheetName : '结款函',
      titleText: typeof template.titleText === 'string' ? template.titleText : '货款结算单',
      matchKeywords: Array.isArray(template.matchKeywords) ? (template.matchKeywords as string[]).filter(Boolean) : [],
      templateRef: typeof template.templateRef === 'string' ? template.templateRef : undefined,
      htmlTemplate: typeof template.htmlTemplate === 'string' ? template.htmlTemplate : undefined,
    }
  })
}

function createLegacySettlementTemplate(settlementLetter: Record<string, unknown>): Api.Hospital.SettlementLetterTemplate {
  const hospitalName = typeof settlementLetter.hospitalName === 'string' ? settlementLetter.hospitalName : ''
  const name = hospitalName || '默认结款函模板'
  return {
    id: 'default_template',
    name,
    hospitalName,
    templateSheetName: typeof settlementLetter.templateSheetName === 'string' ? settlementLetter.templateSheetName : '结款函',
    titleText: typeof settlementLetter.titleText === 'string' ? settlementLetter.titleText : '货款结算单',
    matchKeywords: hospitalName ? [hospitalName] : [],
    templateRef: 'default',
    htmlTemplate: typeof settlementLetter.htmlTemplate === 'string' ? settlementLetter.htmlTemplate : undefined,
  }
}

function convertLegacyRules(record: Record<string, unknown>): Record<string, unknown> {
  const legacyPaperPlastic = isRecord(record.paperPlastic) ? record.paperPlastic : {}
  const legacyNonWoven = isRecord(record.nonWoven) ? record.nonWoven : {}
  const legacyBagSizes = Array.isArray(legacyPaperPlastic.bagSizes)
    ? (legacyPaperPlastic.bagSizes as Array<Record<string, unknown>>)
    : []

  const highTempBagSizes = legacyBagSizes.length
    ? legacyBagSizes.map((bag) => ({
        size: toNumber(bag.size),
        price: toNumber(bag.basePrice, toNumber(bag.price)),
        keywords: Array.isArray(bag.keywords) ? (bag.keywords as string[]) : [],
      }))
    : createDefaultHighTemperaturePaperPlasticBagSizes()

  return {
    ...record,
    highTemperature: {
      nonWoven: {
        minCharge: toNumber(legacyNonWoven.minPrice, 16.5),
        flatPerPackagePrice: toNumber(legacyNonWoven.perInstrumentPrice, 5.5),
        flatRateThreshold: toNumber(legacyNonWoven.capThreshold, 3),
      },
      paperPlastic: {
        bagSizes: highTempBagSizes,
        perPackagePrice: toNumber(legacyPaperPlastic.perInstrumentPrice, 5.5),
        minCharge: toNumber(legacyPaperPlastic.minPackagePrice, 16.5),
      },
    },
    lowTemperature: {
      nonWoven: {
        tierPrices: createDefaultTierPrices(),
        remainderPerPiecePrice: 22,
        minSingleCharge: 35,
      },
      paperPlastic: {
        bagSizes: createDefaultLowTemperaturePaperPlasticBagSizes(),
        tierPrices: createDefaultTierPrices(),
        minSingleCharge: 35,
      },
    },
    packaging: createDefaultPackagingRules(),
    needle: isRecord(record.needle) ? record.needle : { threshold: 5, foldRatio: 5, keywordMatchMode: 'exact_token', keywords: ['小件', '探针', '穿刺针', '缝合针', '车针', '拔髓针', '成型片', '根管针', '根管锉', '支抗钉', '洁牙机尖', '球钻', '挖勺'] },
    cleaning: isRecord(record.cleaning)
      ? record.cleaning
      : {
          removeFirstRow: false,
          dropSummaryRows: true,
          summaryKeywords: ['合计', '小计', '总计'],
          trimPackagingMaterial: true,
          clearInstrumentColumnFormatting: false,
          recomputeTotalsWhenPriceChanges: true,
        },
    logistics: isRecord(record.logistics)
      ? record.logistics
      : {
          enabled: true,
          feePerTrip: 50,
          defaultLogisticsFee: 50,
          dayBoundaryHour: 20,
          mergeAdjacentDays: false,
          mergeWindowDays: 1,
        },
    specialRules: isRecord(record.specialRules) ? record.specialRules : createEmptySpecialRules(),
    settlementLetter: isRecord(record.settlementLetter)
      ? record.settlementLetter
      : {
          companyName: '',
          rowHeight: 20,
          dateRangeTextTemplate: '{start} 至 {end}',
          uppercaseTotalLabel: '大写金额',
          templates: [createLegacySettlementTemplate({})],
          defaultTemplateId: 'default_template',
          feeItems: [
            { key: 'sterilize', label: '灭菌费', remark: '', enabled: true, sortOrder: 1 },
            { key: 'logistics', label: '物流费', remark: '', enabled: true, sortOrder: 2 },
          ],
        },
    exportOptions: isRecord(record.exportOptions)
      ? record.exportOptions
      : {
          billFilePrefix: '账单_',
          warningFilePrefix: '异常_',
          settlementFilePrefix: '结款函_',
          includeWarningSheet: true,
          defaultPageMargin: '1cm',
        },
  }
}

export function validatePricingRules(rules: Partial<Api.Hospital.PricingRules>): { valid: boolean; errors: string[] } {
  const errors: string[] = []
  if (!rules.version) errors.push('版本号不能为空')

  if (!rules.highTemperature) {
    errors.push('缺少高温规则')
  } else {
    if (rules.highTemperature.nonWoven.minCharge <= 0) errors.push('高温无纺布最低收费必须大于 0')
    if (rules.highTemperature.nonWoven.flatPerPackagePrice <= 0) errors.push('高温无纺布件单价必须大于 0')
    if (rules.highTemperature.nonWoven.flatRateThreshold <= 0) errors.push('高温无纺布阶梯阈值必须大于 0')
    if (rules.highTemperature.paperPlastic.perPackagePrice <= 0) errors.push('高温纸塑袋件单价必须大于 0')
    if (rules.highTemperature.paperPlastic.minCharge <= 0) errors.push('高温纸塑袋最低收费必须大于 0')
    if (!rules.highTemperature.paperPlastic.bagSizes?.length) errors.push('至少需要配置一个高温纸塑袋袋型')
    rules.highTemperature.paperPlastic.bagSizes?.forEach((bag, index) => {
      if (bag.size <= 0) errors.push(`高温纸塑袋袋型 ${index + 1} 的尺寸必须大于 0`)
      if (bag.price <= 0) errors.push(`高温纸塑袋袋型 ${index + 1} 的袋费必须大于 0`)
      if (!bag.keywords.length) errors.push(`高温纸塑袋袋型 ${index + 1} 至少需要一个关键词`)
    })
  }

  if (!rules.lowTemperature) {
    errors.push('缺少低温规则')
  } else {
    if (rules.lowTemperature.nonWoven.minSingleCharge <= 0) errors.push('低温无纺布单件最低收费必须大于 0')
    if ((rules.lowTemperature.nonWoven.remainderPerPiecePrice ?? 0) <= 0) errors.push('低温无纺布阶梯余数单价必须大于 0')
    if (!rules.lowTemperature.nonWoven.tierPrices?.length) errors.push('至少需要配置一个低温无纺布阶梯价格')
    rules.lowTemperature.nonWoven.tierPrices?.forEach((tier, index) => {
      if (tier.count <= 0) errors.push(`低温无纺布阶梯 ${index + 1} 的件数必须大于 0`)
      if (tier.price <= 0) errors.push(`低温无纺布阶梯 ${index + 1} 的价格必须大于 0`)
    })

    if (!rules.lowTemperature.paperPlastic.tierPrices?.length) errors.push('至少需要配置一个低温纸塑袋阶梯价格')
    if (!rules.lowTemperature.paperPlastic.bagSizes?.length) errors.push('至少需要配置一个低温纸塑袋袋型')
    rules.lowTemperature.paperPlastic.tierPrices?.forEach((tier, index) => {
      if (tier.count <= 0) errors.push(`低温纸塑袋阶梯 ${index + 1} 的件数必须大于 0`)
      if (tier.price <= 0) errors.push(`低温纸塑袋阶梯 ${index + 1} 的价格必须大于 0`)
    })
    rules.lowTemperature.paperPlastic.bagSizes?.forEach((bag, index) => {
      if (bag.size <= 0) errors.push(`低温纸塑袋袋型 ${index + 1} 的尺寸必须大于 0`)
      if (bag.price <= 0) errors.push(`低温纸塑袋袋型 ${index + 1} 的袋费必须大于 0`)
      if (!bag.keywords.length) errors.push(`低温纸塑袋袋型 ${index + 1} 至少需要一个关键词`)
    })
  }

  if (!rules.packaging) {
    errors.push('缺少包装收费规则')
  } else {
    rules.packaging.items?.forEach((item, itemIndex) => {
      if (!item.name.trim()) errors.push(`包装收费项目 ${itemIndex + 1} 名称不能为空`)
      if (!item.keywords.length) errors.push(`包装收费项目 ${itemIndex + 1} 至少需要一个匹配关键词`)
      item.options?.forEach((option, optionIndex) => {
        if (!option.label.trim()) errors.push(`包装收费项目 ${itemIndex + 1} 的选项 ${optionIndex + 1} 名称不能为空`)
        if (option.price < 0) errors.push(`包装收费项目 ${itemIndex + 1} 的选项 ${optionIndex + 1} 价格不能小于 0`)
        if (!option.keywords.length) errors.push(`包装收费项目 ${itemIndex + 1} 的选项 ${optionIndex + 1} 至少需要一个关键词`)
      })
    })
  }

  if (!rules.needle) {
    errors.push('缺少小件识别规则')
  } else {
    if (rules.needle.threshold < 0) errors.push('小件识别触发件数不能小于 0')
    if (rules.needle.foldRatio < 0) errors.push('小件折算比例不能小于 0')
  }

  if (!rules.cleaning) errors.push('缺少清洗规则')
  else if (!rules.cleaning.summaryKeywords.length) errors.push('至少需要一个汇总关键词')

  if (!rules.logistics) {
    errors.push('缺少物流规则')
  } else {
    if (rules.logistics.dayBoundaryHour < 0 || rules.logistics.dayBoundaryHour > 23) errors.push('物流跨天时间点必须在 0-23 之间')
    if (rules.logistics.feePerTrip < 0) errors.push('物流单次费用不能小于 0')
  }

  if (!rules.settlementLetter) {
    errors.push('缺少结款函规则')
  } else {
    if (rules.settlementLetter.rowHeight <= 0) errors.push('结款函行高必须大于 0')
    if (!rules.settlementLetter.feeItems.length) errors.push('至少需要一个结款函费用项')
    if (!rules.settlementLetter.templates.length) errors.push('至少需要一个结款函模板')
  }

  if (!rules.exportOptions) {
    errors.push('缺少导出规则')
  } else {
    if (!rules.exportOptions.billFilePrefix.trim()) errors.push('账单导出文件名前缀不能为空')
    if (!rules.exportOptions.settlementFilePrefix.trim()) errors.push('结款函导出文件名前缀不能为空')
  }

  return { valid: errors.length === 0, errors }
}

export function detectBagSizeFromRules(input: string, rules: Api.Hospital.PricingRules): number | null {
  const normalized = input.replace(/\s+/g, '')
  const bagConfigs = [...rules.highTemperature.paperPlastic.bagSizes, ...rules.lowTemperature.paperPlastic.bagSizes]

  // 1. 优先尝试 mm 尺寸匹配（如 "75*260" → 75mm → 最近袋型）
  const mmMatch = normalized.match(/(\d+)\s*[*×x]\s*\d+/)
  if (mmMatch) {
    const firstNum = parseInt(mmMatch[1], 10)
    // 数字 ≥ 50 视为毫米，转为厘米后精确匹配袋型
    if (firstNum >= 50) {
      const cmSize = firstNum / 10
      // 先精确匹配
      const exact = bagConfigs.find(bag => bag.size === Math.round(cmSize))
      if (exact) return exact.size
      // 无精确匹配时取最接近的袋型（向上取整）
      const sorted = [...bagConfigs].sort((a, b) => a.size - b.size)
      const nearest = sorted.find(bag => bag.size >= cmSize)
      if (nearest) return nearest.size
      // 都匹配不到时返回最大袋型
      return sorted[sorted.length - 1]?.size ?? null
    }
  }

  // 2. 关键词匹配（兜底）
  for (const bag of bagConfigs) {
    for (const keyword of bag.keywords) {
      if (normalized.includes(keyword)) return bag.size
    }
  }
  return null
}

export function getBagSizeConfig(
  size: number,
  bagSizes: Api.Hospital.BagSizeConfig[],
): Api.Hospital.BagSizeConfig | undefined {
  return bagSizes.find((bag) => bag.size === size)
}

export function exportRulesToJson(rules: Api.Hospital.PricingRules): string {
  return JSON.stringify(rules, null, 2)
}

export function normalizePricingRules(raw: unknown): Api.Hospital.PricingRules {
  const rawRecord = readRuleObject(raw, '规则')
  const record = isRecord(rawRecord.highTemperature) && isRecord(rawRecord.lowTemperature)
    ? rawRecord
    : convertLegacyRules(rawRecord)
  const highTemperature = readRuleObject(record.highTemperature, '高温规则')
  const lowTemperature = readRuleObject(record.lowTemperature, '低温规则')
  const highTempNonWoven = readRuleObject(highTemperature.nonWoven, '高温无纺布')
  const highTempPaperPlastic = readRuleObject(highTemperature.paperPlastic, '高温纸塑袋')
  const lowTempNonWoven = readRuleObject(lowTemperature.nonWoven, '低温无纺布')
  const lowTempPaperPlastic = readRuleObject(lowTemperature.paperPlastic, '低温纸塑袋')
  const packaging = isRecord(record.packaging) ? record.packaging : {}
  const needle = readRuleObject(record.needle, '小件识别')
  const cleaning = isRecord(record.cleaning) ? record.cleaning : {}
  const logistics = isRecord(record.logistics) ? record.logistics : {}
  const settlementLetter = isRecord(record.settlementLetter) ? record.settlementLetter : {}
  const exportOptions = isRecord(record.exportOptions) ? record.exportOptions : {}
  const specialRules = isRecord(record.specialRules) ? record.specialRules : {}

  const rules: Api.Hospital.PricingRules = {
    version: typeof record.version === 'string' ? record.version : '',
    updatedAt: typeof record.updatedAt === 'string' ? record.updatedAt : undefined,
    highTemperature: {
      nonWoven: {
        minCharge: toNumber(highTempNonWoven.minCharge),
        flatPerPackagePrice: toNumber(highTempNonWoven.flatPerPackagePrice),
        flatRateThreshold: toNumber(highTempNonWoven.flatRateThreshold),
      },
      paperPlastic: {
        bagSizes: normalizeBagSizes(highTempPaperPlastic.bagSizes),
        perPackagePrice: toNumber(highTempPaperPlastic.perPackagePrice),
        minCharge: toNumber(highTempPaperPlastic.minCharge),
      },
    },
    lowTemperature: {
      nonWoven: {
        tierPrices: normalizeTierPrices(lowTempNonWoven.tierPrices),
        remainderPerPiecePrice: toNumber(lowTempNonWoven.remainderPerPiecePrice, 22),
        minSingleCharge: toNumber(lowTempNonWoven.minSingleCharge),
      },
      paperPlastic: {
        bagSizes: normalizeBagSizes(lowTempPaperPlastic.bagSizes),
        tierPrices: normalizeTierPrices(lowTempPaperPlastic.tierPrices),
      },
    },
    packaging: {
      enabled: packaging.enabled !== undefined ? Boolean(packaging.enabled) : true,
      selfPackedKeywords: Array.isArray(packaging.selfPackedKeywords) ? (packaging.selfPackedKeywords as string[]) : ['仅灭菌', '医院自行打包', '自行打包', '自带包装'],
      items: normalizePackagingItems(packaging.items).length
        ? normalizePackagingItems(packaging.items)
        : createDefaultPackagingRules().items,
    },
    needle: {
      threshold: typeof needle.threshold === 'number' ? needle.threshold : 0,
      foldRatio: typeof needle.foldRatio === 'number' ? needle.foldRatio : 0,
      keywordMatchMode: normalizeKeywordMatchMode(needle.keywordMatchMode),
      keywords: Array.isArray(needle.keywords) ? (needle.keywords as string[]) : [],
    },
    cleaning: {
      removeFirstRow: Boolean(cleaning.removeFirstRow),
      dropSummaryRows: cleaning.dropSummaryRows !== undefined ? Boolean(cleaning.dropSummaryRows) : true,
      summaryKeywords: Array.isArray(cleaning.summaryKeywords) ? (cleaning.summaryKeywords as string[]) : ['合计', '小计', '总计'],
      trimPackagingMaterial: cleaning.trimPackagingMaterial !== undefined ? Boolean(cleaning.trimPackagingMaterial) : true,
      clearInstrumentColumnFormatting: Boolean(cleaning.clearInstrumentColumnFormatting),
      recomputeTotalsWhenPriceChanges: cleaning.recomputeTotalsWhenPriceChanges !== undefined ? Boolean(cleaning.recomputeTotalsWhenPriceChanges) : true,
    },
    logistics: {
      enabled: logistics.enabled !== undefined ? Boolean(logistics.enabled) : true,
      feePerTrip: typeof logistics.feePerTrip === 'number' ? logistics.feePerTrip : 50,
      defaultLogisticsFee: typeof logistics.defaultLogisticsFee === 'number' ? logistics.defaultLogisticsFee : 50,
      dayBoundaryHour: typeof logistics.dayBoundaryHour === 'number' ? logistics.dayBoundaryHour : 20,
      mergeAdjacentDays: Boolean(logistics.mergeAdjacentDays),
      mergeWindowDays: typeof logistics.mergeWindowDays === 'number' ? logistics.mergeWindowDays : 1,
    },
    specialRules: {
      fixedPrices: Array.isArray(specialRules.fixedPrices)
        ? (specialRules.fixedPrices as Api.Hospital.SpecialFixedPriceRule[])
        : [],
      foldRules: Array.isArray(specialRules.foldRules)
        ? (specialRules.foldRules as Api.Hospital.SpecialFoldRule[]).map((rule) => ({
            ...rule,
            keywordMatchMode: normalizeKeywordMatchMode(rule.keywordMatchMode),
          }))
        : [],
      extraFees: Array.isArray(specialRules.extraFees)
        ? (specialRules.extraFees as Api.Hospital.SpecialExtraFeeRule[])
        : [],
    },
    settlementLetter: {
      companyName: typeof settlementLetter.companyName === 'string' ? settlementLetter.companyName : '',
      rowHeight: typeof settlementLetter.rowHeight === 'number' ? settlementLetter.rowHeight : 20,
      dateRangeTextTemplate: typeof settlementLetter.dateRangeTextTemplate === 'string' ? settlementLetter.dateRangeTextTemplate : '{start} 至 {end}',
      uppercaseTotalLabel: typeof settlementLetter.uppercaseTotalLabel === 'string' ? settlementLetter.uppercaseTotalLabel : '大写金额',
      templates: normalizeSettlementTemplates(settlementLetter.templates).length
        ? normalizeSettlementTemplates(settlementLetter.templates)
        : [createLegacySettlementTemplate(settlementLetter)],
      defaultTemplateId: typeof settlementLetter.defaultTemplateId === 'string' ? settlementLetter.defaultTemplateId : 'default_template',
      feeItems: Array.isArray(settlementLetter.feeItems) ? (settlementLetter.feeItems as Api.Hospital.SettlementLetterFeeItem[]) : [
        { key: 'sterilize', label: '灭菌费', remark: '', enabled: true, sortOrder: 1 },
        { key: 'logistics', label: '物流费', remark: '', enabled: true, sortOrder: 2 },
      ],
    },
    exportOptions: {
      billFilePrefix: typeof exportOptions.billFilePrefix === 'string' ? exportOptions.billFilePrefix : '账单_',
      warningFilePrefix: typeof exportOptions.warningFilePrefix === 'string' ? exportOptions.warningFilePrefix : '异常_',
      settlementFilePrefix: typeof exportOptions.settlementFilePrefix === 'string' ? exportOptions.settlementFilePrefix : '结款函_',
      includeWarningSheet: exportOptions.includeWarningSheet !== undefined ? Boolean(exportOptions.includeWarningSheet) : true,
      defaultPageMargin: typeof exportOptions.defaultPageMargin === 'string' ? exportOptions.defaultPageMargin : '1cm',
    },
    customCategoryRules: isRecord(record.customCategoryRules)
      ? (record.customCategoryRules as Record<string, Record<string, number | string | boolean>>)
      : undefined,
  }

  const validation = validatePricingRules(rules)
  if (!validation.valid) throw new Error(`规则验证失败：${validation.errors.join('；')}`)
  return rules
}

export function importRulesFromJson(jsonString: string): { success: boolean; rules?: Api.Hospital.PricingRules; error?: string } {
  try {
    const raw = JSON.parse(jsonString) as unknown
    const rules = normalizePricingRules(raw)
    return { success: true, rules }
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'JSON 解析失败：未知错误' }
  }
}

/** 从后端默认模板创建新方案规则（单一数据源，无 specialRules 硬编码） */
export async function createRulesFromDefaultTemplate(): Promise<Api.Hospital.PricingRules> {
  const { fetchDefaultPricingTemplate } = await import('@/api/settings/settingsApi')
  const template = await fetchDefaultPricingTemplate()
  const rules = normalizePricingRules(template)
  if (!rules.settlementLetter?.templates?.length) {
    rules.settlementLetter = {
      ...rules.settlementLetter,
      templates: [{
        id: 'default_template',
        name: '默认结款函模板',
        hospitalName: '',
        templateSheetName: '结款函',
        titleText: '货款结算单',
        matchKeywords: [],
        templateRef: 'default',
      }],
      defaultTemplateId: 'default_template',
      feeItems: rules.settlementLetter?.feeItems?.length
        ? rules.settlementLetter.feeItems
        : [
            { key: 'sterilize', label: '灭菌费', remark: '', enabled: true, sortOrder: 1 },
            { key: 'logistics', label: '物流费', remark: '', enabled: true, sortOrder: 2 },
          ],
      companyName: rules.settlementLetter?.companyName ?? '',
      rowHeight: rules.settlementLetter?.rowHeight ?? 20,
      dateRangeTextTemplate: rules.settlementLetter?.dateRangeTextTemplate ?? '{start} 至 {end}',
      uppercaseTotalLabel: rules.settlementLetter?.uppercaseTotalLabel ?? '大写金额',
    }
  }
  if (!rules.exportOptions) {
    rules.exportOptions = {
      billFilePrefix: '账单_',
      warningFilePrefix: '异常_',
      settlementFilePrefix: '结款函_',
      includeWarningSheet: true,
      defaultPageMargin: '1cm',
    }
  }
  return rules
}
