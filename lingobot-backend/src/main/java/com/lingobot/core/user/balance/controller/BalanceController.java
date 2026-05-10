package com.lingobot.core.user.balance.controller;

import com.lingobot.core.user.balance.dto.BalanceTransactionDTO;
import com.lingobot.core.user.balance.service.BalanceService;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.infrastructure.common.response.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/balance")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    @GetMapping
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance() {
        BigDecimal balance = balanceService.getCurrentUserBalance();
        return ResponseEntity.ok(ApiResponse.success("获取余额成功", balance));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<BalanceTransactionDTO> result = balanceService.getCurrentUserTransactions(
                PageRequest.of(page, size));

        Map<String, Object> response = new HashMap<>();
        response.put("content", result.getContent());
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("hasNext", result.hasNext());
        response.put("hasPrevious", result.hasPrevious());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> updateUserBalance(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateBalanceRequest request) {
        try {
            balanceService.setUserBalance(userId, request.getNewBalance());
            return ResponseEntity.ok(ApiResponse.success("余额修改成功", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, e.getMessage()));
        }
    }

    @Data
    public static class UpdateBalanceRequest {
        @NotNull(message = "余额不能为空")
        private BigDecimal newBalance;
    }
}
