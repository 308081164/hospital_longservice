package com.hospital.backend.mapper;

import com.hospital.backend.entity.ExternalInstrument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExternalInstrumentMapper {

    void insert(ExternalInstrument instrument);

    void updateById(ExternalInstrument instrument);

    void deleteById(Long id);

    ExternalInstrument selectById(Long id);

    List<ExternalInstrument> selectCatalogByCustomerId(@Param("customerId") Long customerId);

    List<ExternalInstrument> selectByJobId(@Param("jobId") Long jobId);

    ExternalInstrument selectByCustomerAndCategoryNo(
            @Param("customerId") Long customerId,
            @Param("categoryNo") String categoryNo);
}
