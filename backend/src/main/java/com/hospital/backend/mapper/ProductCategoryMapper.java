package com.hospital.backend.mapper;

import com.hospital.backend.entity.ProductCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductCategoryMapper {

    void insert(ProductCategory category);

    void updateById(ProductCategory category);

    ProductCategory selectById(Long id);

    ProductCategory selectByCode(String code);

    List<ProductCategory> selectAllActive();

    List<ProductCategory> selectByParentId(Long parentId);

    long countActiveChildren(Long parentId);

    long countProductsByCategoryId(Long categoryId);

    boolean existsByCode(String code);

    boolean existsByCodeExcludingId(@Param("code") String code, @Param("id") Long id);
}
