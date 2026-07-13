import request from '@/utils/http'

export function listProducts(categoryId?: number) {
  return request.get<Api.MasterData.ProductRecord[]>({
    url: '/api/v1/products',
    params: categoryId != null ? { categoryId } : undefined,
  })
}

export function getProduct(id: number) {
  return request.get<Api.MasterData.ProductRecord>({
    url: `/api/v1/products/${id}`,
  })
}

export function createProduct(payload: Api.MasterData.SaveProductPayload) {
  return request.post<Api.MasterData.ProductRecord>({
    url: '/api/v1/products',
    data: payload,
  })
}

export function updateProduct(id: number, payload: Api.MasterData.SaveProductPayload) {
  return request.put<Api.MasterData.ProductRecord>({
    url: `/api/v1/products/${id}`,
    data: payload,
  })
}

export function deleteProduct(id: number) {
  return request.del<{ success: boolean }>({
    url: `/api/v1/products/${id}`,
  })
}

export function matchPreview(payload: Api.MasterData.MatchPreviewPayload) {
  return request.post<Api.MasterData.MatchPreviewResult>({
    url: '/api/v1/products/match-preview',
    data: payload,
  })
}

export function listProductVariants(productId: number) {
  return request.get<Api.MasterData.ProductVariantRecord[]>({
    url: `/api/v1/products/${productId}/variants`,
  })
}

export function quickOnboardProduct(payload: Record<string, unknown>) {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/products/quick-onboard',
    data: payload,
  })
}
