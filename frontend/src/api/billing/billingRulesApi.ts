import request from '@/utils/http'

export function simulateBillingRule(payload: {
  customerId: number
  ruleId?: number
  hospitalName: string
  sampleRow: Record<string, unknown>
  operatorName?: string
}) {
  return request.post<Api.Billing.RuleSimulateResult>({
    url: '/api/v1/billing-rules/simulate',
    data: payload,
  })
}

export function validateRuleConflicts(payload: {
  customerId: number
  rules: Record<string, unknown>[]
}) {
  return request.post<{ hasConflicts: boolean; conflicts: unknown[] }>({
    url: '/api/v1/billing-rules/validate-conflicts',
    data: payload,
  })
}

export function listRuleChangeLog(customerId: number, limit = 50) {
  return request.get<Api.Billing.RuleChangeLogEntry[]>({
    url: '/api/v1/billing-rules/change-log',
    params: { customerId, limit },
  })
}

export function previewRuleImport(payload: {
  customerId: number
  rows: Record<string, unknown>[]
}) {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/billing-rules/import/preview',
    data: payload,
  })
}

export function confirmRuleImport(payload: {
  customerId: number
  rows: Record<string, unknown>[]
  operatorName?: string
}) {
  return request.post<{ importedCount: number }>({
    url: '/api/v1/billing-rules/import/confirm',
    data: payload,
  })
}

export function listBuiltinRuleTemplates() {
  return request.get<Record<string, unknown>[]>({
    url: '/api/v1/billing-rules/templates',
  })
}

export function copyRulesFromCustomer(
  targetId: number,
  sourceId: number,
  operatorName?: string,
) {
  return request.post<{ copiedCount: number }>({
    url: `/api/v1/billing-rules/customers/${targetId}/copy-from/${sourceId}`,
    params: operatorName ? { operatorName } : undefined,
  })
}

export function splitReconciliationDaily(jobId: number) {
  return request.post<Record<string, unknown>>({
    url: `/api/hospital-reconciliations/${jobId}/split-daily`,
  })
}

export function getInstrumentAuditReport(jobId: number) {
  return request.get<Record<string, unknown>>({
    url: `/api/hospital-reconciliations/${jobId}/export-instrument-audit`,
  })
}
