package com.hospital.backend.mapper;

import com.hospital.backend.entity.ProductAlias;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductAliasMapper {

    void insert(ProductAlias alias);

    void deleteByProductId(Long productId);

    void deleteById(Long id);

    List<ProductAlias> selectByProductId(Long productId);

    List<ProductAlias> selectAllActive();
}
