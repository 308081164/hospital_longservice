package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.product.BatchMatchPreviewRequest;
import com.hospital.backend.dto.request.product.MatchPreviewRequest;
import com.hospital.backend.dto.request.product.QuickOnboardRequest;
import com.hospital.backend.dto.request.product.SaveProductRequest;
import com.hospital.backend.dto.response.product.MatchPreviewResponse;
import com.hospital.backend.dto.response.product.ProductResponse;

import java.util.List;
import java.util.Map;

public interface ProductService {

    Result<List<ProductResponse>> listProducts(Long categoryId);

    Result<ProductResponse> getProduct(Long id);

    Result<ProductResponse> createProduct(SaveProductRequest request);

    Result<ProductResponse> updateProduct(Long id, SaveProductRequest request);

    Result<Map<String, Boolean>> deleteProduct(Long id);

    Result<MatchPreviewResponse> matchPreview(MatchPreviewRequest request);

    Result<List<MatchPreviewResponse>> batchMatchPreview(BatchMatchPreviewRequest request);

    Result<Map<String, Object>> quickOnboard(QuickOnboardRequest request);

    Result<List<Map<String, Object>>> listVariants(Long productId);
}
