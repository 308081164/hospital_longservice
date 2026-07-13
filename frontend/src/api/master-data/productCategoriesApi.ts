import request from '@/utils/http'

export function listProductCategories() {
  return request.get<Api.MasterData.ProductCategoryRecord[]>({
    url: '/api/v1/product-categories',
  })
}

export function createProductCategory(payload: Api.MasterData.SaveProductCategoryPayload) {
  return request.post<Api.MasterData.ProductCategoryRecord>({
    url: '/api/v1/product-categories',
    data: payload,
  })
}

export function updateProductCategory(id: number, payload: Api.MasterData.SaveProductCategoryPayload) {
  return request.put<Api.MasterData.ProductCategoryRecord>({
    url: `/api/v1/product-categories/${id}`,
    data: payload,
  })
}

export function deleteProductCategory(id: number, force = false) {
  return request.del<{ success: boolean; softDeleted?: boolean; message?: string }>({
    url: `/api/v1/product-categories/${id}`,
    params: { force },
  })
}
