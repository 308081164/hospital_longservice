package com.hospital.backend.mapper;

import com.hospital.backend.entity.ProductMatchRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMatchRuleMapper {

    void insert(ProductMatchRule rule);

    void updateById(ProductMatchRule rule);

    void deleteByProductId(Long productId);

    void deleteById(Long id);

    List<ProductMatchRule> selectByProductId(Long productId);

    List<ProductMatchRule> selectAllActiveWithProduct();

    List<ProductMatchRule> selectAllActiveVariantRules();
}
