package com.hospital.backend.mapper;

import com.hospital.backend.entity.HospitalReconciliationJob;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HospitalReconciliationJobMapper {

    void insert(HospitalReconciliationJob job);

    HospitalReconciliationJob selectById(Long id);

    boolean existsById(Long id);

    List<HospitalReconciliationJob> selectByHospitalNameOrderByCreatedAtDesc(String hospitalName);

    List<HospitalReconciliationJob> selectAllOrderByCreatedAtDesc();

    Integer selectMaxVersionNoByHospitalName(String hospitalName);

    Integer selectMaxVersionNoByHospitalNameAndSourceFileName(String hospitalName, String sourceFileName);

    void updateById(HospitalReconciliationJob job);

    void updateAllocationResult(@org.apache.ibatis.annotations.Param("id") Long id,
                                @org.apache.ibatis.annotations.Param("allocationResult") String allocationResult);
}
