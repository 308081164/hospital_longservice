import request from '@/utils/http'

export function listCustomers() {
  return request.get<Api.MasterData.CustomerRecord[]>({
    url: '/api/v1/customers',
  })
}

export function getCustomer(id: number) {
  return request.get<Api.MasterData.CustomerRecord>({
    url: `/api/v1/customers/${id}`,
  })
}

export function createCustomer(payload: Api.MasterData.SaveCustomerPayload) {
  return request.post<Api.MasterData.CustomerRecord>({
    url: '/api/v1/customers',
    data: payload,
  })
}

export function updateCustomer(id: number, payload: Api.MasterData.SaveCustomerPayload) {
  return request.put<Api.MasterData.CustomerRecord>({
    url: `/api/v1/customers/${id}`,
    data: payload,
  })
}

export function deleteCustomer(id: number) {
  return request.del<{ success: boolean }>({
    url: `/api/v1/customers/${id}`,
  })
}

export function listCustomerProductRules(customerId: number) {
  return request.get<Api.MasterData.CustomerProductRuleRecord[]>({
    url: `/api/v1/customers/${customerId}/product-rules`,
  })
}

export function createCustomerProductRule(
  customerId: number,
  payload: Api.MasterData.SaveCustomerProductRulePayload,
) {
  return request.post<Api.MasterData.CustomerProductRuleRecord>({
    url: `/api/v1/customers/${customerId}/product-rules`,
    data: payload,
  })
}

export function updateCustomerProductRule(
  customerId: number,
  ruleId: number,
  payload: Api.MasterData.SaveCustomerProductRulePayload,
) {
  return request.put<Api.MasterData.CustomerProductRuleRecord>({
    url: `/api/v1/customers/${customerId}/product-rules/${ruleId}`,
    data: payload,
  })
}

export function deleteCustomerProductRule(customerId: number, ruleId: number) {
  return request.del<{ success: boolean }>({
    url: `/api/v1/customers/${customerId}/product-rules/${ruleId}`,
  })
}

export function listCustomerBillingPolicies(customerId: number) {
  return request.get<Api.MasterData.CustomerBillingPolicyRecord[]>({
    url: `/api/v1/customers/${customerId}/billing-policies`,
  })
}

export function createCustomerBillingPolicy(
  customerId: number,
  payload: Api.MasterData.SaveCustomerBillingPolicyPayload,
) {
  return request.post<Api.MasterData.CustomerBillingPolicyRecord>({
    url: `/api/v1/customers/${customerId}/billing-policies`,
    data: payload,
  })
}

export function updateCustomerBillingPolicy(
  customerId: number,
  policyId: number,
  payload: Api.MasterData.SaveCustomerBillingPolicyPayload,
) {
  return request.put<Api.MasterData.CustomerBillingPolicyRecord>({
    url: `/api/v1/customers/${customerId}/billing-policies/${policyId}`,
    data: payload,
  })
}

export function deleteCustomerBillingPolicy(customerId: number, policyId: number) {
  return request.del<{ success: boolean }>({
    url: `/api/v1/customers/${customerId}/billing-policies/${policyId}`,
  })
}
