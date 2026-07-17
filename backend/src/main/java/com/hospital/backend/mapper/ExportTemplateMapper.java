package com.hospital.backend.mapper;

import com.hospital.backend.entity.ExportTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExportTemplateMapper {

    void insert(ExportTemplate template);

    void updateById(ExportTemplate template);

    ExportTemplate selectById(Long id);

    List<ExportTemplate> selectByCustomerAndType(
            @Param("customerId") Long customerId,
            @Param("templateType") String templateType);

    List<ExportTemplate> selectGlobalByType(@Param("templateType") String templateType);

    List<ExportTemplate> selectAll(@Param("customerId") Long customerId, @Param("templateType") String templateType);

    void deleteById(Long id);
}
