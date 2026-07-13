package com.hospital.backend.mapper;

import com.hospital.backend.entity.HospitalReconciliationExportLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HospitalReconciliationExportLogMapper {

    void insert(HospitalReconciliationExportLog log);

    List<HospitalReconciliationExportLog> selectByJobIdOrderByCreatedAtAsc(Long jobId);
}
