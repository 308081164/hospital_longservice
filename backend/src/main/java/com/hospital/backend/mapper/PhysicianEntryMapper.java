package com.hospital.backend.mapper;

import com.hospital.backend.entity.PhysicianEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PhysicianEntryMapper {

    int insert(PhysicianEntry entry);

    int updateById(PhysicianEntry entry);

    int deleteById(@Param("id") Long id);

    PhysicianEntry selectById(@Param("id") Long id);

    List<PhysicianEntry> selectByCustomerId(@Param("customerId") Long customerId);

    int countActiveByCustomerId(@Param("customerId") Long customerId);
}
