package com.lingobot.core.user.redemption.controller;

import com.lingobot.core.user.auth.service.AuthService;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.infrastructure.common.response.ErrorCode;
import com.lingobot.core.user.redemption.dto.CreateRedemptionCodeRequest;
import com.lingobot.core.user.redemption.dto.RedeemCodeRequest;
import com.lingobot.core.user.redemption.dto.RedemptionCodeDTO;
import com.lingobot.core.user.redemption.dto.RedemptionCodeUsageDTO;
import com.lingobot.core.user.redemption.service.RedemptionCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 兑换码控制器。
 *
 * 提供兑换码相关的 REST 接口，
 * 包括用户使用兑换码和管理员管理兑换码（创建、查询、删除）功能。
 */
@Slf4j
@RestController
@RequestMapping("/api/redemption")
@RequiredArgsConstructor
public class RedemptionCodeController {
    
    // 兑换码服务
    private final RedemptionCodeService redemptionCodeService;
    // 认证服务，用于获取当前登录用户
    private final AuthService authService;
    
    // 用户使用兑换码兑换点数
    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<RedemptionCodeDTO>> redeemCode(
            @Valid @RequestBody RedeemCodeRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户未登录"));
        }
        
        RedemptionCodeDTO result = redemptionCodeService.redeemCode(request.getCode(), userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("兑换成功", result));
    }
    
    // 管理员创建兑换码（需 ADMIN 角色）
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/codes")
    public ResponseEntity<ApiResponse<RedemptionCodeDTO>> createCode(
            @Valid @RequestBody CreateRedemptionCodeRequest request) {
        Long creatorId = authService.getCurrentUserId();
        
        if (creatorId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "管理员未登录"));
        }
        
        RedemptionCodeDTO result = redemptionCodeService.createCode(
                request.getPoints(), 
                creatorId, 
                request.getExpiresInSeconds(),
                request.getMaxUsages());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("兑换码创建成功", result));
    }
    
    // 管理员获取所有兑换码列表（需 ADMIN 角色）
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/codes")
    public ResponseEntity<ApiResponse<List<RedemptionCodeDTO>>> getAllCodes() {
        List<RedemptionCodeDTO> codes = redemptionCodeService.getAllCodes();
        return ResponseEntity.ok(ApiResponse.success("获取兑换码列表成功", codes));
    }
    
    // 管理员根据 ID 获取兑换码详情（需 ADMIN 角色）
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/codes/{id}")
    public ResponseEntity<ApiResponse<RedemptionCodeDTO>> getCodeById(@PathVariable Long id) {
        RedemptionCodeDTO code = redemptionCodeService.getCodeById(id);
        return ResponseEntity.ok(ApiResponse.success(code));
    }

    // 管理员获取兑换码的使用记录列表（需 ADMIN 角色）
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/codes/{id}/usages")
    public ResponseEntity<ApiResponse<List<RedemptionCodeUsageDTO>>> getCodeUsages(@PathVariable Long id) {
        List<RedemptionCodeUsageDTO> usages = redemptionCodeService.getCodeUsages(id);
        return ResponseEntity.ok(ApiResponse.success("获取使用记录成功", usages));
    }

    // 管理员删除兑换码（需 ADMIN 角色，仅可删除未被使用过的兑换码）
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/codes/{id}")
    public ResponseEntity<Void> deleteCode(@PathVariable Long id) {
        redemptionCodeService.deleteCode(id);
        return ResponseEntity.noContent().build();
    }
}
