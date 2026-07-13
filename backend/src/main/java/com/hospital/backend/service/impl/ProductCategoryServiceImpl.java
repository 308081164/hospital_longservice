package com.hospital.backend.service.impl;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.product.SaveProductCategoryRequest;
import com.hospital.backend.dto.response.product.ProductCategoryResponse;
import com.hospital.backend.entity.ProductCategory;
import com.hospital.backend.mapper.ProductCategoryMapper;
import com.hospital.backend.service.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryMapper categoryMapper;

    @Override
    public Result<List<ProductCategoryResponse>> listCategories() {
        List<ProductCategoryResponse> data = categoryMapper.selectAllActive().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Result.success(data);
    }

    @Override
    public Result<ProductCategoryResponse> getCategory(Long id) {
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) {
            return Result.fail(404, "产品分类不存在");
        }
        return Result.success(toResponse(category));
    }

    @Override
    @Transactional
    public Result<ProductCategoryResponse> createCategory(SaveProductCategoryRequest request) {
        if (categoryMapper.existsByCode(request.getCode())) {
            return Result.fail(400, "分类编码已存在");
        }
        if (request.getParentId() != null && categoryMapper.selectById(request.getParentId()) == null) {
            return Result.fail(400, "父分类不存在");
        }

        ProductCategory category = new ProductCategory();
        applyRequest(category, request);
        category.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        categoryMapper.insert(category);
        return Result.success(toResponse(categoryMapper.selectById(category.getId())));
    }

    @Override
    @Transactional
    public Result<ProductCategoryResponse> updateCategory(Long id, SaveProductCategoryRequest request) {
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) {
            return Result.fail(404, "产品分类不存在");
        }
        if (categoryMapper.existsByCodeExcludingId(request.getCode(), id)) {
            return Result.fail(400, "分类编码已存在");
        }
        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                return Result.fail(400, "父分类不能是自身");
            }
            if (categoryMapper.selectById(request.getParentId()) == null) {
                return Result.fail(400, "父分类不存在");
            }
        }

        applyRequest(category, request);
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }
        categoryMapper.updateById(category);
        return Result.success(toResponse(categoryMapper.selectById(id)));
    }

    @Override
    @Transactional
    public Result<Map<String, Object>> deleteCategory(Long id, boolean force) {
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) {
            return Result.fail(404, "产品分类不存在");
        }

        long productCount = categoryMapper.countProductsByCategoryId(id);
        if (productCount > 0 && !force) {
            return Result.fail(400, "该分类下仍有 " + productCount + " 个产品，无法删除。请先移除或迁移产品，或使用 force=true 强制软删除子分类。");
        }

        long childCount = categoryMapper.countActiveChildren(id);
        if (childCount > 0) {
            softDeleteCategoryTree(id);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("softDeleted", true);
            result.put("message", "已软删除该分类及其 " + childCount + " 个子分类");
            return Result.success(result);
        }

        category.setIsActive(false);
        category.setDeletedAt(LocalDateTime.now());
        categoryMapper.updateById(category);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("softDeleted", true);
        return Result.success(result);
    }

    private void softDeleteCategoryTree(Long categoryId) {
        ProductCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            return;
        }
        for (ProductCategory child : categoryMapper.selectByParentId(categoryId)) {
            softDeleteCategoryTree(child.getId());
        }
        category.setIsActive(false);
        category.setDeletedAt(LocalDateTime.now());
        categoryMapper.updateById(category);
    }

    private void applyRequest(ProductCategory category, SaveProductCategoryRequest request) {
        category.setCode(request.getCode());
        category.setName(request.getName());
        category.setParentId(request.getParentId());
        category.setPricingPath(request.getPricingPath());
        category.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
    }

    private ProductCategoryResponse toResponse(ProductCategory category) {
        return ProductCategoryResponse.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .parentId(category.getParentId())
                .pricingPath(category.getPricingPath())
                .sortOrder(category.getSortOrder())
                .isActive(category.getIsActive())
                .productCount(categoryMapper.countProductsByCategoryId(category.getId()))
                .childCount(categoryMapper.countActiveChildren(category.getId()))
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
