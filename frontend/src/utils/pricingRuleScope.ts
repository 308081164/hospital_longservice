/** 判断是否为全行业通用计价规则（非某医院特色方案） */
export function isGeneralPricingRule(rule: Api.Hospital.PricingRuleRecord): boolean {
  const hospitalName = (rule.hospitalName ?? '').trim()
  if (hospitalName) return false
  const name = (rule.name ?? '').trim()
  if (/标准|通用|默认|模板/.test(name)) return true
  if (name === '新建方案') return true
  return false
}

export function isCustomerSpecificPricingRule(rule: Api.Hospital.PricingRuleRecord): boolean {
  return !isGeneralPricingRule(rule)
}

/** 计费方案名称是否与客户规范名/别名相关 */
export function matchPricingRuleToCustomer(
  rule: Api.Hospital.PricingRuleRecord,
  customer: { canonicalName?: string; canonical_name?: string; aliases?: Array<{ alias: string }> },
): boolean {
  const canonical = (customer.canonicalName ?? customer.canonical_name ?? '').trim()
  const aliases = customer.aliases?.map((a) => a.alias.trim()).filter(Boolean) ?? []
  const candidates = [canonical, ...aliases].filter(Boolean)
  const ruleName = (rule.name ?? '').trim()
  if (!ruleName) return false
  return candidates.some(
    (n) => ruleName.includes(n) || n.includes(ruleName) || ruleName === n,
  )
}

export function findPricingRuleLabel(
  rules: Api.Hospital.PricingRuleRecord[],
  ruleId?: number | null,
): string {
  if (ruleId == null) return '—'
  const hit = rules.find((r) => r.id === ruleId)
  return hit?.name ?? `#${ruleId}`
}
