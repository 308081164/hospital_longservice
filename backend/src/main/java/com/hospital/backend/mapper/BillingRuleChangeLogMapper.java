package com.hospital.backend.mapper;

import com.hospital.backend.entity.BillingRuleChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BillingRuleChangeLogMapper {

    void insert(BillingRuleChangeLog log);

    List<BillingRuleChangeLog> selectByCustomerId(
            @Param("customerId") Long customerId,
            @Param("limit") int limit);
}
