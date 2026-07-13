package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.product.SaveProductCategoryRequest;
import com.hospital.backend.dto.response.product.ProductCategoryResponse;

import java.util.List;
import java.util.Map;

public interface ProductCategoryService {

    Result<List<ProductCategoryResponse>> listCategories();

    Result<ProductCategoryResponse> getCategory(Long id);

    Result<ProductCategoryResponse> createCategory(SaveProductCategoryRequest request);

    Result<ProductCategoryResponse> updateCategory(Long id, SaveProductCategoryRequest request);

    Result<Map<String, Object>> deleteCategory(Long id, boolean force);
}
