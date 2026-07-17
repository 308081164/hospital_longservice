package com.hospital.backend.mapper;

import com.hospital.backend.entity.RosterEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RosterEntryMapper {

    void insert(RosterEntry entry);

    void updateById(RosterEntry entry);

    void deleteById(Long id);

    RosterEntry selectById(Long id);

    List<RosterEntry> selectByCustomerId(@Param("customerId") Long customerId);

    List<RosterEntry> selectActiveByCustomerId(@Param("customerId") Long customerId);

    void deleteByCustomerId(@Param("customerId") Long customerId);
}
