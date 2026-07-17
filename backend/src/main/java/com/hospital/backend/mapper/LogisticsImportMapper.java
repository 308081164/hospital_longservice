package com.hospital.backend.mapper;

import com.hospital.backend.entity.LogisticsImport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LogisticsImportMapper {

    void insert(LogisticsImport record);

    void updateById(LogisticsImport record);

    LogisticsImport selectById(Long id);

    List<LogisticsImport> selectByCustomerId(@Param("customerId") Long customerId);

    List<LogisticsImport> selectByCustomerAndMonth(
            @Param("customerId") Long customerId,
            @Param("billingMonth") String billingMonth);

    List<LogisticsImport> selectByJobId(@Param("jobId") Long jobId);

    int linkJobByCustomerAndMonth(
            @Param("customerId") Long customerId,
            @Param("billingMonth") String billingMonth,
            @Param("jobId") Long jobId);

    void deleteById(Long id);
}
