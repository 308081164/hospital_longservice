package com.hospital.backend.mapper;

import com.hospital.backend.entity.LogisticsCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LogisticsCardMapper {

    void insert(LogisticsCard card);

    void updateById(LogisticsCard card);

    LogisticsCard selectById(Long id);

    List<LogisticsCard> selectAll(@Param("customerId") Long customerId);

    LogisticsCard selectActiveByCustomerId(@Param("customerId") Long customerId);

    void deleteById(Long id);
}
