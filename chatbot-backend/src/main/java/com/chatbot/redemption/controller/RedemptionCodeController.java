package com.lingobot.redemption.controller;

import com.lingobot.auth.service.AuthService;
import com.lingobot.common.response.ApiResponse;
import com.lingobot.redemption.dto.CreateRedemptionCodeRequest;
import com.lingobot.redemption.dto.RedeemCodeRequest;
import com.lingobot.redemption.dto.RedemptionCodeDTO;
import com.lingobot.redemption.service.RedemptionCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public ResponseEntity<ApiResponse<Integer>> getBalance() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户未登�?, null));
        }
        Integer balance = redemptionCodeService.getUserBalance(userId);
        return ResponseEntity.ok(ApiResponse.success("获取余额成功", balance));
    }
    
    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<RedemptionCodeDTO>> redeemCode(
            @Valid @RequestBody RedeemCodeRequest request) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户未登�?, null));
        }
        
        try {
            RedemptionCodeDTO result = redemptionCodeService.redeemCode(request.getCode(), userId);
            return ResponseEntity.ok(ApiResponse.success("兑换成功", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage(), null));
        }
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/codes")
    public ResponseEntity<ApiResponse<RedemptionCodeDTO>> createCode(
            @Valid @RequestBody CreateRedemptionCodeRequest request) {
        Long creatorId = authService.getCurrentUserId();
        if (creatorId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("管理员未登录", null));
        }
        
        RedemptionCodeDTO result = redemptionCodeService.createCode(request.getPoints(), creatorId);
        return ResponseEntity.ok(ApiResponse.success("兑换码创建成�?, result));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/codes")
    public ResponseEntity<ApiResponse<List<RedemptionCodeDTO>>> getAllCodes() {
        List<RedemptionCodeDTO> codes = redemptionCodeService.getAllCodes();
        return ResponseEntity.ok(ApiResponse.success("获取兑换码列表成�?, codes));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/codes/{id}")
    public ResponseEntity<ApiResponse<RedemptionCodeDTO>> getCodeById(@PathVariable Long id) {
        try {
            RedemptionCodeDTO code = redemptionCodeService.getCodeById(id);
            return ResponseEntity.ok(ApiResponse.success("获取兑换码详情成�?, code));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage(), null));
        }
    }
}
