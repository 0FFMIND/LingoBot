package com.lingobot.core.user.balance.controller;

import com.lingobot.core.user.balance.dto.BalanceTransactionDTO;
import com.lingobot.core.user.balance.dto.TransactionSummaryDTO;
import com.lingobot.core.user.balance.service.BalanceService;
import com.lingobot.infrastructure.common.response.ApiResponse;
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
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户余额控制器。
 *
 * 提供余额查询、交易记录分页查询、管理员调账等 REST 接口，
 * 所有响应通过 ApiResponse 统一包装。
 */
@RestController
@RequestMapping("/api/balance")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    // 获取当前登录用户的可用余额
    @GetMapping
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance() {
        BigDecimal balance = balanceService.getCurrentUserBalance();
        return ResponseEntity.ok(ApiResponse.success("获取余额成功", balance));
    }

    // 获取当前登录用户的交易记录（分页，支持按类型和时间范围筛选）
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {

        Page<BalanceTransactionDTO> result;
        if (startDate != null && endDate != null) {
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.atTime(LocalTime.MAX);
            result = balanceService.getCurrentUserTransactions(PageRequest.of(page, size), type, start, end);
        } else {
            result = balanceService.getCurrentUserTransactions(PageRequest.of(page, size), type, null, null);
        }

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

    // 获取当前登录用户的交易汇总统计（支持按时间范围筛选）
    @GetMapping("/transactions/summary")
    public ResponseEntity<ApiResponse<TransactionSummaryDTO>> getTransactionSummary(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {

        TransactionSummaryDTO summary;
        if (startDate != null && endDate != null) {
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.atTime(LocalTime.MAX);
            summary = balanceService.getCurrentUserTransactionSummary(start, end);
        } else {
            summary = balanceService.getCurrentUserTransactionSummary(null, null);
        }

        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    // 管理员手动调整指定用户的余额（仅 ADMIN 角色可访问）
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> updateUserBalance(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateBalanceRequest request) {
        balanceService.setUserBalance(userId, request.getNewBalance());
        return ResponseEntity.ok(ApiResponse.success("余额修改成功", null));
    }

    /**
     * 管理员更新用户余额请求 DTO。
     */
    @Data
    public static class UpdateBalanceRequest {
        // 新的余额值
        @NotNull(message = "余额不能为空")
        private BigDecimal newBalance;
    }
}
