import request from '@/utils/http'

export interface ClerkRuleCustomerSummary {
  customerCode: string
  customerName: string
  notes?: string
  ruleCount: number
  activeRuleCount: number
  pendingMigrationCount: number
}

export interface ClerkRuleCustomerDetail {
  baseline: Record<string, unknown>
  compiled?: Record<string, unknown>
}

export function listClerkRuleCustomers() {
  return request.get<ClerkRuleCustomerSummary[]>({
    url: '/api/v1/clerk-rules/customers'
  })
}

export function getClerkRuleCustomer(customerCode: string) {
  return request.get<ClerkRuleCustomerDetail>({
    url: `/api/v1/clerk-rules/customers/${customerCode}`
  })
}

export function getClerkRuleIndex() {
  return request.get<{ baselineHash: string; customerCount: number; customerCodes: string[] }>({
    url: '/api/v1/clerk-rules/index'
  })
}
