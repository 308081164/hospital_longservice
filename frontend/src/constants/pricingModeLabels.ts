/**
 * 产品计价模式 / 计价路径的中文标签映射。
 * pricing_mode 为空时表示继承分类默认计价路径。
 */

/** 所有已知计价模式/路径值 → 中文展示标签 */
export const PRICING_MODE_LABELS: Record<string, string> = {
  // 继承默认（仅用于展示，不写入数据库）
  INHERIT_CATEGORY: '继承分类默认',

  // 引擎计价路径（product.pricing_mode / product_category.pricing_path）
  standard: '小件计价',
  dressing_cotton: '敷料计价（纸塑袋+棉球）',
  dressing_nonwoven: '敷料计价（无纺布）',
  fixed: '固定价格',
  legacy_per_piece: '按件计价',

  // 规则/分类编码别名（兼容展示）
  SMALL_ITEM: '小件计价',
  DRESSING: '敷料计价',
  DRESSING_COTTON: '敷料计价（纸塑袋+棉球）',
  DRESSING_NONWOVEN: '敷料计价（无纺布）',
  FIXED_PRICE: '固定价格',
  FIXED_OVERRIDE: '固定价格',
  MULTIPLIER: '独立倍率',
  MULTIPLY: '独立倍率',
  HIGH_TEMP: '高温计价',
  LOW_TEMP: '低温计价',
  HT_PAPER_PLASTIC: '高温纸塑袋计价',
  LT_PAPER_PLASTIC: '低温纸塑袋计价',
  HT_NON_WOVEN: '高温无纺布计价',
  LT_NON_WOVEN: '低温无纺布计价',
  EXTRA_PACK: '额外包计价',
}

/** 产品编辑时可选的计价模式覆盖项（不含继承默认） */
export const PRICING_MODE_OPTIONS = [
  { value: 'standard', label: PRICING_MODE_LABELS.standard },
  { value: 'dressing_cotton', label: PRICING_MODE_LABELS.dressing_cotton },
  { value: 'dressing_nonwoven', label: PRICING_MODE_LABELS.dressing_nonwoven },
  { value: 'fixed', label: PRICING_MODE_LABELS.fixed },
  { value: 'legacy_per_piece', label: PRICING_MODE_LABELS.legacy_per_piece },
] as const

export const PRICING_MODE_INHERIT_PLACEHOLDER = PRICING_MODE_LABELS.INHERIT_CATEGORY

/** 将枚举/路径值转为中文标签，未知值原样返回 */
export function getPricingModeLabel(value?: string | null): string {
  if (value == null || value === '') {
    return PRICING_MODE_INHERIT_PLACEHOLDER
  }
  return PRICING_MODE_LABELS[value] ?? value
}

/** 展示有效计价路径：有覆盖时标注覆盖，否则标注继承 */
export function formatEffectivePricingPath(
  pricingPath?: string | null,
  pricingMode?: string | null,
): string {
  const pathLabel = getPricingModeLabel(pricingPath)
  if (pricingMode != null && pricingMode !== '') {
    return pathLabel
  }
  if (pricingPath == null || pricingPath === '') {
    return PRICING_MODE_INHERIT_PLACEHOLDER
  }
  return `${pathLabel}（继承分类）`
}
