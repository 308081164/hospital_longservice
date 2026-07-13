package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.product.BatchMatchPreviewRequest;
import com.hospital.backend.dto.request.product.MatchPreviewRequest;
import com.hospital.backend.dto.request.product.QuickOnboardRequest;
import com.hospital.backend.dto.request.product.SaveProductRequest;
import com.hospital.backend.dto.response.product.MatchPreviewResponse;
import com.hospital.backend.dto.response.product.ProductResponse;
import com.hospital.backend.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Result<List<ProductResponse>> listProducts(@RequestParam(required = false) Long categoryId) {
        return productService.listProducts(categoryId);
    }

    @GetMapping("/{id}")
    public Result<ProductResponse> getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    @GetMapping("/{id}/variants")
    public Result<List<Map<String, Object>>> listVariants(@PathVariable Long id) {
        return productService.listVariants(id);
    }

    @PostMapping
    public Result<ProductResponse> createProduct(@Valid @RequestBody SaveProductRequest request) {
        return productService.createProduct(request);
    }

    @PutMapping("/{id}")
    public Result<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody SaveProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Boolean>> deleteProduct(@PathVariable Long id) {
        return productService.deleteProduct(id);
    }

    @PostMapping("/match-preview")
    public Result<MatchPreviewResponse> matchPreviewPost(@Valid @RequestBody MatchPreviewRequest request) {
        return productService.matchPreview(request);
    }

    @PostMapping("/match/batch-preview")
    public Result<List<MatchPreviewResponse>> batchMatchPreview(@Valid @RequestBody BatchMatchPreviewRequest request) {
        return productService.batchMatchPreview(request);
    }

    @PostMapping("/quick-onboard")
    public Result<Map<String, Object>> quickOnboard(@Valid @RequestBody QuickOnboardRequest request) {
        return productService.quickOnboard(request);
    }

    @GetMapping("/match-preview")
    public Result<MatchPreviewResponse> matchPreviewGet(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String packName,
            @RequestParam(required = false) String packageMaterial,
            @RequestParam(required = false) String categoryNo,
            @RequestParam(required = false) Integer instrumentCount) {
        MatchPreviewRequest request = new MatchPreviewRequest();
        request.setType(type);
        request.setPackName(packName);
        request.setPackageMaterial(packageMaterial);
        request.setCategoryNo(categoryNo);
        request.setInstrumentCount(instrumentCount);
        return productService.matchPreview(request);
    }
}
