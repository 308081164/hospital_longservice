package com.hospital.backend.service.impl;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.logistics.LogisticsCardTransactionRequest;
import com.hospital.backend.dto.request.logistics.SaveLogisticsCardRequest;
import com.hospital.backend.dto.response.logistics.LogisticsCardResponse;
import com.hospital.backend.dto.response.logistics.LogisticsCardTransactionResponse;
import com.hospital.backend.entity.LogisticsCard;
import com.hospital.backend.entity.LogisticsCardTransaction;
import com.hospital.backend.mapper.LogisticsCardMapper;
import com.hospital.backend.mapper.LogisticsCardTransactionMapper;
import com.hospital.backend.service.LogisticsCardService;
import com.hospital.backend.service.LogisticsFeeCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LogisticsCardServiceImpl implements LogisticsCardService {

    private final LogisticsCardMapper logisticsCardMapper;
    private final LogisticsCardTransactionMapper transactionMapper;

    @Override
    public Result<List<LogisticsCardResponse>> listCards(Long customerId) {
        return Result.success(logisticsCardMapper.selectAll(customerId).stream()
                .map(this::toResponseWithoutTransactions)
                .toList());
    }

    @Override
    public Result<LogisticsCardResponse> getCard(Long id) {
        LogisticsCard card = logisticsCardMapper.selectById(id);
        if (card == null) {
            return Result.fail(404, "Logistics card not found");
        }
        return Result.success(toResponseWithTransactions(card));
    }

    @Override
    @Transactional
    public Result<LogisticsCardResponse> createCard(SaveLogisticsCardRequest request) {
        LogisticsCard card = new LogisticsCard();
        card.setCustomerId(request.getCustomerId());
        card.setName(request.getName());
        double initial = request.getInitialBalance() != null ? request.getInitialBalance() : 0;
        card.setInitialBalance(initial);
        card.setBalance(initial);
        card.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        logisticsCardMapper.insert(card);
        if (initial > 0) {
            insertTransaction(card.getId(), "RECHARGE", initial, initial, null, "开卡充值");
        }
        return Result.success(toResponseWithTransactions(logisticsCardMapper.selectById(card.getId())));
    }

    @Override
    @Transactional
    public Result<LogisticsCardResponse> updateCard(Long id, SaveLogisticsCardRequest request) {
        LogisticsCard existing = logisticsCardMapper.selectById(id);
        if (existing == null) {
            return Result.fail(404, "Logistics card not found");
        }
        existing.setName(request.getName());
        if (request.getIsActive() != null) {
            existing.setIsActive(request.getIsActive());
        }
        logisticsCardMapper.updateById(existing);
        return Result.success(toResponseWithTransactions(logisticsCardMapper.selectById(id)));
    }

    @Override
    @Transactional
    public Result<LogisticsCardResponse> recharge(Long id, LogisticsCardTransactionRequest request) {
        LogisticsCard card = logisticsCardMapper.selectById(id);
        if (card == null) {
            return Result.fail(404, "Logistics card not found");
        }
        double amount = request.getAmount();
        double newBalance = LogisticsFeeCalculator.roundCurrency(
                (card.getBalance() != null ? card.getBalance() : 0) + amount);
        card.setBalance(newBalance);
        logisticsCardMapper.updateById(card);
        insertTransaction(id, "RECHARGE", amount, newBalance, null, request.getRemark());
        return Result.success(toResponseWithTransactions(logisticsCardMapper.selectById(id)));
    }

    @Override
    @Transactional
    public Result<LogisticsCardResponse> deduct(Long id, LogisticsCardTransactionRequest request) {
        LogisticsCard card = logisticsCardMapper.selectById(id);
        if (card == null) {
            return Result.fail(404, "Logistics card not found");
        }
        double balance = card.getBalance() != null ? card.getBalance() : 0;
        double amount = request.getAmount();
        if (amount > balance) {
            return Result.fail(400, "物流卡余额不足");
        }
        double newBalance = LogisticsFeeCalculator.roundCurrency(balance - amount);
        card.setBalance(newBalance);
        logisticsCardMapper.updateById(card);
        insertTransaction(id, "DEDUCT", amount, newBalance, null, request.getRemark());
        return Result.success(toResponseWithTransactions(logisticsCardMapper.selectById(id)));
    }

    @Override
    @Transactional
    public Result<Boolean> deleteCard(Long id) {
        LogisticsCard existing = logisticsCardMapper.selectById(id);
        if (existing == null) {
            return Result.fail(404, "Logistics card not found");
        }
        logisticsCardMapper.deleteById(id);
        return Result.success(true);
    }

    private void insertTransaction(
            Long cardId,
            String type,
            double amount,
            double balanceAfter,
            Long jobId,
            String remark) {
        LogisticsCardTransaction tx = new LogisticsCardTransaction();
        tx.setCardId(cardId);
        tx.setTransactionType(type);
        tx.setAmount(amount);
        tx.setBalanceAfter(balanceAfter);
        tx.setJobId(jobId);
        tx.setRemark(remark);
        transactionMapper.insert(tx);
    }

    private LogisticsCardResponse toResponseWithoutTransactions(LogisticsCard card) {
        return LogisticsCardResponse.builder()
                .id(card.getId())
                .customerId(card.getCustomerId())
                .name(card.getName())
                .balance(card.getBalance())
                .initialBalance(card.getInitialBalance())
                .isActive(card.getIsActive())
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .build();
    }

    private LogisticsCardResponse toResponseWithTransactions(LogisticsCard card) {
        List<LogisticsCardTransactionResponse> transactions = transactionMapper.selectByCardId(card.getId()).stream()
                .map(tx -> LogisticsCardTransactionResponse.builder()
                        .id(tx.getId())
                        .cardId(tx.getCardId())
                        .transactionType(tx.getTransactionType())
                        .amount(tx.getAmount())
                        .balanceAfter(tx.getBalanceAfter())
                        .jobId(tx.getJobId())
                        .remark(tx.getRemark())
                        .createdAt(tx.getCreatedAt())
                        .build())
                .toList();
        return LogisticsCardResponse.builder()
                .id(card.getId())
                .customerId(card.getCustomerId())
                .name(card.getName())
                .balance(card.getBalance())
                .initialBalance(card.getInitialBalance())
                .isActive(card.getIsActive())
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .transactions(transactions)
                .build();
    }
}
