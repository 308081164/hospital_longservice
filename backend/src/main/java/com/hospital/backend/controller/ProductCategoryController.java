package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.product.SaveProductCategoryRequest;
import com.hospital.backend.dto.response.product.ProductCategoryResponse;
import com.hospital.backend.service.ProductCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/product-categories")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    @GetMapping
    public Result<List<ProductCategoryResponse>> listCategories() {
        return productCategoryService.listCategories();
    }

    @GetMapping("/{id}")
    public Result<ProductCategoryResponse> getCategory(@PathVariable Long id) {
        return productCategoryService.getCategory(id);
    }

    @PostMapping
    public Result<ProductCategoryResponse> createCategory(@Valid @RequestBody SaveProductCategoryRequest request) {
        return productCategoryService.createCategory(request);
    }

    @PutMapping("/{id}")
    public Result<ProductCategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody SaveProductCategoryRequest request) {
        return productCategoryService.updateCategory(id, request);
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> deleteCategory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force) {
        return productCategoryService.deleteCategory(id, force);
    }
}
