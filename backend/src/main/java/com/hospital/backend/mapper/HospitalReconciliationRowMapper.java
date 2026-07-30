package com.hospital.backend.mapper;

import com.hospital.backend.entity.HospitalReconciliationRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HospitalReconciliationRowMapper {

    void insert(HospitalReconciliationRow row);

    void batchInsert(@Param("list") List<HospitalReconciliationRow> rows);

    List<HospitalReconciliationRow> selectByJobIdOrderBySheetNameAscRowNumberAsc(Long jobId);

    List<HospitalReconciliationRow> selectPageByJobId(@Param("jobId") Long jobId, @Param("offset") int offset, @Param("size") int size);

    List<HospitalReconciliationRow> selectPageByJobIdAndSheetName(
            @Param("jobId") Long jobId,
            @Param("sheetName") String sheetName,
            @Param("offset") int offset,
            @Param("size") int size);

    int countByJobId(@Param("jobId") Long jobId);

    int countByJobIdAndSheetName(@Param("jobId") Long jobId, @Param("sheetName") String sheetName);

    void deleteByJobId(Long jobId);
}
