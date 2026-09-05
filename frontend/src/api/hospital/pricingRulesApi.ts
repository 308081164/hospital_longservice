import request from '@/utils/http'
import { normalizePricingRules } from './pricingRules'
import type { BaseResponse } from '@/types'

function normalizeRuleRecord(record: Record<string, unknown> | null): Api.Hospital.PricingRuleRecord | null {
  if (record == null) return null
  try {
    const normalized = {
      id: record.id as number,
      name: record.name as string,
      version: record.version as string,
      description: (record.description as string) ?? undefined,
      isActive: (record.is_active ?? record.isActive) as boolean,
      hospitalName: (record.hospitalName as string) ?? undefined,
      rules: normalizePricingRules(record.rules),
      createdAt: (record.created_at ?? record.createdAt) as string,
      updatedAt: (record.updated_at ?? record.updatedAt) as string,
    }
    return normalized
  } catch {
    // skip records with invalid rules data
    return null
  }
}

export function listHospitalPricingRules(hospitalName?: string) {
  return request.get<Api.Hospital.PricingRuleRecord[]>({
    url: '/api/hospital-pricing-rules',
    params: { hospitalName },
  }).then((records) => {
    const result = (records as unknown as Record<string, unknown>[])
      .map(normalizeRuleRecord)
      .filter((r): r is Api.Hospital.PricingRuleRecord => r !== null)
    return result
  })
}

function ensureNormalized(record: Record<string, unknown> | null): Api.Hospital.PricingRuleRecord {
  const result = normalizeRuleRecord(record)
  if (result == null) throw new Error('规则数据格式异常')
  return result
}

export function getActiveHospitalPricingRule(hospitalName?: string) {
  return request.get<Record<string, unknown>>({
    url: '/api/hospital-pricing-rules/active',
    params: { hospitalName },
  }).then((record) => ensureNormalized(record as Record<string, unknown>))
}

export function createHospitalPricingRule(payload: Api.Hospital.SavePricingRulePayload) {
  return request.post<Record<string, unknown>>({
    url: '/api/hospital-pricing-rules',
    data: payload,
  }).then((record) => ensureNormalized(record as Record<string, unknown>))
}

export function updateHospitalPricingRule(id: number, payload: Partial<Api.Hospital.SavePricingRulePayload>) {
  return request.put<Record<string, unknown>>({
    url: `/api/hospital-pricing-rules/${id}`,
    data: payload,
  }).then((record) => ensureNormalized(record as Record<string, unknown>))
}

export function deleteHospitalPricingRule(id: number) {
  return request.del<{ success: boolean }>({
    url: `/api/hospital-pricing-rules/${id}`,
  })
}

/**
 * 按医院名称解析匹配计费规则
 *
 * 前端上传 Excel 提取医院名称后调用，后端按三级策略匹配：
 * 1. 精确匹配 hospitalName 字段（激活 → 最新）
 * 2. 模糊匹配规则名称（LIKE %keyword%）
 * 3. 回退到全局激活规则
 *
 * @param hospitalName 从文件名/Excel内容提取的医院名称
 * @returns 匹配到的计费规则
 */
export function resolveHospitalPricingRule(hospitalName: string) {
  return request.get<Record<string, unknown>>({
    url: '/api/hospital-pricing-rules/resolve',
    params: { hospitalName },
  }).then((record) => ensureNormalized(record as Record<string, unknown>))
}

export function listPricingRuleRevisions(ruleId: number) {
  return request.get<Array<Record<string, unknown>>>({
    url: `/api/hospital-pricing-rules/${ruleId}/revisions`,
  })
}

export function rollbackPricingRuleRevision(ruleId: number, revisionId: number, operator?: string) {
  return request.post<Record<string, unknown>>({
    url: `/api/hospital-pricing-rules/${ruleId}/revisions/${revisionId}/rollback`,
    params: operator ? { operator } : undefined,
  }).then((record) => ensureNormalized(record as Record<string, unknown>))
}

export function batchUpdateNeedleKeywords(
  ruleId: number,
  needle: {
    keywords: string[]
    keywordConfigs?: Api.Hospital.NeedleKeywordConfig[]
    threshold?: number
    foldRatio?: number
    keywordMatchMode?: string
  },
  operator?: string,
) {
  return request.put<Record<string, unknown>>({
    url: `/api/hospital-pricing-rules/${ruleId}/needle-keywords/batch`,
    data: { ...needle, operator },
  }).then((record) => ensureNormalized(record as Record<string, unknown>))
}

export function shadowComparePricing(payload: {
  productionRuleId: number
  draftRuleId?: number
  draftRules?: Api.Hospital.PricingRules
  hospitalName?: string
  sampleRows: Array<Record<string, unknown>>
}) {
  return request.post<Record<string, unknown>>({
    url: '/api/pricing/shadow-compare',
    data: payload,
  })
}
