package com.hospital.backend.mapper;

import com.hospital.backend.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    void insert(Product product);

    void updateById(Product product);

    Product selectById(Long id);

    List<Product> selectAllActive();

    List<Product> selectByCategoryId(Long categoryId);

    long countByCategoryId(Long categoryId);

    void softDeleteById(Long id);
}
