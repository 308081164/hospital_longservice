package com.hospital.backend.service;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.logistics.LogisticsCardTransactionRequest;
import com.hospital.backend.dto.request.logistics.SaveLogisticsCardRequest;
import com.hospital.backend.dto.response.logistics.LogisticsCardResponse;

import java.util.List;

public interface LogisticsCardService {

    Result<List<LogisticsCardResponse>> listCards(Long customerId);

    Result<LogisticsCardResponse> getCard(Long id);

    Result<LogisticsCardResponse> createCard(SaveLogisticsCardRequest request);

    Result<LogisticsCardResponse> updateCard(Long id, SaveLogisticsCardRequest request);

    Result<LogisticsCardResponse> recharge(Long id, LogisticsCardTransactionRequest request);

    Result<LogisticsCardResponse> deduct(Long id, LogisticsCardTransactionRequest request);

    Result<Boolean> deleteCard(Long id);
}
