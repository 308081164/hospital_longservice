package com.hospital.backend.mapper;

import com.hospital.backend.entity.DepartmentEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DepartmentEntryMapper {

    int insert(DepartmentEntry entry);

    int updateById(DepartmentEntry entry);

    int deleteById(@Param("id") Long id);

    DepartmentEntry selectById(@Param("id") Long id);

    List<DepartmentEntry> selectByCustomerId(@Param("customerId") Long customerId);

    int countActiveByCustomerId(@Param("customerId") Long customerId);
}
