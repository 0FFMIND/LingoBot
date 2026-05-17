package com.lingobot.core.user.auth.controller;

import com.lingobot.core.user.auth.dto.AuthResponse;
import com.lingobot.core.user.auth.dto.ChangePasswordRequest;
import com.lingobot.core.user.auth.dto.LoginWithCodeRequest;
import com.lingobot.core.user.auth.dto.RegisterWithCodeRequest;
import com.lingobot.core.user.auth.dto.SendLoginCodeRequest;
import com.lingobot.core.user.auth.dto.SendVerificationCodeRequest;
import com.lingobot.core.user.auth.dto.UpdateAvatarRequest;
import com.lingobot.core.user.auth.dto.UpdateUsernameRequest;
import com.lingobot.core.user.auth.dto.UserDTO;
import com.lingobot.core.user.auth.entity.User;
import com.lingobot.core.user.auth.repository.UserRepository;
import com.lingobot.core.user.auth.service.AuthService;
import com.lingobot.core.user.auth.service.EmailVerificationService;
import com.lingobot.core.user.auth.service.JwtService;
import com.lingobot.core.user.balance.repository.UserBalanceRepository;
import com.lingobot.infrastructure.common.config.AppProperties;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.infrastructure.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 用户认证控制器。
 *
 * 提供用户注册、登录、退出登录、账户管理等 REST 接口，
 * 支持邮箱验证码注册、邮箱密码验证码登录等认证方式。
 * 所有响应通过 ApiResponse 统一包装。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    // 认证服务，处理注册、登录等核心业务逻辑
    private final AuthService authService;
    // 邮箱验证码服务，处理验证码的发送和验证
    private final EmailVerificationService emailVerificationService;
    // 应用配置属性，用于判断当前运行环境
    private final AppProperties appProperties;
    // 用户仓库，用于查询用户信息
    private final UserRepository userRepository;
    // JWT 服务，用于生成认证 Token
    private final JwtService jwtService;
    // 用户余额仓库，用于查询用户余额信息
    private final UserBalanceRepository userBalanceRepository;
    
    // 发送登录验证码（邮箱 + 密码验证后发送）
    @PostMapping("/send-login-code")
    public ResponseEntity<ApiResponse<Void>> sendLoginCode(
            @RequestBody SendLoginCodeRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        authService.sendLoginVerificationCode(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("登录验证码已发送", null));
    }
    
    // 用户登录（邮箱 + 密码 + 验证码方式）
    @PostMapping("/login-with-code")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithCode(
            @RequestBody LoginWithCodeRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        AuthResponse response = authService.loginWithCode(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("登录成功", response));
    }
    
    // 获取当前登录用户的信息
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDTO>> getCurrentUser() {
        UserDTO user = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    // 用户退出登录，清除 SecurityContext
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success("退出登录成功", null));
    }
    
    // 修改当前登录用户的密码
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success("密码修改成功", null));
    }
    
    // 注销当前登录用户的账户（删除用户数据）
    @PostMapping("/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateAccount() {
        authService.deactivateAccount();
        return ResponseEntity.ok(ApiResponse.success("账户已注销", null));
    }
    
    // 更新当前登录用户的头像
    @PostMapping("/update-avatar")
    public ResponseEntity<ApiResponse<Void>> updateAvatar(@RequestBody UpdateAvatarRequest request) {
        authService.updateAvatar(request.getAvatar());
        return ResponseEntity.ok(ApiResponse.success("头像更新成功", null));
    }
    
    // 更新当前登录用户的昵称，返回新的认证响应（包含新 Token）
    @PostMapping("/update-username")
    public ResponseEntity<ApiResponse<AuthResponse>> updateUsername(@RequestBody UpdateUsernameRequest request) {
        AuthResponse response = authService.updateUsername(request.getUsername());
        return ResponseEntity.ok(ApiResponse.success("昵称更新成功", response));
    }
    
    // 发送邮箱注册验证码
    @PostMapping("/send-verification-code")
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(
            @RequestBody SendVerificationCodeRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        emailVerificationService.sendVerificationCode(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("验证码已发送", null));
    }
    
    // 用户注册（邮箱 + 验证码方式）
    @PostMapping("/register-with-code")
    public ResponseEntity<ApiResponse<AuthResponse>> registerWithCode(
            @RequestBody RegisterWithCodeRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        AuthResponse response = authService.registerWithCode(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("注册成功", response));
    }
    
    // 开发环境自动登录（仅开发环境可用，自动登录第一个管理员用户）
    @GetMapping("/dev-auto-login")
    public ResponseEntity<ApiResponse<AuthResponse>> devAutoLogin() {
        if (!appProperties.isDev()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(com.lingobot.infrastructure.common.response.ErrorCode.FORBIDDEN, "仅开发环境可用"));
        }
        
        User adminUser = userRepository.findByRole(User.Role.ROLE_ADMIN).stream().findFirst().orElse(null);
        
        if (adminUser == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(com.lingobot.infrastructure.common.response.ErrorCode.INTERNAL_ERROR, "未找到管理员用户"));
        }
        
        String token = jwtService.generateToken(adminUser.getUsername(), adminUser.getId());
        
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal frozenBalance = BigDecimal.ZERO;
        com.lingobot.core.user.balance.entity.UserBalance userBalance = userBalanceRepository.findByUserId(adminUser.getId()).orElse(null);
        if (userBalance != null) {
            balance = userBalance.getBalance() != null ? userBalance.getBalance() : BigDecimal.ZERO;
            frozenBalance = userBalance.getFrozenBalance() != null ? userBalance.getFrozenBalance() : BigDecimal.ZERO;
        }
        
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .username(adminUser.getUsername())
                .email(adminUser.getEmail())
                .userId(adminUser.getId())
                .role(adminUser.getRole().name())
                .avatar(adminUser.getAvatar())
                .balance(balance)
                .frozenBalance(frozenBalance)
                .build();
        
        return ResponseEntity.ok(ApiResponse.success("开发环境自动登录成功", response));
    }
}
