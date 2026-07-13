package com.hospital.backend.mapper;

import com.hospital.backend.entity.CustomerBillingPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerBillingPolicyMapper {

    void insert(CustomerBillingPolicy policy);

    void updateById(CustomerBillingPolicy policy);

    void deleteById(@Param("id") Long id);

    void deleteByCustomerIdAndType(@Param("customerId") Long customerId, @Param("policyType") String policyType);

    CustomerBillingPolicy selectById(@Param("id") Long id);

    List<CustomerBillingPolicy> selectByCustomerId(@Param("customerId") Long customerId);

    List<CustomerBillingPolicy> selectByCustomerIdAndType(
            @Param("customerId") Long customerId,
            @Param("policyType") String policyType);
}
