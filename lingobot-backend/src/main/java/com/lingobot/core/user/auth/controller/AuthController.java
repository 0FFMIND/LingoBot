package com.lingobot.core.user.auth.controller;

import com.lingobot.core.user.auth.dto.AuthResponse;
import com.lingobot.core.user.auth.dto.ChangePasswordRequest;
import com.lingobot.core.user.auth.dto.LoginRequest;
import com.lingobot.core.user.auth.dto.LoginWithCodeRequest;
import com.lingobot.core.user.auth.dto.RegisterRequest;
import com.lingobot.core.user.auth.dto.RegisterWithCodeRequest;
import com.lingobot.core.user.auth.dto.SendLoginCodeRequest;
import com.lingobot.core.user.auth.dto.SendVerificationCodeRequest;
import com.lingobot.core.user.auth.dto.UpdateAvatarRequest;
import com.lingobot.core.user.auth.dto.UpdateUsernameRequest;
import com.lingobot.core.user.auth.dto.UserDTO;
import com.lingobot.core.user.auth.service.AuthService;
import com.lingobot.core.user.auth.service.EmailVerificationService;
import com.lingobot.infrastructure.common.response.ApiResponse;
import com.lingobot.infrastructure.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        AuthResponse response = authService.register(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("注册成功", response));
    }
    
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        AuthResponse response = authService.login(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("登录成功", response));
    }
    
    @PostMapping("/send-login-code")
    public ResponseEntity<ApiResponse<Void>> sendLoginCode(
            @RequestBody SendLoginCodeRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        authService.sendLoginVerificationCode(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("登录验证码已发送", null));
    }
    
    @PostMapping("/login-with-code")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithCode(
            @RequestBody LoginWithCodeRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        AuthResponse response = authService.loginWithCode(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("登录成功", response));
    }
    
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDTO>> getCurrentUser() {
        UserDTO user = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success("退出登录成功", null));
    }
    
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success("密码修改成功", null));
    }
    
    @PostMapping("/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateAccount() {
        authService.deactivateAccount();
        return ResponseEntity.ok(ApiResponse.success("账户已注销", null));
    }
    
    @PostMapping("/update-avatar")
    public ResponseEntity<ApiResponse<Void>> updateAvatar(@RequestBody UpdateAvatarRequest request) {
        authService.updateAvatar(request.getAvatar());
        return ResponseEntity.ok(ApiResponse.success("头像更新成功", null));
    }
    
    @PostMapping("/update-username")
    public ResponseEntity<ApiResponse<AuthResponse>> updateUsername(@RequestBody UpdateUsernameRequest request) {
        AuthResponse response = authService.updateUsername(request.getUsername());
        return ResponseEntity.ok(ApiResponse.success("昵称更新成功", response));
    }
    
    @PostMapping("/send-verification-code")
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(@RequestBody SendVerificationCodeRequest request) {
        emailVerificationService.sendVerificationCode(request);
        return ResponseEntity.ok(ApiResponse.success("验证码已发送", null));
    }
    
    @PostMapping("/register-with-code")
    public ResponseEntity<ApiResponse<AuthResponse>> registerWithCode(
            @RequestBody RegisterWithCodeRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        AuthResponse response = authService.registerWithCode(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("注册成功", response));
    }
}
