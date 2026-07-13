package com.hospital.backend.mapper;

import com.hospital.backend.entity.ProductVariant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductVariantMapper {

    void insert(ProductVariant variant);

    void updateById(ProductVariant variant);

    ProductVariant selectById(Long id);

    ProductVariant selectBySpecFingerprint(String specFingerprint);

    List<ProductVariant> selectByProductId(Long productId);

    List<ProductVariant> selectAllActive();

    long countAll();

    void upsert(ProductVariant variant);
}
