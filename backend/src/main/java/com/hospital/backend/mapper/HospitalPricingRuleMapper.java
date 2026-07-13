package com.hospital.backend.mapper;

import com.hospital.backend.entity.HospitalPricingRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface HospitalPricingRuleMapper {

    void insert(HospitalPricingRule rule);

    void updateById(HospitalPricingRule rule);

    HospitalPricingRule selectById(Long id);

    void deleteById(Long id);

    boolean existsById(Long id);

    HospitalPricingRule selectByIsActiveTrue();

    List<HospitalPricingRule> selectByHospitalNameOrderByUpdatedAtDesc(String hospitalName);

    HospitalPricingRule selectByIsActiveTrueAndHospitalName(String hospitalName);

    List<HospitalPricingRule> selectByNameContainingOrderByIsActiveDescUpdatedAtDesc(String keyword);

    List<HospitalPricingRule> selectByPlanNameOrderByUpdatedAtDesc(String planName);

    /** 模糊搜索：同时匹配 name、hospital_name、plan_name 三个字段 */
    List<HospitalPricingRule> selectByMultiFieldContainingOrderByIsActiveDescUpdatedAtDesc(String keyword);

    List<HospitalPricingRule> selectAll();

    List<HospitalPricingRule> selectAllOrderByUpdatedAtDesc();
}
