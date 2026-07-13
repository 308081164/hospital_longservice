package com.hospital.backend.mapper;

import com.hospital.backend.entity.HospitalPricingRuleRevision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HospitalPricingRuleRevisionMapper {

    void insert(HospitalPricingRuleRevision revision);

    List<HospitalPricingRuleRevision> selectByRuleId(@Param("ruleId") Long ruleId);

    HospitalPricingRuleRevision selectByRuleIdAndVersion(
            @Param("ruleId") Long ruleId,
            @Param("version") String version);

    HospitalPricingRuleRevision selectById(Long id);
}
