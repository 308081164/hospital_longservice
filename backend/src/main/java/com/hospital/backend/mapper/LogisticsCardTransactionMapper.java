package com.hospital.backend.mapper;

import com.hospital.backend.entity.LogisticsCardTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LogisticsCardTransactionMapper {

    void insert(LogisticsCardTransaction transaction);

    List<LogisticsCardTransaction> selectByCardId(@Param("cardId") Long cardId);
}
