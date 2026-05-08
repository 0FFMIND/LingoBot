package com.lingobot.core.user.redemption.controller;

import com.lingobot.core.user.auth.service.AuthService;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.infrastructure.common.response.ErrorCode;
import com.lingobot.core.user.redemption.dto.CreateRedemptionCodeRequest;
import com.lingobot.core.user.redemption.dto.RedeemCodeRequest;
import com.lingobot.core.user.redemption.dto.RedemptionCodeDTO;
import com.lingobot.core.user.redemption.service.RedemptionCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/redemption")
@RequiredArgsConstructor
public class RedemptionCodeController {
    
    private final RedemptionCodeService redemptionCodeService;
    private final AuthService authService;
    
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<Double>> getBalance() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户未登录"));
        }
        Double balance = redemptionCodeService.getUserBalance(userId);
        return ResponseEntity.ok(ApiResponse.success("获取余额成功", balance));
    }
    
    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<RedemptionCodeDTO>> redeemCode(
            @Valid @RequestBody RedeemCodeRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户未登录"));
        }
        
        try {
            RedemptionCodeDTO result = redemptionCodeService.redeemCode(request.getCode(), userId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.created("兑换成功", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, e.getMessage()));
        }
    }
    
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
                request.getExpiresInSeconds());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("兑换码创建成功", result));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/codes")
    public ResponseEntity<ApiResponse<List<RedemptionCodeDTO>>> getAllCodes() {
        List<RedemptionCodeDTO> codes = redemptionCodeService.getAllCodes();
        return ResponseEntity.ok(ApiResponse.success("获取兑换码列表成功", codes));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/codes/{id}")
    public ResponseEntity<ApiResponse<RedemptionCodeDTO>> getCodeById(@PathVariable Long id) {
        try {
            RedemptionCodeDTO code = redemptionCodeService.getCodeById(id);
            return ResponseEntity.ok(ApiResponse.success(code));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/codes/{id}")
    public ResponseEntity<?> deleteCode(@PathVariable Long id) {
        try {
            redemptionCodeService.deleteCode(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, e.getMessage()));
        }
    }
}
