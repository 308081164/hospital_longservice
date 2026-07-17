package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.logistics.LogisticsCardTransactionRequest;
import com.hospital.backend.dto.request.logistics.SaveLogisticsCardRequest;
import com.hospital.backend.dto.response.logistics.LogisticsCardResponse;
import com.hospital.backend.service.LogisticsCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/logistics-cards")
@RequiredArgsConstructor
public class LogisticsCardController {

    private final LogisticsCardService logisticsCardService;

    @GetMapping
    public Result<List<LogisticsCardResponse>> listCards(
            @RequestParam(required = false) Long customerId) {
        return logisticsCardService.listCards(customerId);
    }

    @GetMapping("/{id}")
    public Result<LogisticsCardResponse> getCard(@PathVariable Long id) {
        return logisticsCardService.getCard(id);
    }

    @PostMapping
    public Result<LogisticsCardResponse> createCard(@Valid @RequestBody SaveLogisticsCardRequest request) {
        return logisticsCardService.createCard(request);
    }

    @PutMapping("/{id}")
    public Result<LogisticsCardResponse> updateCard(
            @PathVariable Long id,
            @Valid @RequestBody SaveLogisticsCardRequest request) {
        return logisticsCardService.updateCard(id, request);
    }

    @PostMapping("/{id}/recharge")
    public Result<LogisticsCardResponse> recharge(
            @PathVariable Long id,
            @Valid @RequestBody LogisticsCardTransactionRequest request) {
        return logisticsCardService.recharge(id, request);
    }

    @PostMapping("/{id}/deduct")
    public Result<LogisticsCardResponse> deduct(
            @PathVariable Long id,
            @Valid @RequestBody LogisticsCardTransactionRequest request) {
        return logisticsCardService.deduct(id, request);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteCard(@PathVariable Long id) {
        return logisticsCardService.deleteCard(id);
    }
}
