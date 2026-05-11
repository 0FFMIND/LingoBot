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

    // 分页获取当前用户的交易记录
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

    // 管理员手动调整指定用户的余额（仅 ADMIN 角色可访问）
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
