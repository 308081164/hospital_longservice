import request from '@/utils/http'

export function fetchDefaultPricingTemplate() {
  return request.get<Record<string, unknown>>({
    url: '/api/v1/settings/default-pricing-template',
  })
}
