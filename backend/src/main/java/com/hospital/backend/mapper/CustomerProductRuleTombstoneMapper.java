package com.hospital.backend.mapper;

import com.hospital.backend.entity.CustomerProductRuleTombstone;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerProductRuleTombstoneMapper {

    int insert(CustomerProductRuleTombstone tombstone);

    List<CustomerProductRuleTombstone> selectActiveByCustomerId(@Param("customerId") Long customerId);

    List<CustomerProductRuleTombstone> selectAllActive(@Param("limit") int limit);

    CustomerProductRuleTombstone selectLatestActiveByRuleId(@Param("productRuleId") Long productRuleId);

    CustomerProductRuleTombstone selectById(@Param("id") Long id);

    int markRestored(@Param("id") Long id, @Param("restoredBy") String restoredBy);

    long countActive();
}
