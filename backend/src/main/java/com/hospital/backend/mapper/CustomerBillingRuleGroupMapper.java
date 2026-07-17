package com.hospital.backend.mapper;

import com.hospital.backend.entity.CustomerBillingRuleGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerBillingRuleGroupMapper {

    void insert(CustomerBillingRuleGroup group);

    void updateById(CustomerBillingRuleGroup group);

    CustomerBillingRuleGroup selectById(Long id);

    CustomerBillingRuleGroup selectByCustomerIdAndCode(
            @Param("customerId") Long customerId,
            @Param("groupCode") String groupCode);

    List<CustomerBillingRuleGroup> selectByCustomerId(Long customerId);

    void deleteByCustomerId(Long customerId);
}
