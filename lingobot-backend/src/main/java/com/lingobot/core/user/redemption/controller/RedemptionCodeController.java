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

import java.math.BigDecimal;
import java.util.List;

/**
 * 兑换码控制器。
 *
 * 统一入口路径：/api/redemption
 *
 * 权限划分：
 * - 普通用户接口：/balance（查余额）、/redeem（使用兑换码）→ 登录即可访问
 * - 管理员接口：/codes/** → 使用 @PreAuthorize("hasRole('ADMIN')") 限制
 *
 * 异常处理策略：
 * - Service 层抛出 IllegalArgumentException → 捕获并包装为 HTTP 400 + ApiResponse.error()
 * - 登录校验失败（userId == null）→ 直接返回 HTTP 400
 * - @Valid 参数校验失败 → 由 GlobalExceptionHandler 统一处理
 *
 * 响应约定：
 * - 成功：ApiResponse.success() / created()，HTTP 2xx
 * - 失败：ApiResponse.error(ErrorCode, message)，HTTP 4xx
 * - DELETE 成功：ResponseEntity.noContent()，HTTP 204 无响应体
 */
@Slf4j
@RestController
@RequestMapping("/api/redemption")
@RequiredArgsConstructor
public class RedemptionCodeController {
    
    private final RedemptionCodeService redemptionCodeService;
    private final AuthService authService;
    
    // 查询当前登录用户的余额，需登录
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "用户未登录"));
        }
        BigDecimal balance = redemptionCodeService.getUserBalance(userId);
        return ResponseEntity.ok(ApiResponse.success("获取余额成功", balance));
    }
    
    // 用户使用兑换码：校验登录 → 调用Service → 捕获IllegalArgumentException转400
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
    
    // 管理员创建兑换码：需要ADMIN角色 + 校验登录 → 调用Service创建 → 返回201
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
    
    // 管理员查询所有兑换码列表：需要ADMIN角色，按创建时间倒序
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/codes")
    public ResponseEntity<ApiResponse<List<RedemptionCodeDTO>>> getAllCodes() {
        List<RedemptionCodeDTO> codes = redemptionCodeService.getAllCodes();
        return ResponseEntity.ok(ApiResponse.success("获取兑换码列表成功", codes));
    }
    
    // 管理员按ID查询单个兑换码：需要ADMIN角色，不存在返回404
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

    // 管理员删除兑换码：需要ADMIN角色，仅允许删除未使用的，成功返回204无响应体
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
