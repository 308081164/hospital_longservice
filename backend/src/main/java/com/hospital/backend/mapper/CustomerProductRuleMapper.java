package com.hospital.backend.mapper;

import com.hospital.backend.entity.CustomerProductRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerProductRuleMapper {

    void insert(CustomerProductRule rule);

    void deleteByCustomerId(Long customerId);

    List<CustomerProductRule> selectByCustomerId(Long customerId);

    CustomerProductRule selectById(@Param("id") Long id);

    CustomerProductRule selectByCustomerIdAndProductId(
            @Param("customerId") Long customerId,
            @Param("productId") Long productId);

    void updateById(CustomerProductRule rule);

    void deleteById(@Param("id") Long id);

    long countByCustomerIdAndName(@Param("customerId") Long customerId, @Param("name") String name);
}
